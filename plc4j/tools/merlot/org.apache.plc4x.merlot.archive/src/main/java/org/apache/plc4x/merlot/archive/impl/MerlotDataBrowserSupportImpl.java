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
package org.apache.plc4x.merlot.archive.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.apache.plc4x.merlot.archive.api.MerlotGPClient;
import org.apache.plc4x.merlot.scheduler.api.Scheduler;
import org.osgi.service.event.EventAdmin;
import org.slf4j.LoggerFactory;


public class MerlotDataBrowserSupportImpl extends MerlotPvHtcCollectorImpl {
    
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotDataBrowserSupportImpl.class);    
    
    private HttpServer server;

    
    public MerlotDataBrowserSupportImpl(Scheduler scheduler, EventAdmin eventAdmin, MerlotGPClient gpClient) {
        super(scheduler, eventAdmin, gpClient);
    }

    @Override
    public void start() {
        super.start();
        try {
            server = HttpServer.create(new InetSocketAddress(2000), 0);
            // Create a context for a specific path and set the handler
            server.createContext("/request/bpl/searchForPVsRegex", new MyHandler());            
            server.setExecutor(null); // Use the default executor
            server.start();
            System.out.println("Server is running on port 8000");            
        }  catch (IOException e) {
            System.out.println("Error starting the server: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        super.stop();
        server.stop(10);
    }
    
    // Define a custom HttpHandler
    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException 
        {
            // Handle the request
            System.out.println("Protocol: " + exchange.getProtocol());
            System.out.println("Method  : " + exchange.getRequestMethod()); 
            System.out.println("URI     : " + exchange.getRequestURI().toString());            
            String response = "uno\ndos\ntres";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }    
    
}
