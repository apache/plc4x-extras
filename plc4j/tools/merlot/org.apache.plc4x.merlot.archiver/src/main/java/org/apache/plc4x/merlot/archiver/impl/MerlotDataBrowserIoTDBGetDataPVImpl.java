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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.apache.plc4x.merlot.archiver.core.MerlotPBRawSerializer;
import org.epics.vtype.VType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotDataBrowserIoTDBGetDataPVImpl extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotDataBrowserRTGetDataPVImpl.class);

    private final Pattern opti_pattern = Pattern.compile("optimized_11520\\(([^)]+)\\)");
    private final Pattern ncount_pattern = Pattern.compile("ncount\\(([^)]+)\\)");
    private final Pattern count_pattern = Pattern.compile("count_3600\\(([^)]+)\\)");

    private Matcher opti_matcher = null;
    private Matcher ncount_matcher = null;
    private Matcher count_matcher = null;

    private final MerlotHtc mhtc;

    public MerlotDataBrowserIoTDBGetDataPVImpl(MerlotHtc mhtc) {
        this.mhtc = mhtc;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        
        String from = req.getParameter("from");
        String to = req.getParameter("to");
        String[] pvs = req.getParameterValues("pv");
        
       
        LOGGER.info("Inicio Servlet.");
        if ((null == from) || (null == to)) {
            return;
        }
        if ((null == pvs) || (pvs.length == 0)) {
            return;
        }

//        ${__Random(1,50)}
//        ${__groovy(def h=new Random().nextInt(23)+1; "2026-03-18T${h.toString().padLeft(2,'0')}:00:00.000000Z")}
        
        resp.setContentType("application/octet-stream");
        for (String pv : pvs) {
            opti_matcher = opti_pattern.matcher(pv);
            ncount_matcher = ncount_pattern.matcher(pv);
            count_matcher = count_pattern.matcher(pv);

            if (opti_matcher.matches()) {
                LOGGER.info("optimized_11520(pv) not supported.");
            } else if (ncount_matcher.matches()) {
                String strpv = ncount_matcher.group(1);
                int countpv = mhtc.countPVs(strpv, from, to);
                LOGGER.info("Number of events: " + countpv);
                resp.getWriter().print(countpv);
                resp.getWriter().close();
            } else if (count_matcher.matches()) {
                LOGGER.info("count_3600(pv) not supported.");
            } else {
                createRawResponse(pv, from, to, resp.getOutputStream());
                resp.getOutputStream().close();
            }
        }

    }

    private void createRawResponse(String pv, String from, String to, OutputStream out) throws IOException {
        
        List<VType> values = mhtc.getPVs(pv, from, to);
        
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        MerlotPBRawSerializer.serializeIoTDBToPBRaw(values, pv, bout);
        ByteBuf buf = Unpooled.wrappedBuffer(bout.toByteArray());
//        System.out.println(ByteBufUtil.prettyHexDump(buf));
        out.write(bout.toByteArray());
    }

}
