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
package org.apache.plc4x.merlot.archiver.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Dictionary;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.osgi.service.cm.ManagedServiceFactory;
import org.apache.plc4x.merlot.scheduler.api.Scheduler;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.event.EventAdmin;
import org.slf4j.LoggerFactory;

/**
 *
 * 
 * @author cgarcia
 */
public class MerlotDataBrowserSupportImpl extends MerlotPvHtcCollectorImpl  {
    
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotDataBrowserSupportImpl.class);    
    
//    private HttpServer server;

    
    public MerlotDataBrowserSupportImpl(Scheduler scheduler, EventAdmin eventAdmin, MerlotGPClient gpClient) {
        super(scheduler, eventAdmin, gpClient);
    }

    @Override
    public void start() {
        super.start();
//        try {
//            server = HttpServer.create(new InetSocketAddress(2000), 0);
//            // Create a context for a specific path and set the handler
//            server.createContext("/request/bpl/searchForPVsRegex", new MyHandler());  
//            server.createContext("/request/data/getData.raw", new MyHandler());             
//            server.setExecutor(null); // Use the default executor
//            server.start();
//            System.out.println("Server is running on port 2000");            
//        }  catch (IOException e) {
//            System.out.println("Error starting the server: " + e.getMessage());
//        }
    }

    @Override
    public void stop() {
        super.stop();
//        server.stop(10);
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void updated(String string, Dictionary<String, ?> dctnr) throws ConfigurationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleted(String string) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    public void StartServer(){
        
    }
    
    public void StopServer(){ 
        
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
            String response = "uno\r\ndos\r\ntres\r\n";
            
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }   
    
    static class MyHandler2 implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException 
        {
            // Handle the request
            System.out.println("2 Protocol: " + exchange.getProtocol());
            System.out.println("2 Method  : " + exchange.getRequestMethod()); 
            System.out.println("2 URI     : " + exchange.getRequestURI().toString());            
            String response = "uno\r\ndos\r\ntres\r\n";

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }         
}
