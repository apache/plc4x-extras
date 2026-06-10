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

import java.io.IOException;
import java.util.List;
import static javax.management.Query.value;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.plc4x.merlot.logrecorder.servlets.core.MerlotServiceManagedLogParameters;
import org.apache.plc4x.merlot.logrecorder.servlets.core.MerlotServiceManagedLogParameters.Level;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerlotLogRecorderLevel extends HttpServlet {

    private MerlotServiceManagedLogParameters sm;

    public MerlotLogRecorderLevel(MerlotServiceManagedLogParameters sm) {
        this.sm = sm;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getOutputStream().write(createListLevels().getBytes());
        resp.getOutputStream().flush();
        resp.getOutputStream().close();
    }

    private String createListLevels() {
        List<Level> levels = sm.getLevels();
        JSONArray levelsArray = new JSONArray();
        
        levels.forEach(l -> {
            JSONObject strLevelsResponse = new JSONObject();
            strLevelsResponse.put(l.getKey(), l.getDescription());
            levelsArray.put(strLevelsResponse);
        });

        
        return levelsArray.toString();
    }

}
