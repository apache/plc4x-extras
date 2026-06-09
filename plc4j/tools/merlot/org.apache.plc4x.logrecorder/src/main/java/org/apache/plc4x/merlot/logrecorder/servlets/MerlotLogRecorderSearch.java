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
package org.apache.plc4x.merlot.logrecorder.servlets;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.apache.plc4x.merlot.logrecorder.appender.MerlotLogRecorderJDBCAppender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

public class MerlotLogRecorderSearch extends HttpServlet {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotLogRecorderSearch.class);
    private static final String TABLE_NAME_PROPERTY = "table.name";
    
    private Map<String, String[]> properties = new HashMap<>();
    private static Map<String, String> mapperPropertiesXattributesTable;
    private MerlotLogRecorderJDBCAppender appender;

    private final static String insertQueryTemplate
        = "SELECT * TABLENAME(id, owner, level, description, title, createdDate, tags, logbooks, attachments_path) VALUES(?,?,?,?,?,?,?,?,?)";
    
    public MerlotLogRecorderSearch(MerlotLogRecorderJDBCAppender appender) {
        this.appender = appender;
    }

//     owner = [luis]   
//                size = [30]    
    //level = [Info,Problem,Suggestion,Urgent]    
//                tz = [America/Caracas]                    
//                start = [20 minutes]                  
//                logbooks = [Diagnostics,Controls,Operations]   
//                end = [now]                    
//                from = [0]               
//                sort = [down]          
//                title = [hola mundo]   
//                tags = [Mantenimiento,Fallo Software,Upgrade,Calibracion]   
//                desc = [un texto]          
    static {
        mapperPropertiesXattributesTable = Map.ofEntries(
                Map.entry("owner", "owner"),
                Map.entry("level", "level"),
                Map.entry("desc", "description"),
                Map.entry("title", "title"),
                Map.entry("tags", "tags"),
                Map.entry("sort", "sort"),
                Map.entry("timeFrom", "from"),
                Map.entry("timeTo", "end"),
                Map.entry("scan", "start"),
                Map.entry("logbooks", "logbooks")
//                Map.entry("timeZone", "tz")
        );

    }

    private static String parse(String property) {

        if (mapperPropertiesXattributesTable.containsKey(property)) {
            return mapperPropertiesXattributesTable.get(property);
        }

        return null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("application/json");
        Map<String, String[]> params = req.getParameterMap();

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }

        requestLogs();

// -------- TAGS --------
        JSONArray tags = new JSONArray();
        JSONObject tag = new JSONObject();
        tag.put("name", "Mantenimiento");
        tag.put("state", "Activo");
        tags.put(tag);

        // -------- LOGBOOKS --------
        JSONArray logbooks = new JSONArray();
        JSONObject logbook = new JSONObject();
        logbook.put("name", "Controls");
        logbook.put("owner", "admin");
        logbook.put("id", "3789165624649568920");
        logbooks.put(logbook);

        // -------- ATTACHMENT 1 (imagen) --------
        JSONObject img = new JSONObject();
        img.put("id", "olog_image10241718654167649265");
        img.put("filename", "olog_image10241718654167649265.png");
        img.put("uniqueFilename", "olog_image10241718654167649265.png");
        img.put("file", "olog_image10241718654167649265.png");
        img.put("fileMetadataDescription", "image/png");
        img.put("thumbnail", false);

        // -------- ATTACHMENT 2 (.bob) --------
//        JSONObject bob = new JSONObject();
//        bob.put("id", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar");
//        bob.put("filename", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar.bob");
//        bob.put("uniqueFilename", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar.bob");
//        bob.put("file", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar.bob");
//        bob.put("fileMetadataDescription", "application/octet-stream");
//        bob.put("thumbnail", false);
        // -------- ATTACHMENTS ARRAY --------
        JSONArray attachments = new JSONArray();
        attachments.put(img);
//        attachments.put(bob);

        // -------- LOG --------
        JSONObject log = new JSONObject();
        log.put("id", 1);
        log.put("owner", "luis");
        log.put("source", "server");
        log.put("level", "Problem");
        log.put("title", "prueba");
        log.put("createdDate", 1717876781000L);
        log.put("modifiedDate", 1717876781000L);
        log.put("description", "prueba 1");

        log.put("tags", tags);
        log.put("logbooks", logbooks);
        log.put("attachments", attachments);

        // -------- ROOT --------
        JSONArray logsArray = new JSONArray();
        logsArray.put(log);

        JSONObject root = new JSONObject();
        root.put("logs", logsArray);

        resp.getOutputStream().write(root.toString().getBytes());
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getOutputStream().close();
    }

    private void requestLogs() {

        //1. Obtener la conexion del datasource
        //2. Hacer la solicitud con los parametros dentro de
        /*
                owner = [luis]   
                size = [30]    
                level = [Info,Problem,Suggestion,Urgent]    
                tz = [America/Caracas]                    
                start = [20 minutes]                  
                logbooks = [Diagnostics,Controls,Operations]   
                end = [now]                    
                from = [0]               
                sort = [down]          
                title = [hola mundo]   
                tags = [Mantenimiento,Fallo Software,Upgrade,Calibracion]   
                desc = [un texto]          

                Usar propertiesNames que contiene los nombres de los campos
         */
        //3. Crear query de la tabla
        
        //Nombre de la tabla
        String table_name = appender.getConnectionProperties().get(TABLE_NAME_PROPERTY);
        
        //Fuente de datos
        DataSource ds = appender.getDataSource();
//        
//          try (Connection connection = dataSource.getConnection()) {
//                String insertQuery = insertQueryTemplate.replaceAll("TABLENAME", this.connectionProperties.get(TABLE_NAME_PROPERTY));
//                try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) {
//                    insertStatement.setLong(1, id);
//                    insertStatement.setString(2, owner);
//                    insertStatement.setString(3, level);
//                    insertStatement.setString(4, description);
//                    insertStatement.setString(5, title);
//                    insertStatement.setLong(6, createdDate);
//                    insertStatement.setString(7, tags);
//                    insertStatement.setString(8, logbooks);
//                    insertStatement.setString(9, attachmentsPath.substring(0, attachmentsPath.length() - 1));
//
//                    //Submit the form
//                    insertStatement.executeUpdate();
//                } catch (Exception e) {
//                    LOGGER.info("Error inserting a record into the DataSource {}", this.connectionProperties.get(TABLE_NAME_PROPERTY));
//                }
//            } catch (SQLException ex) {
//
//            }
    }
}
