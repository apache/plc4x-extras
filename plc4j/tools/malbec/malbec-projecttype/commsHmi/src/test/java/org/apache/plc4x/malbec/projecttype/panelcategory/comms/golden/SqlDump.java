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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.golden;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class SqlDump {

    private SqlDump() {
    }

    static String dump(File dbFile) throws SQLException {
        StringBuilder out = new StringBuilder();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {

            for (String table : tables(stmt)) {
                List<String> columns = columns(stmt, table);
                out.append("CREATE TABLE ").append(table).append(" (");
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(columns.get(i));
                }
                out.append(");\n");

                String orderBy = String.join(", ", columns);
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + quote(table)
                        + " ORDER BY " + orderBy)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    while (rs.next()) {
                        out.append("INSERT INTO ").append(table).append(" VALUES (");
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            if (i > 1) {
                                out.append(", ");
                            }
                            out.append(literal(rs, i));
                        }
                        out.append(");\n");
                    }
                }
            }
        }
        return out.toString();
    }

    private static List<String> tables(Statement stmt) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
                        + " AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private static List<String> columns(Statement stmt, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private static String literal(ResultSet rs, int index) throws SQLException {
        Object value = rs.getObject(index);
        if (value == null) {
            return "NULL";
        }
        if (value instanceof byte[] bytes) {
            StringBuilder hex = new StringBuilder("X'");
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.append('\'').toString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
