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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.json.JSONObject;

public class MerlotLogRecorderLogMultipart extends HttpServlet {

    private final ObjectMapper mapper = new ObjectMapper();
    private Random random = new Random();

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        processMultipart(req, resp);
    }

    private void processMultipart(
        HttpServletRequest req,
        HttpServletResponse resp
    ) throws ServletException, IOException {
        String usuario = "";
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
            usuario = values[0];
            password = values[1];

            //TODO: Realizar la autenticación con JAAS
        }

        //Obtener las secciones de la solicitud
        for (Part part : req.getParts()) {
            String directoryPath = "data/tmp";
            String fileName = part.getSubmittedFileName();

            if (part.getContentType().equals("application/json")) {
                String json = new String(part.getInputStream().readAllBytes());
                try (InputStream is = part.getInputStream()) {
                    JsonNode node = mapper.readTree(is);
                    createOlog(node, resp, usuario);
                }
            } else {
                //Se entiende que es un archivo adjunto
                saveFile(part, directoryPath, fileName);
            }
        }
    }

    private void saveFile(Part part, String directoryPath, String fileName)
        throws IOException {
        File directory = new File(directoryPath);
        File destinationFile = new File(
            directory,
            String.format("%s_olog_%s", System.currentTimeMillis(), fileName)
        );
        try (
            InputStream is = part.getInputStream();
            FileOutputStream fos = new FileOutputStream(destinationFile)
        ) {
            byte[] buffer = new byte[16384];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            System.out.println(
                "Archivo guardado en: " + destinationFile.getAbsolutePath()
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createOlog(
        JsonNode node,
        HttpServletResponse resp,
        String userCheck
    ) throws IOException {
        //El id se debe generar aleatoriamente, cada log debe tener un id unico
        long id = node.path("id").asLong(Math.abs(random.nextLong()));
        String user = node.path("owner").asText(userCheck);
        String level = node.get("level").asText();
        String title = node.get("title").asText();
        long createdDate = node
            .path("createdDate")
            .asLong(System.currentTimeMillis());

        JSONObject strMultpart = new JSONObject();

        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_OK);

        strMultpart.put("id", id);
        strMultpart.put("owner", user);
        strMultpart.put("level", level);
        strMultpart.put("title", title);
        strMultpart.put("createdDate", createdDate);

        resp.getOutputStream().write(strMultpart.toString().getBytes());
        resp.getOutputStream().close();
    }
}
