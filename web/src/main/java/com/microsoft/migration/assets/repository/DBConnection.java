package com.microsoft.migration.assets.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    public static Connection getConnection() throws SQLException {
        
        Properties properties = new Properties();
        try (InputStream input = MainPG.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find application.properties");
                return;
            }
            // Load the properties file
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        String connString = properties.getProperty("AZURE_PGSQL_CONNECTIONSTRING");
        String user = properties.getProperty("AZURE_PGSQL_USER");
        String password = properties.getProperty("AZURE_PGSQL_PASSWORD");
        
        connString = connString + "&user=" + user + "&password=" + password;
        Connection connection = DriverManager.getConnection(connString);
        
        return connection;

    }
}