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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class MerlotLogRecorderLog extends HttpServlet {

    private final ObjectMapper mapper = new ObjectMapper();

 
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processMultipart(req, resp);
    }

    private void processMultipart(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Forzar codificación y tipo de contenido desde el inicio de la respuesta
        resp.setContentType("application/json;charset=UTF-8");

        String contentType = req.getContentType();
        if (contentType == null || !contentType.contains("boundary=")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"La petición no es Multipart válida.\"}");
            return;
        }

        String boundaryStr = "--" + contentType.substring(contentType.indexOf("boundary=") + 9);
        byte[] boundaryBytes = boundaryStr.getBytes(StandardCharsets.UTF_8);

        try {
            // 1. Leer el flujo de datos completo en formato de bytes puros
            byte[] bodyBytes = readAllBytes(req.getInputStream());

            // Marcadores binarios para delimitar las zonas dentro del stream
            byte[] logEntryMarker = "name=\"logEntry\"".getBytes(StandardCharsets.UTF_8);
            byte[] filesMarker = "name=\"files\"".getBytes(StandardCharsets.UTF_8);
            byte[] doubleLineBreak = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

            String logEntryJson = "";

            // =================================================================
            // EXTRACCIÓN DEL JSON DEL LOG ENTRY
            // =================================================================
            int logEntryIndex = findIndex(bodyBytes, logEntryMarker, 0);
            if (logEntryIndex != -1) {
                int inicioJson = findIndex(bodyBytes, "{".getBytes(StandardCharsets.UTF_8), logEntryIndex);
                int finBloque = findIndex(bodyBytes, boundaryBytes, inicioJson);
                
                // Buscar de atrás hacia adelante el cierre del objeto JSON '}'
                int finJson = inicioJson;
                for (int i = finBloque - 1; i >= inicioJson; i--) {
                    if (bodyBytes[i] == '}') {
                        finJson = i + 1;
                        break;
                    }
                }
                logEntryJson = new String(bodyBytes, inicioJson, (finJson - inicioJson), StandardCharsets.UTF_8);
            }

            // =================================================================
            // EXTRACCIÓN Y SALVADO DE LA IMAGEN ADJUNTA
            // =================================================================
            int filesIndex = findIndex(bodyBytes, filesMarker, 0);
            if (filesIndex != -1) {
                int inicioImagenByte = findIndex(bodyBytes, doubleLineBreak, filesIndex) + 4;
                int finImagenByte = findIndex(bodyBytes, boundaryBytes, inicioImagenByte) - 2; // Quita \r\n

                if (inicioImagenByte > 3 && finImagenByte > inicioImagenByte) {
                    int longitudImagen = finImagenByte - inicioImagenByte;
                    byte[] imageBytes = new byte[longitudImagen];
                    
                    // Copia exacta sin decodificación de texto intermedia
                    System.arraycopy(bodyBytes, inicioImagenByte, imageBytes, 0, longitudImagen);
                    System.out.println("Imagen capturada con éxito. Tamaño total: " + imageBytes.length + " bytes.");

                    // Almacenamos físicamente el archivo en el directorio raíz de Karaf
                    try (FileOutputStream fos = new FileOutputStream("pantallazo_recuperado.png")) {
                        fos.write(imageBytes);
                    }
                }
            }

            // =================================================================
            // CONSTRUCCIÓN DE LA RESPUESTA JSON REQUERIDA POR PHOEBUS
            // =================================================================
            if (logEntryJson == null || logEntryJson.trim().isEmpty()) {
                throw new IllegalArgumentException("No se pudo extraer un bloque JSON válido de 'logEntry'");
            }

            Map<String, Object> logMap = mapper.readValue(logEntryJson, Map.class);
            ObjectNode responseJson = mapper.createObjectNode();
            
            // Atributos obligatorios del payload Olog del cliente Phoebus
            responseJson.put("id", 9999);
            responseJson.put("owner", "karaf-server");
            responseJson.put("source", "Phoebus Desktop");
            responseJson.put("state", "Active");
            
            responseJson.put("description", (String) logMap.getOrDefault("description", "Log creado exitosamente"));
            responseJson.put("title", (String) logMap.getOrDefault("title", "Log Entry"));
            responseJson.put("level", (String) logMap.getOrDefault("level", "Info"));

            responseJson.set("logbooks", mapper.valueToTree(logMap.get("logbooks")));
            responseJson.set("tags", mapper.valueToTree(logMap.get("tags")));
            responseJson.set("properties", mapper.createArrayNode());
            responseJson.set("attachments", mapper.createArrayNode());

            // Despachar los bytes de respuesta asegurando el vaciado del buffer (flush)
            resp.setStatus(HttpServletResponse.SC_OK); // HTTP 200
            resp.getOutputStream().write(responseJson.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Excepción procesando Multipart de Olog: " + e.getMessage());
            e.printStackTrace();
            
            // En caso de catástrofe, responder con un JSON de error estructurado en lugar de dejar el body en blanco
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // HTTP 500
            try {
                resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                resp.getWriter().flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
    // Algoritmo helper para buscar arreglos de bytes lineales (Simula un String.indexOf)
    private int findIndex(byte[] source, byte[] target, int start) {
        if (target.length == 0) return 0;
        outer:
        for (int i = start; i < source.length - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // Encapsula y vuelca el InputStream completo a un mapa de bytes directo en memoria
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    }


