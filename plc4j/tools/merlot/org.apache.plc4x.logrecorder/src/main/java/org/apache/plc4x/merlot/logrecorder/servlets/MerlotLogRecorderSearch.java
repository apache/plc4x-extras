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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.plc4x.merlot.logrecorder.appender.MerlotLogRecorderJDBCAppender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

public class MerlotLogRecorderSearch extends HttpServlet {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotLogRecorderSearch.class);
    private static final String TABLE_NAME_PROPERTY = "table.name";

    private Map<String, String[]> properties = new HashMap<>();
    private String[] parameters = {"owner", "level", "tags", "logbooks"};
    private MerlotLogRecorderJDBCAppender appender;

    public MerlotLogRecorderSearch(MerlotLogRecorderJDBCAppender appender) {
        this.appender = appender;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("application/json");
        Map<String, String[]> params = req.getParameterMap();

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }

        List<Data> requestLogs = requestLogs(req);

        //// -------- TAGS --------
//        JSONArray tags = new JSONArray();
//        JSONObject tag = new JSONObject();
//        tag.put("name", "Mantenimiento");
//        tag.put("state", "Activo");
//        tags.put(tag);
//
//        // -------- LOGBOOKS --------
//        JSONArray logbooks = new JSONArray();
//        JSONObject logbook = new JSONObject();
//        logbook.put("name", "Controls");
//        logbook.put("owner", "admin");
//        logbook.put("id", "3789165624649568920");
//        logbooks.put(logbook);
//
//        // -------- ATTACHMENT 1 (imagen) --------
//        JSONObject img = new JSONObject();
//        img.put("id", "olog_image10241718654167649265");
//        img.put("filename", "olog_image10241718654167649265.png");
//        img.put("uniqueFilename", "olog_image10241718654167649265.png");
//        img.put("file", "olog_image10241718654167649265.png");
//        img.put("fileMetadataDescription", "image/png");
//        img.put("thumbnail", false);
//
//        // -------- ATTACHMENT 2 (.bob) --------
////        JSONObject bob = new JSONObject();
////        bob.put("id", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar");
////        bob.put("filename", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar.bob");
////        bob.put("uniqueFilename", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar.bob");
////        bob.put("file", "1780935681953_olog_693577ee-dded-4f22-beb1-fc32b603b0f1_borrar.bob");
////        bob.put("fileMetadataDescription", "application/octet-stream");
////        bob.put("thumbnail", false);
//        // -------- ATTACHMENTS ARRAY --------
//        JSONArray attachments = new JSONArray();
//        attachments.put(img);
////        attachments.put(bob);

        // -------- LOG --------
        //Por cada objeto Data en la lista debe generarse un log
        JSONArray logsArray = new JSONArray();//Mi arreglo de logs
        for (Data dataLog : requestLogs) {

            JSONArray arrayTags = writeToJson(dataLog.getTags(), "tag");
            JSONArray arrayAttachments = writeToJson(dataLog.getAttachments(), "attachment");
            JSONArray arrayLogbooks = writeLogbooks(dataLog.getLogbooks(), dataLog.getOwner());

            JSONObject log = new JSONObject();
            log.put("id", dataLog.getId());
            log.put("owner", dataLog.getOwner());
            log.put("source", "valorFijo");
            log.put("level", dataLog.getLevel());
            log.put("title", dataLog.getTitle());
            log.put("createdDate", dataLog.getCreatedDate());
            log.put("modifiedDate", 0L);
            log.put("description", dataLog.getDescription());
            log.put("tags", arrayTags);
            log.put("logbooks", arrayLogbooks);
            log.put("attachments", arrayAttachments);

            logsArray.put(log);
        }

        // -------- ROOT --------
        JSONObject root = new JSONObject(); //Json general
        root.put("logs", logsArray);

        resp.getOutputStream().write(root.toString().getBytes());
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getOutputStream().flush();
        resp.getOutputStream().close();
    }

    private JSONArray writeToJson(String q, String namePeroperty) {
        JSONArray array = new JSONArray();
        System.out.println(q);
        if (q == null || q.isBlank()) {
            return array;
        }
        String[] splitQ = q.split(",");

        for (String sq : splitQ) {
            sq = sq.trim();
            if (sq.isEmpty()) {
                continue;
            }
            JSONObject node = new JSONObject();

            if (namePeroperty.equalsIgnoreCase("tag")) {
                node.put("name", sq);
                node.put("state", "Active");
            } else if (namePeroperty.equalsIgnoreCase("attachment")) {
                int indexExt = sq.lastIndexOf(".");
                if (indexExt <= 0) continue; 
                String base = sq.substring(0, indexExt);
                node.put("id", base);
                node.put("filename", sq);
                node.put("uniqueFilename", sq);
                node.put("file", sq);
                node.put("fileMetadataDescription", "image/png");//ojo
                node.put("thumbnail", false);

            }

            array.put(node);
        }

        return array;
    }

    private JSONArray writeLogbooks(String logbooks, String owner) {
        JSONArray array = new JSONArray();
        Random random = new Random();
        String[] splitLogbooks = logbooks.split(",");

        for (String logbook : splitLogbooks) {
            JSONObject lb = new JSONObject();

            lb.put("name", logbook);
            lb.put("owner", owner);
            lb.put("id", random.nextLong());
            array.put(lb);
        }
        return array;
    }

    private List<Data> requestLogs(HttpServletRequest req) {

        String tableName = appender.getConnectionProperties().get(TABLE_NAME_PROPERTY);
        StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName + " WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        addLikeFilter(sql, params, "owner", getParam(req, "owner"));
        addEqualsFilter(sql, params, "level", getParam(req, "level"));
        addInFilter(sql, params, "tags", req.getParameterValues("tags"));
        addInFilter(sql, params, "logbooks", req.getParameterValues("logbooks"));

        //Fuente de datos
        DataSource ds = appender.getDataSource();
        List<Data> data = new ArrayList<>();
        try (Connection connection = ds.getConnection(); PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {

                    long id = rs.getLong("id");
                    String owner = rs.getString("owner");
                    String level = rs.getString("level");
                    String tags = rs.getString("tags");
                    String logbooks = rs.getString("logbooks");
                    String attachments = rs.getString("attachments_path");
                    String title = rs.getString("title");
                    long createdDate = rs.getLong("createdDate");
                    String description = rs.getString("description");

                    data.add(new Data(id, owner, level, tags, logbooks, attachments, title, createdDate, description));

                    LOGGER.info("Id: {}\nOwner: {}\nTags: {}\nLevel: {}\nAttachments: {}\nTitle: {}\nCreatedDate: {}\nDescription_ {}",
                            id, owner, tags, level, attachments, title, createdDate, description);
                }
            } catch (SQLException e) {
                LOGGER.info("Error: {}", e.getMessage());
            }

        } catch (SQLException e) {
            LOGGER.info("Error2: {}", e.getMessage());
        }

        return data;
    }

    private String getParam(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private void addLikeFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" LIKE ? ");
            params.add("%" + value + "%");
        }
    }

    private void addEqualsFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ? ");
            params.add(value);
        }
    }

    private void addInFilter(StringBuilder sql, List<Object> params, String column, String[] values) {
        if (values != null && values.length > 0) {
            sql.append(" AND ").append(column).append(" IN (");
            sql.append(Arrays.stream(values).map(v -> "?").collect(Collectors.joining(",")));
            sql.append(")");
            params.addAll(Arrays.asList(values));
        }
    }

    @Getter
    @AllArgsConstructor
    class Data {

        private long id;
        private String owner;
        private String level;
        private String tags;
        private String logbooks;
        private String attachments;
        private String title;
        private long createdDate;
        private String description;

    }
}
