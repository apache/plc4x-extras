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
package org.apache.plc4x.merlot.api.impl;

import com.lmax.disruptor.RingBuffer;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.model.PlcTag;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.apache.plc4x.merlot.scheduler.api.JobContext;
import org.osgi.framework.BundleContext;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.core.PlcItemClientService;
import org.osgi.framework.ServiceReference;
import org.slf4j.LoggerFactory;

/*
*
*/
public class PlcGroupImpl implements PlcGroup, Job {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(PlcGroupImpl.class);
    private static final String FILTER_ITEM =  "(&(" + org.osgi.framework.Constants.OBJECTCLASS + "=" + PlcItem.class.getName() + ")" +
                    "(" + PlcItem.ITEM_UID + "=*))";     
    
    protected final BundleContext bc;    
    
    private UUID groupUid;
        
    private boolean enable = false;
    private boolean isFirtsRun = true;
       
    private long groupTransmit = 0;
    private long groupReceives = 0;    
    private long groupErrors = 0;  
    
    private long groupItemsLength = 0;
    private long groupUpdateRate = -1;
     
    private final Hashtable<String, Object> groupProperties;
    
    private final Map<UUID, PlcItem> groupItems;
    
    private RingBuffer<PlcDeviceReadEvent>  readRingBuffer;
    private RingBuffer<PlcDeviceWriteEvent> writeRingBuffer;
    
    private PlcItemClientService groupItemsService = null;
    
    private AtomicReference<PlcConnection> refPlcConnection = null;
    private PlcReadRequest.Builder builder = null;     

    private StopWatch watch = new StopWatch();
    private long[] aux = new long[1];
    
    

    public PlcGroupImpl(PlcGroupBuilder builder) { 
        this.bc = builder.bc;
        this.groupItemsService = builder.groupItemsService;
        this.groupItems = new Hashtable<>();
        this.groupProperties = new Hashtable<>();

        groupProperties.put(PlcGroup.GROUP_UID, builder.groupUid.toString());  
        groupProperties.put(PlcGroup.GROUP_NAME, builder.groupName);
        if (null != builder.groupDeviceUid)
        groupProperties.put(PlcGroup.GROUP_DEVICE_UID, builder.groupDeviceUid);        

        groupProperties.put(PlcGroup.GROUP_DESCRIPTION, builder.groupDescription);              
        groupProperties.put(PlcGroup.GROUP_CONCURRENT, false);        
        groupProperties.put(PlcGroup.GROUP_IMMEDIATE, true);
        groupProperties.put(PlcGroup.GROUP_PERIOD, builder.groupPeriod);
        
    }
        
    public void start(int bc){
        //
        if (null != refPlcConnection) {
            if (refPlcConnection.get().isConnected()) {
                enable = true;
            } else {
                enable = false;
            }
        } else {
            LOGGER.info("The PlcConnection has not been assigned to the group.");
            enable = false;
        }
    }
    
    public void stop(int bc){
        enable = false;
    }

    @Override
    public void enable() {
        enable = true;
    }

    @Override
    public void disable() {
        enable = false;
    }
    
    
    @Override
    public UUID getGroupUid() {
        return  UUID.fromString((String) groupProperties.get(PlcGroup.GROUP_UID));
    }    

    @Override
    public UUID getGroupDeviceUid() {
        return  (UUID) groupProperties.get(PlcGroup.GROUP_DEVICE_UID);
    }

    @Override
    public void setGroupDeviceUid(UUID groupDeviceUid) {
        groupProperties.put(PlcGroup.GROUP_DEVICE_UID, groupDeviceUid);  
    }

    @Override
    public String getGroupName() {
        return (String) groupProperties.get(PlcGroup.GROUP_NAME);
    }

    @Override
    public void setGroupName(String groupname) {
        groupProperties.put(PlcGroup.GROUP_NAME, groupname);
    }

    @Override
    public String getGroupDescription() {
        return (String) groupProperties.get(PlcGroup.GROUP_DESCRIPTION); 
    }

    @Override
    public void setGroupDescription(String groupdescription) {
        groupProperties.put(PlcGroup.GROUP_DESCRIPTION, groupdescription);
    }

    @Override
    public boolean isEnable() {
        return enable; 
    }

    @Override
    public long getPeriod() {
        return (long) groupProperties.get(PlcGroup.GROUP_PERIOD); 
    }

    @Override
    public void setPeriod(long period) {
        groupProperties.put(PlcGroup.GROUP_PERIOD, (period < 100)?100:period);        
    }

    @Override
    public long getGroupTransmit() {  
        aux[0] = 0;
        groupItems.forEach((groupUid, item) -> {aux[0] = +item.getItemTransmits();});
        return aux[0];
    }

    @Override
    public long getGroupReceives() {
        aux[0] = 0;
        groupItems.forEach((groupUid, item) -> {aux[0] = +item.getItemReceives();});
        return aux[0];
    }

    @Override
    public long getGroupErrors() {
        aux[0] = 0;
        groupItems.forEach((groupUid, item) -> {aux[0] = +item.getItemErrors();});
        return aux[0];
    }

    //TODO: Check the interface
    @Override
    public void setPlcConnection(AtomicReference<PlcConnection> refPlcConnection) {
        LOGGER.info("Grupo [{}] Volatile: Se asigno la conexión.", groupProperties.get(PlcGroup.GROUP_NAME));
        this.refPlcConnection = refPlcConnection;
    }
    
    @Override
    public Map<UUID, PlcItem> getGroupItems() {
        return groupItems; 
    }

