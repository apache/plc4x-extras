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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.plc4x.java.s7.readwrite.MemoryArea;
import org.apache.plc4x.java.s7.readwrite.TransportSize;
import static org.apache.plc4x.java.s7.readwrite.TransportSize.BYTE;
import org.apache.plc4x.java.s7.readwrite.tag.S7Tag;
import org.apache.plc4x.merlot.api.PlcDevice;
import org.apache.plc4x.merlot.api.PlcGeneralFunction;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.api.PlcModel;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
* S7PlcModelImpl is a representation of the memory areas associated 
* with a Siemens S7 PLC.
* 
*/
public class S7PlcModelImpl implements PlcModel {
    private static final Logger logger = LoggerFactory.getLogger(S7PlcModelImpl.class);  
         
    private BundleContext bc;
    
    private PlcGeneralFunction gf;
    
    private PlcDevice plcDevice = null;
    
    //Memory areas of the physical device
    private Map<String, Map<Integer,PlcItem>> memoryAreas;
    
    //Scan group for the device.
    private Map<UUID, PlcGroup> scanGroups = new HashMap<UUID, PlcGroup>();
    
    //Individual Items for a memory area.
    private Map<PlcItem, List<Pair<PlcGroup,PlcItem>>> scanItems = new HashMap<PlcItem, List<Pair<PlcGroup,PlcItem>>>();    
    
