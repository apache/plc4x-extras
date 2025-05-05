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
package org.apache.plc4x.merlot.drv.s7.impl;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.plc4x.java.api.model.PlcTag;
import org.apache.plc4x.java.s7.readwrite.MemoryArea;
import org.apache.plc4x.java.s7.readwrite.tag.S7Tag;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.api.PlcModel;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cgarcia
 */
public class S7PlcModelImpl implements PlcModel{
    private static final Logger logger = LoggerFactory.getLogger(S7PlcModelImpl.class);  
    
    private Map<String, Map<Integer,PlcItem>> memoryAreas;

    public S7PlcModelImpl() {
        memoryAreas = new HashMap();
        memoryAreas.put(MemoryArea.COUNTERS.getShortName(), null);
        memoryAreas.put(MemoryArea.TIMERS.getShortName(), null);
        memoryAreas.put(MemoryArea.DIRECT_PERIPHERAL_ACCESS.getShortName(), null);
        memoryAreas.put(MemoryArea.INPUTS.getShortName(), null);
        memoryAreas.put(MemoryArea.OUTPUTS.getShortName(), null);
        memoryAreas.put(MemoryArea.FLAGS_MARKERS.getShortName(), null);
        memoryAreas.put(MemoryArea.DATA_BLOCKS.getShortName(), null);
        memoryAreas.put(MemoryArea.INSTANCE_DATA_BLOCKS.getShortName(), null);
        memoryAreas.put(MemoryArea.LOCAL_DATA.getShortName(), null);
    }
       
    @Override
    public Set<String> ListMemoryAreas() {
        return memoryAreas.keySet();
    }

    @Override
    public Integer MemoryAreaId(String strMemoryArea) {
        return Integer.valueOf(MemoryArea.firstEnumForFieldShortName((strMemoryArea)).getValue());
    }

    @Override
    public void CreateMemoryArea(PlcTag tag) {

        final S7Tag s7tag = (S7Tag) tag;
        checkByteBufInstance(s7tag);
        
//        switch(s7tag.getMemoryArea().getValue()) {
//            case 0x1C: {
//                    if (null == memoryAreas.get("C")) createCounterArea(s7tag);
//                    final Map<Integer, PlcItem> CBytes = memoryAreas.get("C");                     
//                };
//                break;
//            case 0x1D: {
//                    if (null == memoryAreas.get("T")) createTimerArea(s7tag);
//                    final Map<Integer, PlcItem> TBytes = memoryAreas.get("T");                     
//                 };
//                break;
//            case 0x80: {
//                    if (null == memoryAreas.get("D")) createDirectPeripheralAccessArea(s7tag);
//                    final Map<Integer, PlcItem> DBytes = memoryAreas.get("D");                     
//                };
//                break;
//            case 0x81:{                    
//                    if (null == memoryAreas.get("I")) createInputArea(s7tag);
//                    final Map<Integer, PlcItem> inputBytes = memoryAreas.get("I");                                        
//                };
//                break;
//            case 0x82: {
//                    if (null == memoryAreas.get("Q")) createOutputArea(s7tag);
//                    final Map<Integer, PlcItem> QBytes = memoryAreas.get("Q");                 
//                };
//                break;
//            case 0x83: {
//                    if (null == memoryAreas.get("M")) createFlagMarkerArea(s7tag);
//                    final Map<Integer, PlcItem> MBytes = memoryAreas.get("M");                   
//                };
//                break;   
//            case 0x84: {
//                    if (null == memoryAreas.get("DB")) createDataBlocksArea(s7tag);
//                    final Map<Integer, PlcItem> DBBytes = memoryAreas.get("DB");
//                    
//                };
//                break;
//            case 0x85: {
//                    if (null == memoryAreas.get("DBI")) createDataBlocksInstanceArea(s7tag);
//                    final Map<Integer, PlcItem> DBIBytes = memoryAreas.get("DBI");                 
//                };
//                break;
//            case 0x86: {
//                    if (null == memoryAreas.get("LD")) createFlagMarkerArea(s7tag);
//                    final Map<Integer, PlcItem> LDBytes = memoryAreas.get("LD");                 
//                };
//                break;                  
//                
//        }
    }



    @Override
    public Integer MemoryAreaSegment(String strMemoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<Integer> MemoryAreaSegmentId(String strMemoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<UUID> ModelPlcGroupsUuid(String strMemoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<UUID> ModelPlcItemsUuid(String strMemoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
    private void checkByteBufInstance(S7Tag tag) {
        if (null == memoryAreas.get(tag.getMemoryArea().getShortName())) {
            PlcItem internalPlcItem = new PlcItemImpl.PlcItemBuilder("s7" + tag.getMemoryArea().getShortName() + "["+tag.getBlockNumber() +"]").
                    setItemDescription("Flag markes from PLC in byte order.").
                    setItemId("").
                    setItemEnable(true).
                    build();
            Map<Integer, PlcItem> inputBytes = new HashMap();
            inputBytes.put(tag.getBlockNumber(), internalPlcItem);
            memoryAreas.put(tag.getMemoryArea().getShortName(), inputBytes);
            logger.info("Creted memmory area with PlcItem: " + "s7" + tag.getMemoryArea().getShortName() + "["+tag.getBlockNumber() +"]");
        }
        
        final Map<Integer, PlcItem> memoryBytes = memoryAreas.get(tag.getMemoryArea().getShortName());          
        
        if (null == memoryBytes.get(tag.getBlockNumber())) {
            PlcItem plcItem = new PlcItemImpl.PlcItemBuilder("s7" + tag.getMemoryArea().getShortName() + "["+tag.getBlockNumber() +"]").
                setItemDescription("Flag markes from PLC in byte order.").
                setItemId("").
                setItemEnable(true).
                build();
            memoryBytes.put(tag.getBlockNumber(), plcItem);
        }
        
        final PlcItem internalPlcItem = memoryBytes.get(tag.getBlockNumber());        
        final ByteBuf byteBuf = internalPlcItem.getItemByteBuf();
        
        int minSize =   tag.getByteOffset() + 
                        tag.getDataType().getSizeInBytes() * tag.getNumberOfElements(); 
        
        if (byteBuf.capacity() < minSize) {
            if (byteBuf.ensureWritable(minSize, true) != 0) {
                logger.info("The buffer capacity was expanded to {}.", minSize);
            }
        }        
        
    }
    
}
