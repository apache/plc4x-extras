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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.plc4x.merlot.logrecorder.appender.MerlotLogRecorderJDBCAppender;
import org.apache.plc4x.merlot.logrecorder.core.MerlotLogRecorderFileExplorer;
import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

public class MerlotLogRecorderLog extends HttpServlet {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotLogRecorderLog.class);
    private final BundleContext ctx;

    public MerlotLogRecorderLog(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOGGER.info("Retrieving attachments from the log");
        recoveryAttachmentAndSend(req, resp);
    }

    private void recoveryAttachmentAndSend(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String[] info = req.getPathInfo().substring(1).split("/");

        // Every file has the following name in the file system:: olog_<nameFile.extension>
        
        /*Note:
        info[0] This is the log ID for the log to which the attachments belong
        info[1] This is the name of the attached file
        */
        String searchFile = String.format("%s", info[1]);

        //MerlotLogRecorderFileExplorer: Look for the attached file, which should be located in the Karaf local file system at “data/tmp”
        File attachedFile = MerlotLogRecorderFileExplorer.findFileByFilename(searchFile, ctx);

        if (attachedFile != null) {
           LOGGER.info("The attached file exists");
        } else {
            LOGGER.info("The attached file does not exist");
        }
        
        
        //Sending the serialized response to Phoebus
        resp.getOutputStream().write(Files.readAllBytes(attachedFile.toPath()));
        resp.getOutputStream().flush();
        resp.getOutputStream().close();
    }

}
