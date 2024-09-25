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
package org.apache.plc4x.malbec.services.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import org.openide.util.Lookup;
import org.apache.plc4x.malbec.api.TagRecord;
import org.apache.plc4x.malbec.api.TagGroupRecord;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

@JsonPropertyOrder({"groupName","groupDesc","uuid","deviceuuid","enable","tags"})
@JsonIgnoreProperties(value = {"startInstant","currentInstant","lastUpdateInstant"})
public class TagGroupRecordImpl implements TagGroupRecord, Lookup.Provider {

    private Plc4xPropertyEnum P;    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Lookup lk;   
    private final InstanceContent ic;     
    private String groupName;
    private String groupDesc;
    private UUID uuid;  
    private UUID deviceuuid;     
    
    private Boolean enable = false;  
    
    private int scanTime = 100;
    
    private Instant startInstant;
    private Instant currentInstant;
    private Instant lastUpdateInstant;    
 

    public TagGroupRecordImpl(UUID deviceuuid) {
        this.ic = new InstanceContent ();        
        this.lk = new AbstractLookup (ic);   
        this.deviceuuid = deviceuuid;
    }  
    
    @Override
    public void setTagGroupName(String name) {
        String oldValue = this.groupName;
        this.groupName = name;
        this.pcs.firePropertyChange(P.NAME.name(), oldValue, name);   
    }

    @Override
    public String getTagGroupName() {
        return groupName;
    }

    @Override
    public void setTagGroupDesc(String desc) {
        String oldValue = this.groupDesc;        
        this.groupDesc = desc;
        this.pcs.firePropertyChange(P.DESCRIPTION.name(), oldValue, desc);         
    }

    @Override
    public String getTagGroupDesc() {
        return groupDesc;
    }

    @Override
    public void setUUID(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public void setScanTime(int ms) {
        Integer oldValue = scanTime;
        if (ms < 100) scanTime=100;
        else  scanTime = ms;
        Integer newValue = scanTime;
        this.pcs.firePropertyChange(P.SCANTIME.name(), oldValue, newValue);                
    }

    @Override
    public int getScanTime() {
        return scanTime;
    }  

    @Override
    public void addTag(TagRecord tag) {
        ic.add(tag);
        this.pcs.firePropertyChange(P.ADD_TAG.name(), null, tag);          
        updateLastUpdateDateTime();       
    }


    
    @Override
    public Optional<TagRecord> getTag(UUID uuid) {
        return (Optional<TagRecord>) lk.lookupAll(TagRecord.class).stream().
                filter(d -> d.getUUID().equals(uuid)).
                findFirst();
    }

    @Override
    public Optional<TagRecord> getTag(TagRecord tag) {
        return (Optional<TagRecord>) lk.lookupAll(TagRecord.class).stream().
                filter(d -> d.getTagName().equals(tag.getTagName())).
                findFirst();
    }
    
    @Override
    public Optional<TagRecord> getTag(String name) {
        return (Optional<TagRecord>) lk.lookupAll(TagRecord.class).stream().
                filter(t -> t.getTagName().equals(name)).
                findFirst();
    }    

    @Override
    public Collection<TagRecord> getTags() {
        return  (Collection<TagRecord>) lk.lookupAll(TagRecord.class);
    }
    
    @Override
    public void removeTag(TagRecord tag) {
        ic.remove(tag);
        this.pcs.firePropertyChange(P.REMOVE_TAG.name(), tag, null);          
        updateLastUpdateDateTime();
    }

    @Override
    public void setEnable(Boolean enable) {
        Boolean oldValue = this.enable; 
        this.enable = enable;
        if (enable) startInstant = Instant.now();
        lastUpdateInstant = startInstant;
        this.pcs.firePropertyChange(P.ENABLE.name(), oldValue, enable); 
    }

    @Override
    public Boolean getEnable() {
        return enable;
    }
  
    
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
         this.pcs.removePropertyChangeListener(listener);
    }

    @Override
    public void setDeviceRecord(UUID deviceuuid) {
        this.deviceuuid = deviceuuid;
    }

    @Override
    public UUID getDeviceRecord() {
        return deviceuuid;
    }


    @JsonIgnore     
    @Override
    public int getJitter() {
        return 0;
    }
    
    @JsonIgnore 
    @Override
    public int getTransmits() {
        return 0;
    }

    @JsonIgnore     
    @Override
    public int getReceives() {
        return 0;
    }

    @JsonIgnore     
    @Override
    public int getErrors() {
        return 0;
    }

    @JsonIgnore     
    @Override
    public int getNumberOfTags() {
        return lk.lookupAll(TagRecord.class).size();
    }
    
    @JsonIgnore 
    @Override
    public Instant getStartInstant() {
        return startInstant;
    }
    
    @JsonIgnore 
    @Override
    public Instant getCurrentInstant() {
        return currentInstant; 
    }
    

    @Override
    public void updateLastUpdateDateTime() {
        lastUpdateInstant = Instant.now();
    }    
    
    @JsonIgnore 
    @Override
    public Instant getLastUpdateDateTime() {
        return lastUpdateInstant;
    }

    @JsonIgnore    
    @Override
    public Lookup getLookup() {
        return lk;
    }




    
}
