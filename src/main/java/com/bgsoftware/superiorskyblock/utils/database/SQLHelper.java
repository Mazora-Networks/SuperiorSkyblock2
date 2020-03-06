package com.bgsoftware.superiorskyblock.utils.database;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SQLHelper {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();
    private static final CompletableFuture<Void> ready = new CompletableFuture<>();
    private static HikariDataSource dataSource;

    private SQLHelper(){}

    public static void waitForConnection(){
        try {
            ready.get();
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static boolean createConnection(SuperiorSkyblockPlugin plugin){
        try {
            SuperiorSkyblockPlugin.log("Trying to connect to " + plugin.getSettings().databaseType + " database...");
            HikariConfig config = new HikariConfig();
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("SuperiorSkyblock Pool");

            if (plugin.getSettings().databaseType.equalsIgnoreCase("MySQL")) {
                config.setDriverClassName("com.mysql.jdbc.Driver");

                String address = plugin.getSettings().databaseMySQLAddress;
                String dbName = plugin.getSettings().databaseMySQLDBName;
                String userName = plugin.getSettings().databaseMySQLUsername;
                String password = plugin.getSettings().databaseMySQLPassword;
                int port = plugin.getSettings().databaseMySQLPort;

                config.setJdbcUrl("jdbc:mysql://" + address + ":" + port + "/" + dbName);
                config.setUsername(userName);
                config.setPassword(password);
                config.setMaximumPoolSize(10);
                config.setIdleTimeout(30000);

                dataSource = new HikariDataSource(config);

                SuperiorSkyblockPlugin.log("Successfully established connection with MySQL database!");
            } else {
                config.setDriverClassName("org.sqlite.JDBC");
                File file = new File(plugin.getDataFolder(), "database.db");
                config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath().replace("\\", "/"));

                dataSource = new HikariDataSourceSQLiteWrapper(config);

                SuperiorSkyblockPlugin.log("Successfully established connection with SQLite database!");
            }

            ready.complete(null);

            return true;
        }catch(Exception ignored){}

        return false;
    }

    public static void executeUpdate(String statement){
        String prefix = plugin.getSettings().databaseType.equalsIgnoreCase("MySQL") ? plugin.getSettings().databaseMySQLPrefix : "";
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try{
            conn = dataSource.getConnection();
            preparedStatement = conn.prepareStatement(statement.replace("{prefix}", prefix));
            preparedStatement.executeUpdate();
        }catch(SQLException ex){
            System.out.println(statement);
            ex.printStackTrace();
        } finally {
            close(preparedStatement);
            close(conn);
        }
    }

    public static boolean doesConditionExist(String statement){
        boolean ret = false;

        String prefix = plugin.getSettings().databaseType.equalsIgnoreCase("MySQL") ? plugin.getSettings().databaseMySQLPrefix : "";
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try{
            conn = dataSource.getConnection();
            preparedStatement = conn.prepareStatement(statement.replace("{prefix}", prefix));
            resultSet = preparedStatement.executeQuery();
            ret = resultSet.next();
        }catch(SQLException ex){
            ex.printStackTrace();
        } finally {
            close(resultSet);
            close(preparedStatement);
            close(conn);
        }

        return ret;
    }

    public static void executeQuery(String statement, QueryConsumer<ResultSet> callback){
        String prefix = plugin.getSettings().databaseType.equalsIgnoreCase("MySQL") ? plugin.getSettings().databaseMySQLPrefix : "";
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try{
            conn = dataSource.getConnection();
            preparedStatement = conn.prepareStatement(statement.replace("{prefix}", prefix));
            resultSet = preparedStatement.executeQuery();
            callback.accept(resultSet);
        }catch(SQLException ex){
            ex.printStackTrace();
        } finally {
            close(resultSet);
            close(preparedStatement);
            close(conn);
        }
    }

    public static void close(){
        dataSource.close();
    }

    public static void buildStatement(String query, QueryConsumer<PreparedStatement> consumer, Consumer<SQLException> failure){
        String prefix = plugin.getSettings().databaseType.equalsIgnoreCase("MySQL") ? plugin.getSettings().databaseMySQLPrefix : "";
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try{
            conn = dataSource.getConnection();
            preparedStatement = conn.prepareStatement(query.replace("{prefix}", prefix));
            consumer.accept(preparedStatement);
        }catch(SQLException ex){
            failure.accept(ex);
        } finally {
          close(preparedStatement);
          close(conn);
        }
    }

    private static void close(AutoCloseable closeable){
        if(closeable != null && plugin.getSettings().databaseType.equalsIgnoreCase("MySQL")){
            try {
                closeable.close();
            } catch (Exception ignored) {}
        }
    }

    public interface QueryConsumer<T>{

        void accept(T value) throws SQLException;

    }


    private static class HikariDataSourceSQLiteWrapper extends HikariDataSource{

        private Connection conn;

        HikariDataSourceSQLiteWrapper(HikariConfig config) throws SQLException{
            conn = DriverManager.getConnection(config.getJdbcUrl());
        }

        @Override
        public Connection getConnection(){
            return conn;
        }

        @Override
        public void close() {
            try {
                conn.close();
            }catch(SQLException ignored){}
        }
    }

}

