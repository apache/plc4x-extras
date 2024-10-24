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
package org.apache.plc4x.merlot.api.impl;

import com.lmax.disruptor.RingBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.time.Instant;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.model.PlcTag;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcList;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
*
*/
public class PlcItemImpl implements PlcItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlcItem.class);  
    
    private ReentrantLock lock = new ReentrantLock();    
    private String itemName;
    private String itemDescription;
    private String itemId;
    private UUID itemUid;
    
    private boolean itemEnable = false;
   
    private int itemAccessrigths = 0;
    
    private Boolean itemIsArray     = false;    
    private Boolean itemDisableOutput = false; 
    
    private final Hashtable<String, Object> itemProperties;    
    
    private PlcTag itemPlcTag = null;
    private PlcValue itemPlcValue = null;
   
    private LinkedList<PlcItemListener> itemClients = null;

    private byte[] itemInnerBuffer = null;   
    private ByteBuf itemBuffer = null;

        
    private long itemTransmit = 0;
    private long itemReceives = 0;    
    private long itemErrors = 0;

    private PlcReadResponse  plcResponse;   
    private PlcResponseCode plcDataquality;

    private Date lastReadDate;
    private Date lastWriteDate;
    private Date lastErrorDate;
    
    private RingBuffer<PlcDeviceWriteEvent> writeRingBuffer = null;

 
    public PlcItemImpl(PlcItemBuilder builder) {
        this.itemProperties = new Hashtable<>();        
        
        itemProperties.put(PlcItem.ITEM_NAME, builder.itemName);        
        itemName = builder.itemName;
        
        itemProperties.put(PlcItem.ITEM_DESCRIPTION, builder.itemDescription);         
        itemDescription = builder.itemDescription;
        
        itemProperties.put(PlcItem.ITEM_UID, builder.itemUid.toString());           
        itemUid = builder.itemUid;        
        
        itemProperties.put(PlcItem.ITEM_ID, builder.itemId);           
        itemId = builder.itemId;        

        
        itemEnable = builder.itemEnable;
        itemAccessrigths = builder.itemAccessrigths;
        
        itemIsArray = builder.itemIsArray;
        itemDisableOutput = builder.itemDisableOutput;
        
        itemBuffer = Unpooled.buffer();
        itemClients = new LinkedList<>();
    }     

    @Override
    public void enable() {
        itemEnable = true;
    }

    @Override
    public void disable() {
        itemEnable = false;
    }
               
    @Override
    public UUID getItemUid() {
        return UUID.fromString((String) itemProperties.get(PlcItem.ITEM_UID));
    }
       
    @Override
    public String getItemName() {
        return (String) itemProperties.get(PlcItem.ITEM_NAME);
    }

    @Override
    public void setItemName(String itemname) {
        itemProperties.put(PlcItem.ITEM_NAME, itemName);   
    }

    @Override
    public String getItemDescription() {
        return (String) itemProperties.get(PlcItem.ITEM_DESCRIPTION);
    }

    @Override
    public void setItemDescription(String itemDescription) {
        itemProperties.put(PlcItem.ITEM_DESCRIPTION, itemDescription);  
    }

    @Override
    public String getItemId() {
        return (String) itemProperties.get(PlcItem.ITEM_ID);
    }

    @Override
    public void setItemId(String itemId) {
        itemProperties.put(PlcItem.ITEM_ID, itemId);
    }

    @Override
    public PlcTag getItemPlcTag() {
        return itemPlcTag;
    }

    @Override
    public void setItemPlcTag(PlcTag itemplctag) {
        this.itemPlcTag = itemplctag;
    }
        
    @Override
    public Boolean isEnable() {
        return itemEnable;
    }

    @Override
    public void setEnable(Boolean enable) {
        this.itemEnable = enable;
                
    }    
                
    @Override
    public Boolean getIsArray() {
        return itemIsArray;
    }

    @Override
    public void setIsArray(Boolean isArray) {
        this.itemIsArray = isArray;
    }

    @Override
    public Boolean isDisableOutput() {
        return itemDisableOutput;
    }

    @Override
    public void setIsDisableOutput(Boolean itemDisableOutput) {
        this.itemDisableOutput = itemDisableOutput;
    }

    @Override
    public long getItemTransmits() {
        return itemTransmit;
    }

    @Override
    public long getItemReceives() {
        return itemReceives;
    }

    public long getItemErrors() {
        return itemErrors;
    }


    @Override
    public int getAccessRights() {
        return itemAccessrigths;
    }

    @Override
    public void setAccessRights(int itemAccessrigths) {
        this.itemAccessrigths = itemAccessrigths;
    }

    @Override
    public PlcResponseCode getDataQuality() {
        return plcDataquality;
    }

    @Override
    public void setPlcValue(PlcValue  plcvalue) {
        try {
            //Creates the default buffer associated with the requested data.
            if (null == itemInnerBuffer) {
                int size = (plcvalue instanceof PlcList) ? 
                        ((PlcList) plcvalue).getLength() * 
                        ((PlcList) plcvalue).getList().get(0).getRaw().length :
                        -1;
                itemInnerBuffer = (size == -1) ? new byte[plcvalue.getRaw().length] :
                                                new byte[size];
                itemBuffer = Unpooled.wrappedBuffer(itemInnerBuffer);
                //Update all clients
                itemClients.forEach(c -> c.atach(this));                
            }
            
            //Transfers data to a byte buffer
            itemBuffer.resetWriterIndex();        
            if (plcvalue instanceof PlcList) {
                ((PlcList) plcvalue).getList().forEach(v -> itemBuffer.writeBytes(v.getRaw()));
            } else {
                itemBuffer.writeBytes(plcvalue.getRaw());
            }
        } catch (Exception ex){
            itemErrors++;
            lastErrorDate = Date.from(Instant.now());
            LOGGER.error(ex.getMessage());
        }

        //Update stat data
        itemReceives++;
        lastReadDate = Date.from(Instant.now());
        
        //Update all clients
        itemClients.forEach(c -> c.update());

    }

    @Override
    public PlcValue getItemPlcValue() {
        lock.lock();
        PlcValue plcvalue;
        try {
            plcvalue = itemPlcValue;
        } finally {
            lock.unlock();
        }
        return plcvalue;
    }

    @Override
    public ByteBuf getItemByteBuf() {
//        lock.lock();
//        ByteBuf itembuffer;
//        try {
//            itembuffer = itemBuffer.duplicate();
//        } finally {
//            lock.unlock();
//        }        
        return itemBuffer;
    }

    @Override
    public byte[] getInnerBuffer() {          
        return itemInnerBuffer;
    }
                  
    @Override
    public void setDataQuality(PlcResponseCode plcDataquality) {
        this.plcDataquality = plcDataquality;
    }

    @Override
    public void addItemListener(PlcItemListener client) {
        if (!itemClients.contains(client)) {
            client.atach(this);
            itemClients.add(client);
        }
    }

    @Override
    public void removeItemListener(PlcItemListener client) {
        if (!itemClients.contains(client)) {
            client.detach();            
            itemClients.remove(client);            
        }        
    }
            
    @Override
    public Hashtable<String, Object> getProperties() {
        return itemProperties;
    }
               
    @Override
    public Date getLastReadDate() {
        return lastReadDate;
    }

    @Override
    public Date getLastWriteDate() {
        return lastWriteDate;
    }

    @Override
    public Date getLastErrorDate() {
        return lastErrorDate;
    }
    
    private void updateClients(){
        itemClients.forEach(c -> c.update());
    }

    @Override
    public void setRingBuffer(RingBuffer<PlcDeviceWriteEvent> ringBuffer) {
        this.writeRingBuffer = ringBuffer;
    }

    @Override
    public void itemWrite(final ByteBuf byteBuf, int byteOffset, byte bitOffset) {
        if (null == writeRingBuffer) {
            LOGGER.info("*** Rinbuffer es null ***");
            return;
        }
        long sequenceId = writeRingBuffer.next();
        final PlcDeviceWriteEvent writeEvent = writeRingBuffer.get(sequenceId); 
        writeEvent.setPlcItem(this);
        writeEvent.setByteBuf(byteBuf);
        writeEvent.setByteOffset(byteOffset);
        writeEvent.setBitOffset(bitOffset);        
        writeRingBuffer.publish(sequenceId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(100);
        sb.append("Name: ").append(itemName).append("\r\n").
            append("Description: ").append(itemDescription).append("\r\n").
            append("Id: ").append(itemId).append("\r\n"). 
            append("UID: ").append(itemUid).append("\r\n").
            append("Is enable: ").append(itemEnable).append("\r\n").
            append("Access rigths: ").append(itemAccessrigths).append("\r\n").                
            append("Disable output: ").append(itemDisableOutput).append("\r\n").
            append("Number of clients: ").append(itemClients.size()).append("\r\n").     
            append("Transmits: ").append(itemClients).append("\r\n").
            append("Last transmits date: ").append(lastWriteDate).append("\r\n").                
            append("Receives: ").append(itemClients).append("\r\n"). 
            append("Last receives date: ").append(lastReadDate).append("\r\n").                                
            append("Errors: ").append(itemClients).append("\r\n").
            append("Last error date: ").append(lastErrorDate).append("\r\n").                
            append("Data buffer: ").append("\r\n").                                
            append(ByteBufUtil.prettyHexDump(itemBuffer)).append("\r\n");                
        return sb.toString();
    }
    
    public static class PlcItemBuilder {
        private final String itemName;
        private  UUID itemUid;    
        private String itemDescription;
        private String itemId;
        private Boolean itemEnable        = false;   
        private int itemAccessrigths      = 0;    
        private Boolean itemIsArray       = false; 
        private Boolean itemDisableOutput = false;         

        public PlcItemBuilder(String itemName) {
            this.itemName = itemName;
            this.itemUid = UUID.randomUUID();
        }

        public PlcItemBuilder setItemUid(UUID itemUid) {
            this.itemUid = itemUid;
            return this;
        }

        public PlcItemBuilder setItemDescription(String itemDescription) {
            this.itemDescription = itemDescription;
            return this;            
        }

        public PlcItemBuilder setItemId(String itemId) {
            this.itemId = itemId;
            return this;            
        }

        public PlcItemBuilder setItemEnable(boolean itemEnable) {
            this.itemEnable = itemEnable;
            return this;            
        }

        public PlcItemBuilder setItemAccessrigths(int itemAccessrigths) {
            this.itemAccessrigths = itemAccessrigths;
            return this;            
        }

        public PlcItemBuilder setItemIsarray(Boolean itemIsArray) {
            this.itemIsArray = itemIsArray;
            return this;            
        }

        public PlcItemBuilder setItemDisableoutput(Boolean itemDisableOutput) {
            this.itemDisableOutput = itemDisableOutput;
            return this;            
        }

        public PlcItem build() {
            PlcItem plcitem = new PlcItemImpl(this);
            validatePlcItemObject(plcitem);
            return plcitem;
        }
        
        private void validatePlcItemObject(PlcItem plcitem) {
            //
        }            
        
        
    }        
    
}
