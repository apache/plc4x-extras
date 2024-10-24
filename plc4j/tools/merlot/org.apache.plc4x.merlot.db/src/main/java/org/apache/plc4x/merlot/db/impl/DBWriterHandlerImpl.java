/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.merlot.db.impl;

import io.netty.buffer.ByteBufUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.api.DBWriterHandler;
import org.epics.pvdata.copy.CreateRequest;
import org.epics.pvdata.misc.BitSet;
import org.epics.pvdata.monitor.Monitor;
import org.epics.pvdata.monitor.MonitorElement;
import org.epics.pvdata.pv.MessageType;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Structure;
import org.epics.pvdatabase.pva.MonitorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
* This service is responsible for calling the writing of a PvRecord through 
* the associated PlcItem.
*/
public class DBWriterHandlerImpl implements DBWriterHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBWriterHandlerImpl.class);
    private  CreateRequest createRequest = CreateRequest.create();
    private Map<Monitor, DBRecord> recordMonitors = new HashMap<>();
    
    private MonitorElement element  = null;
    private PVStructure structure   = null;
    private BitSet changedBitSet    = null;
    private BitSet overrunBitSet    = null;
    
    @Override
    public void monitorConnect(Status status, Monitor monitor, Structure structure) {

    }  

    @Override
    public void monitorEvent(Monitor monitor) {
        try 
        {
            element = monitor.poll();
            structure = element.getPVStructure();
            changedBitSet = element.getChangedBitSet();
            overrunBitSet = element.getOverrunBitSet();
                      
            if ((recordMonitors.containsKey(monitor)) && 
                 structure.getBooleanField("write_enable").get()) {
                if (changedBitSet.get(1) && 
                    (changedBitSet.length() == 2) &&
                    overrunBitSet.isEmpty()) {

                    final DBRecord dbRecord = recordMonitors.get(monitor);
                    final Optional<PlcItem> optPlcItem = dbRecord.getPlcItem();
                    LOGGER.info(ByteBufUtil.prettyHexDump(dbRecord.getWriteBuffer().get()));
                    
                    if (optPlcItem.isPresent()) {
                        optPlcItem.get().itemWrite(dbRecord.getWriteBuffer().get(), dbRecord.getByteOffset(), dbRecord.getBiteOffset());  
                    }

                }
            }
        } catch (Exception ex) {
             LOGGER.error(ex.getMessage());
             ex.printStackTrace();
        } finally {
            monitor.release(element);             
        }        
    }

      
    @Override
    public void unlisten(Monitor monitor) {
        recordMonitors.remove(monitor);
    }

    @Override
    public String getRequesterName() {
        return "getRequesterName()";
    }

    @Override
    public void message(String message, MessageType messageType) {
        
    }

    @Override
    public void putDBRecord(DBRecord dbRecord) {
        LOGGER.info("Monitor with fields =  {}", dbRecord.getFieldsToMonitor());
        PVStructure request = createRequest.createRequest(dbRecord.getFieldsToMonitor());
        Monitor monitor = MonitorFactory.create(dbRecord, this, request);
        if (null != monitor) {
            recordMonitors.put(monitor, dbRecord);
            monitor.start();
        } else {
            LOGGER.error("The monitor is 'null' for [{}]", dbRecord.getRecordName());
        }
    }

    @Override
    public void removeDBRecord(DBRecord dbRecord) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
