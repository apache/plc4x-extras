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
package org.apache.plc4x.merlot.logrecorder.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.apache.plc4x.merlot.logrecorder.api.MerlotLogRecorderAction;
import org.json.JSONObject;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

public class MerlotLogRecorderActionImpl implements MerlotLogRecorderAction {

    private  EventAdmin admin;
    private final static String MERLOT_OLOG_EVENT_TOPIC = "merlot/olog";

    public MerlotLogRecorderActionImpl(EventAdmin admid) {
        this.admin = admid;
    }

    public MerlotLogRecorderActionImpl(){}
    @Override
    public void publishEvent(Event evt) {

        if (this.admin != null){
            this.admin.postEvent(evt);
        }

    }

    @Override
    public void prepareAndSendMessage(JSONObject ologMessage) {
        Event evtMessage = MerlotLogRecorderActionProcessor.readAndProcessMessage(ologMessage);
        publishEvent(evtMessage);
    }

    protected class MerlotLogRecorderActionProcessor {

        private MerlotLogRecorderActionProcessor() {
        }

        public static Event readAndProcessMessage(JSONObject ologMessage) {
            Event ev = null;
            Map<String, Object> properties = new HashMap();

            properties.put("id", ologMessage.getLong("id"));
            properties.put("owner", ologMessage.getString("owner"));
            properties.put("level", ologMessage.getString("level"));
            properties.put("description", ologMessage.getString("description"));
            properties.put("title", ologMessage.getString("title"));
            properties.put("createdDate", ologMessage.getLong("createdDate"));
            properties.put("tags", ologMessage.getString("tags"));
            properties.put("logbooks", ologMessage.getString("logbooks"));
            properties.put("attachmentsPath", ologMessage.getString("attachmentsPath"));
            
            
            ev = new Event(MERLOT_OLOG_EVENT_TOPIC, properties);
            return ev;
        }
    }
}
