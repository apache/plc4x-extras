/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.merlot.lector62x.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.plc4x.merlot.lector62x.api.MerlotDecanterProcessorDataLector62X;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.event.Event;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotDecanterProcessorDataLector62XImpl implements MerlotDecanterProcessorDataLector62X {

    private final static Logger LOGGER = LoggerFactory.getLogger(MerlotDecanterProcessorDataLector62XImpl.class);

    private DataSource dataSource;
    private BundleContext bc;

    private static final String URL_EXTERNAL = "url.external";
    private final static String MERLOT_LECTOR62X_EVENT_TOPIC = "decanter/collect/socket";
    private static final String TABLE_NAME_PROPERTY = "table.name";
    private static final String DIALECT_PROPERTY = "dialect";
    private static final String DATASOURCE_TARGET = "dataSource.target";

    private Map<String, String> connectionProperties = new HashMap();

    //Cliente HTTP
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private String url;

    private ServiceTracker<DataSource, DataSource> dsTracker;

//    private final static String createTableQueryGenericTemplate
//            = "CREATE TABLE IF NOT EXISTS TABLENAME(codguia VARCHAR(40) NOT NULL PRIMARY KEY, numeropieza BIGINT, totalpiezas BIGINT, oficinaorigen BIGINT, "
//            + "oficinadestino BIGINT, servicio BIGINT, casillero BIGINT,"
//            + " ruta BIGINT, peso DOUBLE, guiaconfirmada VARCHAR(10), id_tipoprocesoplc BIGINT)";
    private final static String createTableQueryGenericTemplate
            = "CREATE TABLE IF NOT EXISTS TABLENAME ( "
            + "codguia VARCHAR(40) NOT NULL PRIMARY KEY, "
            + "numeropieza BIGINT, "
            + "totalpiezas BIGINT, "
            + "oficinaorigen BIGINT, "
            + "oficinadestino BIGINT, "
            + "servicio BIGINT, "
            + "casillero BIGINT, "
            + "ruta BIGINT, "
            + "peso DOUBLE PRECISION, "
            + "guiaconfirmada VARCHAR(10), "
            + "id_tipoprocesoplc BIGINT )";
    
    private final static String insertQueryTemplate
            = "INSERT INTO TABLENAME(codguia, numeropieza, totalpiezas, oficinaorigen, "
            + "oficinadestino, servicio, casillero, ruta, peso, guiaconfirmada, id_tipoprocesoplc) VALUES(?,?,?,?,?,?,?,?,?,?,?)";

    private volatile MerlotLecto620ShippingPackageService service;
    private DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MerlotDecanterProcessorDataLector62XImpl(BundleContext bc) {
        this.bc = bc;
    }

    //Get is used by other services as a repository
    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public Map<String, String> getConnectionProperties() {
        return connectionProperties;
    }

    @Override
    public void init() {
        LOGGER.info("Initializing the Event Processing Module for the Lector620 Camera");
        this.httpClient = HttpClient.newHttpClient();

    }

    @Override
    public void destroy() {
        LOGGER.info("Destroy the Event Processing Module of the Lector620 Camera");
        if (dsTracker != null) {
            dsTracker.close();
            dsTracker = null;
        }
        this.httpClient.shutdownNow();
        this.httpClient.close();
    }

    public void setService(MerlotLecto620ShippingPackageService service) {
        LOGGER.info("Setting service on processor module");
        this.service = service;
        pushConfigToService();
    }

    @Override
    public void saveData(String... data) {

        System.out.println("DATA: " + Arrays.toString(data));
        String codguia = "";
        long partNumber = 0L;
        long totalPieces = 0L;
        long originOffice = 0L;
        long destinationOffice = 0L;
        long service = 0L;
        long storageLocker = 0L;
        long route = 0L;
        Double weight = 0.0;
        long idTypeProcessPLC = 0L;

        /**
         * Note: Exception when trying to convert from a String to another data
         * type.
         */
        /**
         * EVENTO: 1714233644;01;01;0046;0136;0104;00000000;0705; 1.54;1
         */
        try {
            codguia = data[0].replace("\u0002", "");
            partNumber = Long.parseLong(data[1]);
            totalPieces = Long.parseLong(data[2]);
            originOffice = Long.parseLong(data[3]);
            destinationOffice = Long.parseLong(data[4]);
            service = Long.parseLong(data[5]);
            storageLocker = Long.parseLong(data[6]);
            route = Long.parseLong(data[7].replace("\u0003", ""));

            weight = Double.parseDouble(data[8].trim());
            idTypeProcessPLC = Long.parseLong(data[9].trim());
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

        try (Connection connection = dataSource.getConnection()) {
            String insertQuery = insertQueryTemplate.replaceAll("TABLENAME", this.connectionProperties.get(TABLE_NAME_PROPERTY));
            try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
                insertStatement.setString(1, codguia);
                insertStatement.setLong(2, partNumber);
                insertStatement.setLong(3, totalPieces);
                insertStatement.setLong(4, originOffice);
                insertStatement.setLong(5, destinationOffice);
                insertStatement.setLong(6, service);
                insertStatement.setLong(7, storageLocker);
                insertStatement.setLong(8, route);
                insertStatement.setDouble(9, weight);
                insertStatement.setString(10, "false");
                insertStatement.setLong(11, idTypeProcessPLC);

                //Submit the form
                if (insertStatement.executeUpdate() > 1) {
                    LOGGER.info("Record inserted with guide code {}", codguia);
                }
            } catch (Exception e) {
                LOGGER.info("Error inserting a record into the DataSource {}", this.connectionProperties.get(TABLE_NAME_PROPERTY));
            }
        } catch (SQLException ex) {
            LOGGER.info(ex.getMessage());
        }
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.info("Processing event data from lector62x, sending to persistence");
        String topic = event.getTopic();
        if (topic.equalsIgnoreCase(MERLOT_LECTOR62X_EVENT_TOPIC)) {
            System.out.println("EVENTO");
            String payloadEvent = (String) event.getProperty("payload");
            System.out.println(event.getProperty("payload"));
            try {
                //Successful Reading
                if (!payloadEvent.contains("NoRead")) {
                    saveData(payloadEvent.split(";"));
                    LOGGER.info("Sending {} to the {} server", payloadEvent.split(";"), URL_EXTERNAL);

                    if (service == null) {
                        LOGGER.info("Service not initialized");
                        return;
                    }
                    this.service.postEventPackage(payloadEvent.split(";"));
                } else {
                    //TODO: Hacer algo con la lectura fallida
                    LOGGER.info("The Data Matrix could not be read correctly");

                    if (service == null) {
                        LOGGER.info("Service not initialized");
                        return;
                    }

                    this.service.insertNoRead(LocalDateTime.now().format(dateTimeFormat), "0.0");//Valor fijo para pruebas por causa PLC zoom no envia a tiempo el peso
                }
            } catch (Exception e) {
                LOGGER.info("Error: {}", e.getMessage());
            }
        }
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        LOGGER.info("Updating Properties from the Configuration File \n{}", properties);
        if (properties == null || properties.isEmpty()) {
            return;
        }

        //Clean properties
        this.connectionProperties.clear();

        //Server URL
        this.url = (String) properties.get(URL_EXTERNAL);

        String tableName = (String) properties.get(TABLE_NAME_PROPERTY);

        if (tableName == null || tableName.trim().isEmpty()) {
            throw new ConfigurationException("table.name", "The 'table.name' property cannot be empty.");
        }

        String dialect = (String) properties.get(DIALECT_PROPERTY);

        if (dialect == null || dialect.trim().isEmpty()) {
            throw new ConfigurationException("dialect", "The 'dialect' property cannot be empty.");
        }

        String jndiName = (String) properties.get(DATASOURCE_TARGET);

        if (jndiName == null || jndiName.trim().isEmpty()) {
            throw new ConfigurationException("dataSource.target", "The 'dataSource.target' property cannot be empty.");
        }

        this.connectionProperties.put(TABLE_NAME_PROPERTY, tableName);
        this.connectionProperties.put(DIALECT_PROPERTY, dialect);
        this.connectionProperties.put(DATASOURCE_TARGET, jndiName);
        this.connectionProperties.put(URL_EXTERNAL, url);

        //----------------------End Validate Properties------------------------------------
        getDataSourceForPropertiesConnectionAndCreateTable();
    }

    private void getDataSourceForPropertiesConnectionAndCreateTable() throws ConfigurationException {
        String target = this.connectionProperties.get(DATASOURCE_TARGET).trim();
        if (!target.startsWith("(")) {
            target = "(" + target + ")";
        }
        LOGGER.info("Searching for a data source named [{}]", target);
        try {
            Filter filter = bc.createFilter(
                    "(&(objectClass=" + DataSource.class.getName() + ")" + target + ")");

            if (dsTracker != null) {
                dsTracker.close();
            }

            dsTracker = new ServiceTracker<>(bc, filter,
                    new ServiceTrackerCustomizer<DataSource, DataSource>() {

                @Override
                public DataSource addingService(ServiceReference<DataSource> ref) {
                    DataSource ds = bc.getService(ref);
                    dataSource = ds;
                    LOGGER.info("DataSource found: {}", ds.getClass().getName());
                    getConnectionAndconstructTable();
                    pushConfigToService();
                    if (service != null) {
                        service.onDataSourceReady();
                    }
                    return ds;
                }

                @Override
                public void modifiedService(ServiceReference<DataSource> ref, DataSource ds) {
                }

                @Override
                public void removedService(ServiceReference<DataSource> ref, DataSource ds) {
                    if (dataSource == ds) {
                        dataSource = null;
                    }
                    bc.ungetService(ref);
                }
            });
            dsTracker.open();

        } catch (InvalidSyntaxException e) {
            throw new ConfigurationException(DATASOURCE_TARGET,
                    "Invalid LDAP filter: " + target, e);
        }
    }

    public void getConnectionAndconstructTable() {
        try (Connection connection = dataSource.getConnection()) {
            createTable(connection);
        } catch (Exception e) {
            LOGGER.info("Error creating table schemas: {}", e.getMessage());
        }
    }

    private void createTable(Connection connection) {
        String createTemplate = null;

        if (this.connectionProperties.get(DIALECT_PROPERTY).equals("generic")) {
            createTemplate = createTableQueryGenericTemplate;
        } else {
            LOGGER.info("Not Support this option");
        }

        String createTableQuery = createTemplate.replaceAll("TABLENAME", this.connectionProperties.get(TABLE_NAME_PROPERTY));

        try (Statement createStatement = connection.createStatement()) {
            createStatement.executeUpdate(createTableQuery);
            LOGGER.info("Table {} has been created", this.connectionProperties.get(TABLE_NAME_PROPERTY));
        } catch (SQLException e) {
            LOGGER.info("Can't create table {} \n {}", TABLE_NAME_PROPERTY, e.getMessage());
        }
    }

    private void pushConfigToService() {
        MerlotLecto620ShippingPackageService s = this.service;
        String u = this.url;
        if (s != null && u != null) {
            s.setUrl(u);

        }
    }
}