    @Override
    public void setGroupItems(long groupItems) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public Hashtable<String, Object> getProperties() {
        return groupProperties;
    }
           
    @Override
    public void putItem(PlcItem plcItem) {
        if (!groupItems.containsKey(plcItem.getItemUid())) {
            plcItem.setRingBuffer(writeRingBuffer);
            groupItems.put(plcItem.getItemUid(), plcItem);                

            //bc.registerService(PlcItem.class.getName(), item, item.getProperties());            
        }                   
    }

    @Override
    public PlcItem getItem(UUID itemgroupUid) {
        return groupItems.get(itemgroupUid);
    }

    //TODO: remove from context
    @Override
    public void removeItem(UUID itemgroupUid) {
        String filter = FILTER_ITEM.replace("*", itemgroupUid.toString());
        ServiceReference<?> sr = bc.getServiceReference(filter);
        bc.ungetService(sr);        
        groupItems.remove(itemgroupUid);
    }

    @Override
    public List<PlcItem> getItems() {
        return groupItems.values().stream().
                collect(Collectors.toList());
    }

    @Override
    public void execute(JobContext context) {
        if (enable) {
            if ((null != refPlcConnection) && (null != refPlcConnection.get())) {
                if (refPlcConnection.get().isConnected()) {
                    if (isFirtsRun) {
                        groupItems.forEach((u,i) ->{
                            Optional<PlcTag> refPlcTag = refPlcConnection.get().parseTagAddress(i.getItemId());
                            if (refPlcTag.isPresent()) {
                                i.setItemPlcTag(refPlcTag.get());
                            } else {
                                i.disable();
                            }
                        });
                        isFirtsRun = false;
                    }
                    //executeReadAllItems();
                    long sequenceId = readRingBuffer.next();
                    final PlcDeviceReadEvent readEvent = readRingBuffer.get(sequenceId);
                    readEvent.setPlcGroup(this);
                    readRingBuffer.publish(sequenceId);
                } else {
                    LOGGER.info("The driver is disconnected.");
                }
            } else {
                LOGGER.info("Unassigned or null PlcConnection connection.");
            }
        } else {
            LOGGER.info("The group {}:{} is disable.", 
                    ((String) groupProperties.get(PlcGroup.GROUP_NAME)),
                    ((String) groupProperties.get(PlcGroup.GROUP_UID)));
        }
    }

    @Override
    public void setReadRingBuffer(RingBuffer<PlcDeviceReadEvent> readRingBuffer) {
        this.readRingBuffer = readRingBuffer;
    }

    @Override
    public void setWriteRingBuffer(RingBuffer<PlcDeviceWriteEvent> writeRingBuffer) {
        this.writeRingBuffer = writeRingBuffer;
    }
    
    
    
    /*
    * Execute the read function for all items
    * 
    */
    public void executeReadAllItems() {
        //1. The item was 
        builder = refPlcConnection.get().readRequestBuilder();
        groupItems.forEach((u,i) ->{
            if (i.isEnable()) {
                builder.addTagAddress(u.toString(), i.getItemId());
            }
        }); 
        final PlcReadRequest readRequest = builder.build();        
        try {        
            PlcReadResponse syncResponse = readRequest.execute().get();
            groupItems.forEach((u,i) -> {
                final PlcValue plcvalue = syncResponse.getPlcValue(u.toString());
                if (null == plcvalue) LOGGER.info("Valor nulo");
                groupItemsService.putItemEvent(u, plcvalue);                
            });

        } catch (Exception ex) {
            LOGGER.info(ex.getMessage());
        }
    }

    public static class PlcGroupBuilder {
        protected final BundleContext bc;        
        private final String groupName;
        private String groupDescription; 
        private UUID groupUid;
        private UUID groupDeviceUid = null;        
        private boolean group_enable = false;
        private long groupPeriod = 100;
        private PlcItemClientService groupItemsService = null;
        
        public PlcGroupBuilder(BundleContext bc, String groupName, UUID group_groupUid) {
            this.bc = bc;
            this.groupName = groupName;
            this.groupUid = group_groupUid; 
            this.groupDescription = "";
        }
        
        public PlcGroupBuilder(BundleContext bc, String groupName) {
            this.bc = bc;
            this.groupName = groupName;
            this.groupUid = UUID.randomUUID();
            this.groupDescription = "";
        }        

        public PlcGroupBuilder  setGroupDescription(String groupDescription) {
            this.groupDescription = groupDescription;            
            return this;
        }

        public PlcGroupBuilder  setGroupUid(UUID group_groupUid) {
            this.groupUid = group_groupUid;
            return this;
        }
        
        public PlcGroupBuilder  setGroupDeviceUid(UUID groupDeviceUid) {
            this.groupDeviceUid = groupDeviceUid;
            return this;
        }        

        public PlcGroupBuilder  setGroupEnable(boolean group_enable) {
            this.group_enable = group_enable;
            return this;
        }

        public PlcGroupBuilder  setGroupPeriod(long groupPeriod) {
            this.groupPeriod = groupPeriod;
            return this;
        }
        
        public PlcGroupBuilder  setItemService(PlcItemClientService groupItemsService) {
            this.groupItemsService = groupItemsService;
            return this;
        }        
        
        public PlcGroup build() {
            PlcGroup plcgroup = new PlcGroupImpl(this);
            validatePlcGroupObject(plcgroup);
            return plcgroup;
        }
        
        private void validatePlcGroupObject(PlcGroup plcgroup) {
            //
        }        
        
        
        
    }    
    
}
