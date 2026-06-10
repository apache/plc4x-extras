/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.logrecorder.appender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.Getter;
import org.apache.plc4x.merlot.logrecorder.exception.MerlotLogRecorderSecurityException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class MerlotLogRecorderJDBCAppender implements EventHandler, ManagedService {

    private final static Logger LOGGER = LoggerFactory.getLogger(MerlotLogRecorderJDBCAppender.class);

    private DataSource dataSource;
    private BundleContext bc;

    private final static String MERLOT_OLOG_EVENT_TOPIC = "merlot/olog";
    private static final String TABLE_NAME_PROPERTY = "table.name";
    private static final String DIALECT_PROPERTY = "dialect";
    private static final String DATASOURCE_TARGET = "dataSource.target";

    private Map<String, String> connectionProperties = new HashMap();

    //OPS4J support: Derby, H2, MariaDB, MySQL, PostgreSQL, SQLite
    private final static String createTableQueryGenericTemplate
            = "CREATE TABLE IF NOT EXISTS TABLENAME(id BIGINT NOT NULL PRIMARY KEY, owner VARCHAR(255),"
            + " level VARCHAR(100), description VARCHAR(4000), title VARCHAR(255), createdDate BIGINT, tags VARCHAR(255), logbooks VARCHAR(255), attachments_path VARCHAR(1500))";

    //OPS4J support: Oracle
    private final static String createTableQueryOracleTemplate
            = "CREATE TABLE IF NOT EXISTS TABLENAME(id NUMBER(19) NOT NULL PRIMARY KEY, owner VARCHAR2(255),"
            + " level VARCHAR2(100), description VARCHAR2(4000), title VARCHAR2(255), createdDate NUMBER(19), tags VARCHAR2(255), logbooks VARCHAR2(255), attachments_path VARCHAR(1500))";

    //The `INSERT` statement is the same for all databases supported by OPS4J
    private final static String insertQueryTemplate
            = "INSERT INTO TABLENAME(id, owner, level, description, title, createdDate, tags, logbooks, attachments_path) VALUES(?,?,?,?,?,?,?,?,?)";

    public MerlotLogRecorderJDBCAppender(BundleContext bc) {
        this.bc = bc;
    }

    public void constructTable() {
        try (Connection connection = dataSource.getConnection()) {
            createTable(connection);
        } catch (Exception e) {
            LOGGER.info("Error creating table schemas: {}", e.getMessage());
        }
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.info("Processing log from Phoebus, sending to persistence");

        String topic = event.getTopic();

        if (topic.equalsIgnoreCase(MERLOT_OLOG_EVENT_TOPIC)) {

            long id = (long) event.getProperty("id");
            String owner = (String) event.getProperty("owner");
            String level = (String) event.getProperty("level");
            String description = (String) event.getProperty("description");
            String title = (String) event.getProperty("title");
            long createdDate = (long) event.getProperty("createdDate");
            String tags = (String) event.getProperty("tags");
            String logbooks = (String) event.getProperty("logbooks");
            String attachmentsPath = (String) event.getProperty("attachmentsPath");

            try (Connection connection = dataSource.getConnection()) {
                String insertQuery = insertQueryTemplate.replaceAll("TABLENAME", this.connectionProperties.get(TABLE_NAME_PROPERTY));
                try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
                    insertStatement.setLong(1, id);
                    insertStatement.setString(2, owner);
                    insertStatement.setString(3, level);
                    insertStatement.setString(4, description);
                    insertStatement.setString(5, title);
                    insertStatement.setLong(6, createdDate);
                    insertStatement.setString(7, tags);
                    insertStatement.setString(8, logbooks);
                    insertStatement.setString(9, attachmentsPath.substring(0, attachmentsPath.length()));

                    //Submit the form
                    insertStatement.executeUpdate();
                } catch (Exception e) {
                    LOGGER.info("Error inserting a record into the DataSource {}", this.connectionProperties.get(TABLE_NAME_PROPERTY));
                }
            } catch (SQLException ex) {

            }

        }
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {

        if (properties == null || properties.isEmpty()) {
            return;
        }

        //Clean properties
        this.connectionProperties.clear();

        //-------------------------Validate Properties--------------------------------------
        String tableName = (String) properties.get(TABLE_NAME_PROPERTY);

        if (tableName == null || tableName.trim().isEmpty()) {
            throw new ConfigurationException("table.name", "The ‘table.name’ property cannot be empty.");
        }

        String dialect = (String) properties.get(DIALECT_PROPERTY);

        if (dialect == null || dialect.trim().isEmpty()) {
            throw new ConfigurationException("dialect", "The ‘dialect’ property cannot be empty.");
        }

        String jndiName = (String) properties.get(DATASOURCE_TARGET);

        if (jndiName == null || jndiName.trim().isEmpty()) {
            throw new ConfigurationException("dataSource.target", "The ‘dataSource.target’ property cannot be empty.");
        }

        this.connectionProperties.put(TABLE_NAME_PROPERTY, tableName);
        this.connectionProperties.put(DIALECT_PROPERTY, dialect);
        this.connectionProperties.put(DATASOURCE_TARGET, jndiName);

        //----------------------End Validate Properties------------------------------------
        //----------------------Get services Datasources----------------------------------
        try {
            ServiceReference[] refDataSource = bc.getAllServiceReferences(DataSource.class.getName(), jndiName);

            this.dataSource = (DataSource) bc.getService(refDataSource[0]);

            //It only triggers the table creation if the data source is available
            if (this.dataSource.getConnection() != null) {
                constructTable();
            }

            LOGGER.info("DataSource found: {}", this.dataSource.getClass().getName());

        } catch (Exception e) {
            throw new ConfigurationException(null, "Error retrieving the DataSource", e);
        }
        //---------------------End get services-------------------------------------------
    }

    private void createTable(Connection connection) {
        String createTemplate = null;

        if (this.connectionProperties.get(DIALECT_PROPERTY).equals("generic")) {
            createTemplate = createTableQueryGenericTemplate;
        } else if (this.connectionProperties.get(DIALECT_PROPERTY).equals("oracle")) {
            createTemplate = createTableQueryOracleTemplate;
        }

        String createTableQuery = createTemplate.replaceAll("TABLENAME", this.connectionProperties.get(TABLE_NAME_PROPERTY));

        try (Statement createStatement = connection.createStatement()) {
            createStatement.executeUpdate(createTableQuery);
            LOGGER.info("Table {} has been created", this.connectionProperties.get(TABLE_NAME_PROPERTY));
        } catch (SQLException e) {
            if (e.getErrorCode() == 955) {
                LOGGER.info("Oracle error",
                        new MerlotLogRecorderSecurityException("The table {} already exists in the Oracle database"), TABLE_NAME_PROPERTY);
            }

            LOGGER.info("Can't create table {}", TABLE_NAME_PROPERTY);
        }
    }

}
