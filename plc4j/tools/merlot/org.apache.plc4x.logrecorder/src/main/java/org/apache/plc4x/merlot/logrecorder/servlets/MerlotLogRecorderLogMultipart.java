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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;
import javax.security.auth.login.LoginException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.json.JSONObject;
import org.apache.plc4x.merlot.logrecorder.api.MerlotLogRecorderAction;
import org.apache.plc4x.merlot.logrecorder.core.MerlotLogRecorderSecurityAction;
import org.apache.plc4x.merlot.logrecorder.exception.MerlotLogRecorderSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLogRecorderLogMultipart extends HttpServlet {

    private final static Logger LOGGER = LoggerFactory.getLogger(MerlotLogRecorderLogMultipart.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private Random random = new Random();
    private MerlotLogRecorderAction merlotAction;

    public MerlotLogRecorderLogMultipart(MerlotLogRecorderAction merlotAction) {
        this.merlotAction = merlotAction;
    }

    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processMultipart(req, resp);
    }

    private void processMultipart(HttpServletRequest req, HttpServletResponse resp
    ) throws ServletException, IOException {
        String username = "";
        String password = "";
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String base64Credentials = authHeader
                    .substring("Basic ".length())
                    .trim();
            byte[] credDecoded = java.util.Base64.getDecoder().decode(
                    base64Credentials
            );
            String credentials = new String(
                    credDecoded,
                    java.nio.charset.StandardCharsets.UTF_8
            );
            String[] values = credentials.split(":", 2);
            username = values[0];
            password = values[1];
        }

        //Getting the application sections
        for (Part part : req.getParts()) {
            String directoryPath = System.getenv("MERLOT_OLOG_ATTACHMENT");
            String fileName = part.getSubmittedFileName();

            if (part.getContentType().equals("application/json")) {
                try (InputStream is = part.getInputStream()) {
                    JsonNode node = mapper.readTree(is);

                    try {
                        if (MerlotLogRecorderSecurityAction.validateCredentials(username, password)) {
                            createOlog(node, resp, username);
                        } else {
                            throw new MerlotLogRecorderSecurityException(
                                    String.format("Unable to log in to the system with those credentials:  Username:{} Passwor:{}", username, password));
                        }
                    } catch (MerlotLogRecorderSecurityException | LoginException ex) {
                        LOGGER.info("MerlotLogRecorderSecurity: Error validating the user {}", username);
                        resp.getOutputStream().close();
                        return;
                    }

                }
            } else {
                //It is assumed that the attachments were added from the Phoebus Creaty Log
                try {
                    if (MerlotLogRecorderSecurityAction.validateCredentials(username, password)) {
                        saveFile(part, directoryPath, fileName);
                    } else {
                        throw new MerlotLogRecorderSecurityException(
                                String.format("Unable to log in to the system with those credentials:  Username:{} Passwor:{}", username, password));
                    }
                } catch (MerlotLogRecorderSecurityException | LoginException ex) {
                    LOGGER.info("MerlotLogRecorderSecurity: Error validating the user {}", username);
                    resp.getOutputStream().close();
                    return;
                }

            }
        }
    }

    //TODO: The file must be located in a shared directory on the server. The path
    // must be stored in the table and updated if it is deleted (see Apache Lucene)
    private void saveFile(Part part, String directoryPath, String fileName)
            throws IOException {
        if (part == null) {
            return;
        }
        File directory = new File(directoryPath);
        File destinationFile = new File(
                directory,
                String.format("olog_%s", fileName)
        );
        try (
                InputStream is = part.getInputStream(); FileOutputStream fos = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[16384];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            LOGGER.info("File saved in: {}", destinationFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createOlog(
            JsonNode node,
            HttpServletResponse resp,
            String userCheck
    ) throws IOException {
        //The ID must be generated randomly; each log must have a unique ID (The ID generation process is currently being improved)
        long id = node.path("id").asLong(Math.abs(random.nextLong()));
        String user = node.path("owner").asText(userCheck);
        String level = node.get("level").asText();
        String description = node.get("description").asText();
        String title = node.get("title").asText();

        long createdDate = node.path("createdDate").asLong(System.currentTimeMillis());

        String attachments = extractJSONData(node.get("attachments"), "attachments");
        String logbooks = extractJSONData(node.get("logbooks"), "logbooks");
        String tags = extractJSONData(node.get("tags"), "tags");

        JSONObject strMultpart = new JSONObject();

        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_OK);

        strMultpart.put("id", id);
        strMultpart.put("owner", user);
        strMultpart.put("level", level);
        strMultpart.put("title", title);
        strMultpart.put("description", description);
        strMultpart.put("createdDate", createdDate);
        strMultpart.put("attachmentsPath", attachments);

        resp.getOutputStream().write(strMultpart.toString().getBytes());
        resp.getOutputStream().flush();
        resp.getOutputStream().close();

        //If they are included in the log sent to Phoebus, they must be sent as a JSON array (Optional)
        strMultpart.put("tags", tags);
        strMultpart.put("logbooks", logbooks);
        //Olog message
        this.merlotAction.prepareAndSendMessage(strMultpart);
    }

    public String extractJSONData(JsonNode n, String nodeName) {
        StringBuilder data = new StringBuilder();

        switch (nodeName) {
            case "attachments":
                if (n != null && n.isArray()) {
                    for (JsonNode archivoNode : n) {
                        String filePath = archivoNode.get("uniqueFilename").asText();
                        data.append(String.format("olog_%s", filePath));
                        data.append(",");
                    }
                }
                break;
            case "tags":
                if (n != null && n.isArray()) {
                    for (JsonNode tagNode : n) {
                        String tagName = tagNode.get("name").asText();
                        data.append(String.format("%s", tagName));
                        data.append(",");
                    }
                }
                break;
            case "logbooks":
                for (JsonNode logbookNode : n) {
                    String logbookName = logbookNode.get("name").asText();
                    data.append(String.format("%s", logbookName));
                    data.append(",");
                }
                break;
            default:
                LOGGER.info("Error data cannot be extracted");
        }

        String dataResult = data.toString().substring(0, data.toString().length() - 1);
        return dataResult;

    }
    

}
