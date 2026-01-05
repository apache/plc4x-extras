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
package org.apache.plc4x.merlot.drv.simulated.impl;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.merlot.api.PlcDevice;
import org.apache.plc4x.merlot.api.PlcGeneralFunction;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.api.PlcModel;
import org.apache.plc4x.merlot.api.core.PlcStaticHelper;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cgarcia
 */
public class SimulatedPlcModelImpl  implements PlcModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatedPlcModelImpl.class);  
         
    private BundleContext bc;
    
    private PlcGeneralFunction gf;
    
    private PlcDevice plcDevice = null;    
    
    
    public SimulatedPlcModelImpl(BundleContext bc, PlcGeneralFunction gf) {
        this.bc = bc;
        this.gf = gf;
    }
    
    @Override
    public Set<String> listMemoryAreas() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Integer getMemoryAreaId(String memoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void createMemoryArea(Object dbRecord) {
        if (dbRecord instanceof String){
            LOGGER.info("Creating an unsupported memory area with String.");
            return;
        } else if (dbRecord instanceof PlcItem){
            final PlcItem plcItem = (PlcItem) dbRecord;
            Optional<PlcGroup> optPlcGroup = gf.getPlcItemGroup(plcItem.getItemUid());
            if (optPlcGroup.isEmpty()){
                LOGGER.info("000 Grupo no prsente...");
                return;
            }
            final PlcDevice plcDevice = gf.getPlcDevice(optPlcGroup.get().getGroupDeviceUid());     
            LOGGER.info("000");            
            if (null != plcDevice) {
                LOGGER.info("001");
                //Only for simulated driver                
                plcDevice.enable();
                final PlcConnection plcConnection = plcDevice.getPlcConnection();
                if (plcConnection.isConnected()) {
                    try {
                        plcConnection.connect();
                    } catch (Exception ex) {
                        LOGGER.info("Failure connecting '{}'.", ex.getMessage());
                    }
                }
                if (plcConnection.isConnected()) {
                                    LOGGER.info("002");
                    final ByteBuf byteBuf = plcItem.getItemByteBuf();
                    Optional<PlcValue> optPlcValue = PlcStaticHelper.ByteBufToPlcValue(byteBuf, plcItem.getItemPlcTag().getPlcValueType());
                    if (optPlcValue.isPresent()) {
                        try {
                            LOGGER.info("003");
                            final PlcWriteRequest.Builder builder = plcConnection.writeRequestBuilder();
                            builder.addTag(plcItem.getItemName(), plcItem.getItemPlcTag(), optPlcValue.get());
                            final PlcWriteRequest writeRequest = builder.build();
                            PlcWriteResponse writeResponse = writeRequest.execute().get(1, TimeUnit.SECONDS);
                                            LOGGER.info("004");
                            writeResponse.getTagNames().forEach( t->
                                    LOGGER.info("Write tag[{}] is {}.", t, writeResponse.getResponseCode(t))                        
                            );
                        } catch (Exception ex) {
                        LOGGER.info("Failure to create memory area {}.", ex.getMessage());
                        } 
                    } else {
                        LOGGER.info("Failure to create memory area for PlcItem {} and Tag {}.", plcItem.getItemName(), plcItem.getItemPlcTag());
                    }
                    
                } else {
                    LOGGER.info("Device is not connected..");                    
                }
            }
            return;
        } else if (dbRecord instanceof DBRecord){
            LOGGER.info("Creating an unsupported memory area with DBRecord.");
            return;
        };
        
    }

    @Override
    public Optional<PlcItem> getMemoryAreaPlcItem(String memoryArea, Integer index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void createScanGroup(Object dbRecord) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void addMemoryAreaListener(String memoryArea, Integer index, PlcItemListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void removeMemoryAreaListener(String memoryArea, Integer index, PlcItemListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Integer getMemoryAreaSegmentCount(String memoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<Integer> getMemoryAreaSegmentIds(String memoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<UUID> getModelPlcGroupUuids(String memoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<UUID> getModelPlcItemUuids(String memoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
