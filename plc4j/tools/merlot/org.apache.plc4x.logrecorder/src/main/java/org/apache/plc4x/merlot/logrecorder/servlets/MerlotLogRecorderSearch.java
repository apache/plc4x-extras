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

    private static final Long TIME_MINUTE_TO_MILISECOND = 60_000L;
    private static final Long TIME_HOUR_TO_MILISECOND = 3_600_000L;
    private static final Long TIME_DAY_TO_MILISECOND = 86_400_000L;
    private static final Long TIME_WEEK_TO_MILISECOND = 604_800_000L;
    private static final Long TIME_MONTH_TO_MILISECOND = 2_592_000_000L;
    private static final Long TIME_YEAR_TO_MILISECOND = 31_536_000_000L;

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

        // -------- LOG --------
        JSONArray logsArray = new JSONArray();
        for (Data dataLog : requestLogs) {

            JSONArray arrayTags = writeToJson(dataLog.getTags(), "tag");
            JSONArray arrayAttachments = writeToJson(dataLog.getAttachments(), "attachment");
            JSONArray arrayLogbooks = writeLogbooks(dataLog.getLogbooks(), dataLog.getOwner());

            JSONObject log = new JSONObject();
            log.put("id", dataLog.getId());
            log.put("owner", dataLog.getOwner());
            log.put("source", "source");//TODO: This is where the machine's parameters should be listed—the machine that generated the log—but Phoebus doesn't send them.
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
                if (indexExt <= 0) {
                    continue;
                }
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

        Long start = parseRelativeTime(req.getParameter("start"));
        Long end = parseRelativeTime(req.getParameter("end"));

        

        addBetweenFilter(sql, params, "createdDate", start, end);

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

    private Long parseRelativeTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        value = value.trim().toLowerCase();
        long now = System.currentTimeMillis();

        // Case 1: “now” date
        if (value.equals("now")) {
            return now;
        }

        // Case 2: absolute date “yyyy-MM-dd HH:mm:ss”
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            try {
                java.time.LocalDateTime dt = java.time.LocalDateTime.parse(
                        value,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                );
                return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception e) {
                return null;
            }
        }

        //  Case 3 Date buttons: regarding “5 minutes,” “12 hours,” “1 day,”...
        String[] parts = value.split(" ");
        if (parts.length != 2) {
            return null;
        }

        long amount;
        try {
            amount = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        String unit = parts[1].toLowerCase();

        switch (unit) {
            case "minute":
            case "minutes":
                return now - amount * TIME_MINUTE_TO_MILISECOND;

            case "hour":
            case "hours":
                return now - amount * TIME_HOUR_TO_MILISECOND;

            case "day":
            case "days":
                return now - amount * TIME_DAY_TO_MILISECOND;

            case "week":
            case "weeks":
                return now - amount * TIME_WEEK_TO_MILISECOND;

            case "month":
            case "months":
                return now - amount * TIME_MONTH_TO_MILISECOND;

            case "year":
            case "years":
                return now - amount * TIME_YEAR_TO_MILISECOND;

            default:
                return null;
        }
    }

    private void addBetweenFilter(StringBuilder sql, List<Object> params,
            String column, Long start, Long end) {
        if (start != null && end != null) {
            sql.append(" AND ").append(column).append(" BETWEEN ? AND ? ");
            params.add(start);
            params.add(end);
        } else if (start != null) {
            sql.append(" AND ").append(column).append(" >= ? ");
            params.add(start);
        } else if (end != null) {
            sql.append(" AND ").append(column).append(" <= ? ");
            params.add(end);
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
