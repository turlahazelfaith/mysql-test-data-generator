/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.github.sakserv;

import com.github.sakserv.config.ConfigVars;
import com.github.sakserv.config.PropertyParser;
import com.github.sakserv.jdbc.JdbcGenerator;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {
    
    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static PropertyParser propertyParser = new PropertyParser();
    private static JdbcGenerator jdbcGenerator = new JdbcGenerator();
    private static Connection connection;

    public static void main(String[] args) {
        
        // Parse the cmdline args and property file
        parseCmdLineAndProps(args);
        
        // Get a JDBC connection, without the database
        Connection connection = getConnectionNoDb();
                
        // Create the grants
        createGrants(connection);

        // Create the database, if required
        createDatabase(connection);
        
        // Get a connection, with the database specified
        connection = getConnection();
        
        // Create the table
        // TODO: Make table definitions more abstract
        createTable(connection);
        
        // Display success message if we got this far
        logSuccessMessage();
    }
    
    private static void displayQueryDebug(String sql) {
        LOG.info("DEBUG: Running the following statement: " + sql);
    }
    
    private static String getNoDbConnString() {
        return propertyParser.getProperty(ConfigVars.JDBC_CONNECTION_STRING_PREFIX_VAR) +
                propertyParser.getProperty(ConfigVars.JDBC_HOSTNAME_VAR) + ":" +
                propertyParser.getProperty(ConfigVars.JDBC_PORT_VAR);
    }
    
    private static String getConnString() {
        return propertyParser.getProperty(ConfigVars.JDBC_CONNECTION_STRING_PREFIX_VAR) +
                propertyParser.getProperty(ConfigVars.JDBC_HOSTNAME_VAR) + ":" +
                propertyParser.getProperty(ConfigVars.JDBC_PORT_VAR) + "/" +
                propertyParser.getProperty(ConfigVars.JDBC_DATABASE_VAR);
        
    }
    
    private static Connection getConnectionNoDb() {
        // Establish the connection, without the db
        String noDbConnString = getNoDbConnString();
        LOG.info("Establishing connection to: " + noDbConnString);
        Connection connection = null;
        try {
            connection = jdbcGenerator.getConnectionNoDb(
                    propertyParser.getProperty(ConfigVars.JDBC_CONNECTION_STRING_PREFIX_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_HOSTNAME_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_PORT_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_USER_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_PASSWORD_VAR)
            );
        } catch (SQLException e) {
            LOG.error("ERROR: Failed to connect to database at: " + noDbConnString +
                    " using username: " + propertyParser.getProperty(ConfigVars.JDBC_USER_VAR));
            e.printStackTrace();
        }
        return connection;
        
    }
    
    private static Connection getConnection() {
        // Establish the connection, with the db
        String dbConnString = propertyParser.getProperty(ConfigVars.JDBC_CONNECTION_STRING_PREFIX_VAR) +
                propertyParser.getProperty(ConfigVars.JDBC_HOSTNAME_VAR) + ":" +
                propertyParser.getProperty(ConfigVars.JDBC_PORT_VAR) + "/" +
                propertyParser.getProperty(ConfigVars.JDBC_DATABASE_VAR);
        LOG.info("Establishing connection to: " + getConnString());
        connection = null;
        try {
            connection = jdbcGenerator.getConnection(
                    propertyParser.getProperty(ConfigVars.JDBC_CONNECTION_STRING_PREFIX_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_HOSTNAME_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_PORT_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_DATABASE_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_USER_VAR),
                    propertyParser.getProperty(ConfigVars.JDBC_PASSWORD_VAR)
            );
        } catch (SQLException e) {
            LOG.error("ERROR: Failed to connect to database at: " + dbConnString +
                    " using username: " + propertyParser.getProperty(ConfigVars.JDBC_USER_VAR));
            e.printStackTrace();
        }
        return connection;
        
    }
    
    private static void parseCmdLineAndProps(String[] args) {
        // Arg parsing for input props file
        Options options = new Options();
        options.addOption("c", true, "configuration file");

        CommandLineParser parser = new BasicParser();
        CommandLine cmd;
        String propertyFileName = "";
        try {
            cmd = parser.parse(options, args);

            // Load the props file
            if(cmd.hasOption("c")) {
                propertyFileName = cmd.getOptionValue("c");
            } else {
                propertyFileName = ConfigVars.DEFAULT_PROPS_FILE;
            }
        } catch(ParseException e) {
            LOG.error("ERROR: Failed to parse commandline args!");
            e.printStackTrace();
        }
        LOG.info("Loading and parsing the property file: " + new File(propertyFileName).getAbsolutePath());
        propertyParser.setPropFileName(propertyFileName);
        propertyParser.parsePropsFile();
        
    }
    
    private static void createGrants(Connection connection) {
        // Create the grants
        try {
            LOG.info("Running grants for user " + propertyParser.getProperty(ConfigVars.JDBC_USER_VAR) +
                    " on " + getNoDbConnString());
            Statement statement = connection.createStatement();
            String sqlGrant = "GRANT ALL PRIVILEGES ON *.* TO " +
                    "\"" + propertyParser.getProperty(ConfigVars.JDBC_USER_VAR) + "\"@" +
                    "\"%\" IDENTIFIED BY \"" + propertyParser.getProperty(ConfigVars.JDBC_PASSWORD_VAR) + "\"";
            displayQueryDebug(sqlGrant);
            statement.executeQuery(sqlGrant);

            statement = connection.createStatement();
            String sqlFlushPriv = "FLUSH PRIVILEGES";
            displayQueryDebug(sqlFlushPriv);
            statement.executeQuery(sqlFlushPriv);

        } catch (SQLException e) {
            LOG.error("ERROR: Error applying grants for user: " + propertyParser.getProperty(ConfigVars.JDBC_USER_VAR));
            e.printStackTrace();
        }
        
    }
    
    private static void createDatabase(Connection connection) {
        // Create the database
        if (!Boolean.parseBoolean(propertyParser.getProperty(ConfigVars.JDBC_SKIP_DATABASE_CREATE_VAR))) {
            try {
                LOG.info("Creating the database: " + propertyParser.getProperty(ConfigVars.JDBC_DATABASE_VAR));
                Statement statement = connection.createStatement();
                String sqlCreateDatabase = "CREATE DATABASE " + propertyParser.getProperty(ConfigVars.JDBC_DATABASE_VAR);
                displayQueryDebug(sqlCreateDatabase);
                statement.executeQuery(sqlCreateDatabase);
            } catch (SQLException e) {
                LOG.error("ERROR: Failed to create database: " + propertyParser.getProperty(ConfigVars.JDBC_DATABASE_VAR));
                e.printStackTrace();
            }
        }
        
    }
    
    private static void createTable(Connection connection) {
        try {
            LOG.info("Creating the table: " + propertyParser.getProperty(ConfigVars.JDBC_TABLE_VAR));
            Statement statement = connection.createStatement();
            String sql = "CREATE TABLE " + propertyParser.getProperty(ConfigVars.JDBC_TABLE_VAR) + " " +
                    "(id INTEGER NOT NULL AUTO_INCREMENT, " +
                    "firstname VARCHAR(255), " +
                    "lastname VARCHAR(255), " +
                    "subject VARCHAR(255), " +
                    "score INTEGER, " +
                    "datetime DATETIME, " +
                    "PRIMARY KEY ( id ))";
            statement.executeQuery(sql);
        } catch (SQLException e) {
            LOG.error("ERROR: Failed to create table: " + propertyParser.getProperty(ConfigVars.JDBC_TABLE_VAR));
            e.printStackTrace();
        }
        
    }
    
    private static void logSuccessMessage() {
        // Display success message if we got this far
        LOG.info("SUCCESS: Successfully populated table " +
                        propertyParser.getProperty(ConfigVars.JDBC_DATABASE_VAR) +
                        "." + propertyParser.getProperty(ConfigVars.JDBC_TABLE_VAR) +
                        " with " + propertyParser.getProperty(ConfigVars.JDBC_NUM_ROWS_VAR) + " records."
        );
        
    }
    
}