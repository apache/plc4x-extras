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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    private final Pattern opti_pattern = Pattern.compile("optimized_\\w+\\(([^)]+)\\)");

    private final Pattern ncount_pattern = Pattern.compile("ncount\\(([^)]+)\\)");

    private final Pattern count_pattern = Pattern.compile("count_\\w+\\(([^)]+)\\)");

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
        if (from == null || to == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'from' or 'to'");
            return;
        }
        if (pvs == null || pvs.length == 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'pv' parameter");
            return;
        }

        if (pvs.length > 1) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Multiple PVs not supported");
            return;
        }

        String pv = pvs[0];

        opti_matcher = opti_pattern.matcher(pv);
        ncount_matcher = ncount_pattern.matcher(pv);
        count_matcher = count_pattern.matcher(pv);

        resp.setContentType("application/octet-stream");
        try (OutputStream out = resp.getOutputStream()) {
            if (opti_matcher.matches()) {
                resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "optimized_* not supported");
                return;
            } else if (ncount_matcher.matches()) {
                String strpv = ncount_matcher.group(1);
                int countpv = mhtc.countPVs(strpv, from, to);
                byte[] bytes = Integer.toString(countpv).getBytes(StandardCharsets.UTF_8);
                resp.setContentType("text/plain; charset=utf-8");
                resp.setContentLength(bytes.length);
                out.write(bytes);
                out.flush();
                return;
            } else if (count_matcher.matches()) {
                resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "count_* not supported");
                return;
            } else {
                boolean ok = createRawResponse(pv, from, to, out, resp);
                if (!ok) {
                    return;
                }
                out.flush();
            }
        }
    }

    private boolean createRawResponse(String pv, String from, String to, OutputStream out, HttpServletResponse resp) throws IOException {
        List<VType> values = mhtc.getPVs(pv, from, to);
        if (values == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No values for pv: " + pv);
            return false;
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        MerlotPBRawSerializer.serializeIoTDBToPBRaw(values, pv, bout);
        byte[] payload = bout.toByteArray();
        resp.setContentType("application/octet-stream");
        out.write(payload);
        return true;
    }

}