    public S7PlcModelImpl(BundleContext bc, PlcGeneralFunction gf) {
        this.bc = bc;
        this.gf = gf;
                
        memoryAreas = new HashMap<String, Map<Integer,PlcItem>> ();
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
    public Set<String> listMemoryAreas() {
        return memoryAreas.keySet();
    }
       


    @Override
    public Integer getMemoryAreaId(String strMemoryArea) {
        return Integer.valueOf(MemoryArea.firstEnumForFieldShortName((strMemoryArea)).getValue());
    }

    @Override
    public void createMemoryArea(Object dbRecord) {
        final DBRecord dbrecord = (DBRecord) dbRecord;
        final PVStructure pvStructure = dbrecord.getPVStructure();        
        final String pvId = pvStructure.getStringField("id").get();
               
        // Each tag is comprised of two fields separated by ":", the first field
        // corresponds to the driver instance identifier and the second is the 
        // string that represents the memory area, the tag itselft.

        String[] strTemp = pvId.split(":", 2);
        String strTag = strTemp[1];
        
        //TODO: Split the Device name.
        S7Tag s7tag = S7Tag.of(strTag);
        
        if (null == memoryAreas.get(s7tag.getMemoryArea().getShortName())) {
            Map<Integer, PlcItem> inputBytes = new HashMap<Integer, PlcItem>();
            memoryAreas.put(s7tag.getMemoryArea().getShortName(), inputBytes);
            logger.info("Created memmory area with PlcItem: " + "s7" + s7tag.getMemoryArea().getShortName() + "["+s7tag.getBlockNumber() +"]");
        }

        final Map<Integer, PlcItem> memoryBytes = memoryAreas.get(s7tag.getMemoryArea().getShortName());          
        
        if (null == memoryBytes.get(s7tag.getBlockNumber())) {
            PlcItem plcItem = new PlcItemImpl.PlcItemBuilder("s7" + s7tag.getMemoryArea().getShortName() + "["+s7tag.getBlockNumber() +"]").
                setItemDescription("Flag markes from PLC in byte order.").
                setItemId("").
                setItemEnable(true).
                build();
            memoryBytes.put(s7tag.getBlockNumber(), plcItem);
        }
        
        final PlcItem internalPlcItem = memoryBytes.get(s7tag.getBlockNumber());        
        final ByteBuf byteBuf = internalPlcItem.getItemByteBuf();
        int bufferSize = (dbrecord.getInnerBuffer().isPresent())?dbrecord.getInnerBuffer().get().capacity():1;
        int minSize =   s7tag.getByteOffset() + bufferSize;

        if (byteBuf.capacity() < minSize) {
            byteBuf.capacity(minSize);
            logger.info("The buffer capacity was expanded to {}.", minSize);
        }                   
    }

    @Override
    public void createScanGroup(Object dbRecord) {
        final DBRecord dbrecord = (DBRecord) dbRecord;        
        final PVStructure pvStructure = dbrecord.getPVStructure();
        final PVString pvId = pvStructure.getStringField("id"); 
        final String pvScanTime  = pvStructure.getStringField("scan_time").get();
        long longScanTime = Long.parseLong(pvScanTime);
        long scan_time = ( longScanTime < 100)  ? 100 : longScanTime;
        
        if (null == plcDevice) {
            String[] strTemp = pvId.get().split(":", 2);
            String strDevices = strTemp[0];
            Optional<PlcDevice> optPlcDevice =  gf.getPlcDevice(strDevices);
            if (optPlcDevice.isPresent())
                plcDevice = optPlcDevice.get();
        }
        
        if (null != plcDevice) {
            Optional<Entry<UUID, PlcGroup>> optEntry = scanGroups.
                    entrySet().
                    stream().
                    filter(g -> g.getValue().getPeriod() == scan_time).
                    findFirst();
            if (optEntry.isEmpty()) {
                UUID uuid = UUID.randomUUID();
                Optional<PlcGroup> optPlcGroup = gf.createGroup(uuid.toString(), 
                        plcDevice.getUid().toString(), 
                        Long.toString(System.currentTimeMillis()), 
                        "S7 Model group " + scanGroups.size(), 
                        pvScanTime, 
                        "true");
                if (optPlcGroup.isPresent()) {
                    scanGroups.put(uuid, optPlcGroup.get());
                    System.out.println("Grupo creado!!!");
                } else {
                    logger.info("Scan group was not created for device {} and time {}", plcDevice.getDeviceName(), pvScanTime);
                }
            }
        } else {
            logger.info("Scan group was not created for DBRecord {}.");
            System.out.println("No encontro dispositvo!!!");            
        }
    }
        
    @Override
    public Integer getMemoryAreaSegmentCount(String strMemoryArea) {
        return memoryAreas.get(strMemoryArea).keySet().size();
    }

    @Override
    public Set<Integer> getMemoryAreaSegmentIds(String strMemoryArea) {
        return memoryAreas.get(strMemoryArea).keySet();
    }

    @Override
    public Optional<PlcItem> getMemoryAreaPlcItem(String memoryArea, Integer index) {
        if ((memoryAreas.containsKey(memoryArea)) &&
            (memoryAreas.get(memoryArea).containsKey(index))){
            return Optional.of(memoryAreas.get(memoryArea).get(index));
        }
        return Optional.empty();
    }
        
    @Override
    public void addMemoryAreaListener(String strMemmoryArea, Integer index, PlcItemListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void removeMemoryAreaListener(String strMemmoryArea, Integer index, PlcItemListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
                
    @Override
    public Set<UUID> getModelPlcGroupUuids(String strMemoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<UUID> getModelPlcItemUuids(String strMemoryArea) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
   
    /*
    *  
    */
    private void doUpdateByteBuf(DBRecord dbRecord){
        PlcItem tempPlcItem = null;
        final DBRecord dbrecord = (DBRecord) dbRecord;
        final PVStructure pvStructure = dbrecord.getPVStructure();        
        final String pvId = pvStructure.getStringField("id").get();
        final String pvScanTime  = pvStructure.getStringField("scan_time").get();
        long longScanTime = Long.parseLong(pvScanTime);
        long scan_time = ( longScanTime < 100)  ? 100 : longScanTime;
        
        // Each tag is comprised of two fields separated by ":", the first field
        // corresponds to the driver instance identifier and the second is the 
        // string that represents the memory area, the tag itselft.
        
        String[] strTemp = pvId.split(":", 2);
        String strTag = strTemp[1];
        
        //TODO: Split the Device name.
        S7Tag s7tag = S7Tag.of(strTag); 
        
        final Map<Integer, PlcItem> memoryItems = memoryAreas.get(s7tag.getMemoryArea().getShortName()); //(01)       
        final PlcItem internalPlcItem = memoryItems.get(s7tag.getBlockNumber()); //(02)        
        final ByteBuf byteBuf = internalPlcItem.getItemByteBuf();  
        
        dbRecord.atach(internalPlcItem);
        
        //1. Chequea si el direccionamiento esta dentro de uno de los items
        //   si: 1.1 Verifica si esta dentro de todo el segmento.
        //       1.2 si sobre sale del segmento, llega a una distancia mínima
        //           del sisguiente Item.
        //           si: 1.2.1 recalcula el Item para que cubra ambas area.
        //                     trasiega los clientes al nuevo Item.
        //           no: Incrementa la solicitud del Item a las distancia necesaria.
        //   no: Crea el item con el tamaño necesario, y realiza la suscripción.
        
        //2. El attach se realiza al internalPlcItem
        //3. El Listener se realiza al nuevo PlcIem
        
        //Take the list of items associated with a memory area
        var plcItems = scanItems.get(internalPlcItem);
        
        //Create the first PlcItem in this memory area
        if (null == plcItems) { //(03)
            
            scanItems.put(internalPlcItem, new ArrayList<Pair<PlcGroup,PlcItem>>());            
            //Create a new PlcItem associated with the memory area
            PlcItem scanPlcItem = new PlcItemImpl.PlcItemBuilder(UUID.randomUUID().toString()).
                setItemDescription("Flag markes from PLC in byte order.").
                setItemId("").
                setItemEnable(false).
                build(); 
            
            //Assigns the request tag in Bytes.
            
            S7Tag s7PlcTag = null;
            s7PlcTag = new S7Tag(TransportSize.USINT,
                                s7tag.getMemoryArea(),
                                s7tag.getBlockNumber(),
                                s7tag.getByteOffset(),
                                (byte) 0,
                                dbRecord.getInnerBuffer().get().writableBytes());   
         
            scanPlcItem.setItemPlcTag(s7PlcTag);
            
            scanPlcItem.setItemByteBuf(
                byteBuf.slice(s7tag.getByteOffset(), s7tag.getNumberOfElements())
            );   
            
            Optional<Entry<UUID, PlcGroup>> optGroup = scanGroups.
                    entrySet().
                    stream().
                    filter(g -> g.getValue().getPeriod() == scan_time).
                    findFirst();      

            if (optGroup.isPresent()) {                
                scanPlcItem.addItemListener(dbrecord);
                scanPlcItem.setEnable(true); 
                scanItems.get(internalPlcItem).add(new MutablePair<>(optGroup.get().getValue(), scanPlcItem));                
                optGroup.get().getValue().putItem(scanPlcItem);                                
            } else {
                logger.info("Scan group no present {}.", scan_time);
            }
        } else {
                
            Optional<PlcItem> optPlcItem = scanItems.keySet().
                    stream().
                    filter(i -> {
                        final S7Tag itemTag = (S7Tag) i.getItemPlcTag();
                        int x1 = itemTag.getByteOffset();
                        int x2 =  itemTag.getByteOffset() + s7tag.getByteOffset();

                        return ((s7tag.getByteOffset() >= x1) && (s7tag.getByteOffset() + s7tag.getNumberOfElements() <= x2));
                    }).
                    findFirst();

            if (!optPlcItem.isPresent()){
                List<PlcItem> rangeItems = scanItems.keySet().
                        stream().
                        filter(i -> {
                            final S7Tag itemTag = (S7Tag) i.getItemPlcTag();
                            int x1 = itemTag.getByteOffset();
                            return (s7tag.getByteOffset() >= x1);
                        }).
                        toList();

                int distance = Integer.MAX_VALUE;

                while(rangeItems.listIterator().hasNext()) {
                    final PlcItem i = rangeItems.listIterator().next();
                    final S7Tag itemTag = (S7Tag) i.getItemPlcTag();                
                    if (Math.abs(s7tag.getByteOffset() - itemTag.getByteOffset()) < distance) {
                        distance = Math.abs(s7tag.getByteOffset() - itemTag.getByteOffset());
                        tempPlcItem = i;
                    }                
                }

            } else {
                tempPlcItem = optPlcItem.get();
            }
        }
        
      
        
    }
  
}
