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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.sql.DataSource;
import org.apache.plc4x.merlot.lector62x.api.MerlotDecanterProcessorDataLector62X;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageService;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageDTO;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageResponse;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.event.Event;
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
/*Quitar id y usar PK compuesta (codguia, numeropieza)
    private final static String createTableQueryGenericTemplate
        = "CREATE TABLE IF NOT EXISTS TABLENAME("
        + "codguia VARCHAR(40) NOT NULL, "
        + "numeropieza BIGINT NOT NULL, "
        + "totalpiezas BIGINT, "
        + "oficinaorigen BIGINT, "
        + "oficinadestino BIGINT, "
        + "servicio BIGINT, "
        + "casillero BIGINT, "
        + "ruta BIGINT, "
        + "peso DOUBLE, "
        + "guiaconfirmada VARCHAR(10), "
        + "PRIMARY KEY (codguia, numeropieza))";
    */
    
    
    private final static String createTableQueryGenericTemplate
            = "CREATE TABLE IF NOT EXISTS TABLENAME(id BIGINT NOT NULL PRIMARY KEY, "
            + "codguia VARCHAR(40), numeropieza BIGINT, totalpiezas BIGINT, oficinaorigen BIGINT, "
            + "oficinadestino BIGINT, servicio BIGINT, casillero BIGINT,"
            + " ruta BIGINT, peso DOUBLE, guiaconfirmada VARCHAR(10))";

    private final static String insertQueryTemplate
            = "INSERT INTO TABLENAME(id, codguia, numeropieza, totalpiezas, oficinaorigen, "
            + "oficinadestino, servicio, casillero, ruta, peso, guiaconfirmada) VALUES(?,?,?,?,?,?,?,?,?,?,?)";

    private Random rd;
    private volatile MerlotLecto620ShippingPackageService service;

    public MerlotDecanterProcessorDataLector62XImpl(BundleContext bc) {
        this.bc = bc;
        this.rd = new Random();
    }

    //Get usado por los otros servicios como el repositorio
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
        LOGGER.info(">>> init() llamado");
        this.httpClient = HttpClient.newHttpClient();

    }

    @Override
    public void destroy() {
        LOGGER.info(">>> destroy() llamado");
        this.httpClient.shutdownNow();
        this.httpClient.close();
    }

    public void setService(MerlotLecto620ShippingPackageService service) {
        this.service = service;
        pushConfigToService();
    }

    @Override
    public void saveData(String... data) {

        long id = Math.abs(rd.nextLong());
        String codguia = data[0].replace("\u0002", "");
        long numeropieza = Long.parseLong(data[1]);
        long totalpiezas = Long.parseLong(data[2]);
        long oficinaorigen = Long.parseLong(data[3]);
        long oficinadestino = Long.parseLong(data[4]);
        long servicio = Long.parseLong(data[5]);
        long casillero = Long.parseLong(data[6]);
        long ruta = Long.parseLong(data[7].replace("\u0003", ""));

        Double peso = Double.parseDouble(data[8].replace("\u0003", "").trim());

        try (Connection connection = dataSource.getConnection()) {
            String insertQuery = insertQueryTemplate.replaceAll("TABLENAME", this.connectionProperties.get(TABLE_NAME_PROPERTY));
            try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
                insertStatement.setLong(1, id);
                insertStatement.setString(2, codguia);
                insertStatement.setLong(3, numeropieza);
                insertStatement.setLong(4, totalpiezas);
                insertStatement.setLong(5, oficinaorigen);
                insertStatement.setLong(6, oficinadestino);
                insertStatement.setLong(7, servicio);
                insertStatement.setLong(8, casillero);
                insertStatement.setLong(9, ruta);
                insertStatement.setDouble(10, peso);
                insertStatement.setString(11, "false");

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
            String payloadEvent = (String) event.getProperty("payload");

            try {
                //Successful Reading
                if (!payloadEvent.contains("NoRead")) {
                    saveData(payloadEvent.split(";"));
                    LOGGER.info("Sending {} to the {} server", payloadEvent.split(";"), URL_EXTERNAL);

                    if (service == null) {
                        LOGGER.error("Service no inicializado");
                        return;
                    }
                    this.service.postEventPackage(payloadEvent.split(";"));
                } else {
                    //TODO: Hacer algo con la lectura fallida
                }
            } catch (Exception e) {
                LOGGER.info("Error: {}", e.getMessage());
            }
        }
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        LOGGER.info(">>> updated() llamado, properties = {}", properties);
        if (properties == null || properties.isEmpty()) {
            return;
        }

        LOGGER.info("Reading Properties from the Configuration File");
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
        //----------------------Get services Datasources----------------------------------
        try {
            LOGGER.info("Searching for data source {}", this.connectionProperties.get(DATASOURCE_TARGET));

            ServiceReference[] refDataSource = bc.getAllServiceReferences(DataSource.class.getName(), this.connectionProperties.get(DATASOURCE_TARGET));

            this.dataSource = (DataSource) bc.getService(refDataSource[0]);

            //It only triggers the table creation if the data source is available
            if (this.dataSource.getConnection() != null) {
                LOGGER.info("DataSource found: {}", this.dataSource.getClass().getName());
                LOGGER.info("Establishing connection");
                getConnectionAndconstructTable();
                pushConfigToService();
            }
        } catch (SQLException | InvalidSyntaxException e) {
            throw new ConfigurationException(null, "Error retrieving the DataSource", e);
        }
        //---------------------End get services-------------------------------------------
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
