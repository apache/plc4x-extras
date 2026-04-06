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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.apache.plc4x.merlot.archiver.core.MerlotDecanterManagedService;
import org.apache.plc4x.merlot.archiver.core.MerlotPBRawSerializer;
import org.epics.vtype.VType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotDataBrowserRTGetDataPVImpl extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotDataBrowserRTGetDataPVImpl.class);
    private final MerlotHtc mhtc;

    public MerlotDataBrowserRTGetDataPVImpl(MerlotHtc mhtc) {
        this.mhtc = mhtc;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String from = req.getParameter("from");
        String to = req.getParameter("to");
        String[] pvs = req.getParameterValues("pv");
        LOGGER.info("Inicio Servlet.");
        if ((null == from) || (null == to)) return;
        if ((null == pvs) || (pvs.length == 0)) return;
        LOGGER.info(pvs[0] + " : " + from + " : " + to);
        for (String pv:pvs){
            PBRawResponse(pv, from, to, resp.getOutputStream());            
        }
        resp.getOutputStream().close();
    }
    
    /*
    * 
    */
    private void PBRawResponse(String pv, String init, String end, OutputStream out) {
        try {
            List<VType> values = mhtc.getPVs(pv, init, end);
            MerlotPBRawSerializer.serializeToPBRaw(values, pv, out);
        } catch (Exception ex){
            LOGGER.error(ex.getLocalizedMessage());
        }
        
    }
        
}
