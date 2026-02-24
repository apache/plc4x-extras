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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.openide.util.Lookup;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import org.apache.plc4x.malbec.api.TagGroupRecord;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

@JsonPropertyOrder({ "deviceName","deviceDesc","protocolCode","uuid","treenode",
    "enable","properties","tagGroups"})
public class DeviceRecordImpl implements DeviceRecord, Lookup.Provider {


    private Plc4xPropertyEnum P;
    private Plc4xEventEnum E;    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Lookup lk;   
    private final InstanceContent ic;    
   
    private String deviceName;
    private String deviceDesc;
    private UUID protocolCode;    
    private UUID uuid; 
    private UUID treenode;        
    
    private Boolean enable = false;   
    
    private int transmits = 0;
    private int receives = 0;
    private int errors = 0;    
    
    private Instant startInstant;
    private Instant currentInstant;
    private Instant lastUpdateInstant;    
    
    private Map<String, String> properties = new HashMap<String, String>();
    
    

    public DeviceRecordImpl() {
        this.ic = new InstanceContent ();        
        this.lk = new AbstractLookup (ic);         
    }
    
    public DeviceRecordImpl(UUID uuid) {
        this.ic = new InstanceContent ();        
        this.lk = new AbstractLookup (ic); 
        this.uuid = uuid;
    }


    @Override
    public void setDeviceName(String name) {
        String oldValue = this.deviceName;
        this.deviceName = name;
        this.pcs.firePropertyChange(P.NAME.name(), oldValue, name);        
    }

    @Override
    public String getDeviceName() {
        return deviceName;
    }
    
    @Override
    public void setDeviceDescription(String desc) {
        String oldValue = this.deviceDesc; 
        this.deviceDesc = desc;
        this.pcs.firePropertyChange(P.DESCRIPTION.name(), oldValue, desc); 
    }

    @Override
    public String getDeviceDescription() {
        return deviceDesc;
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
    public void setProtocolCode(UUID protocol) {
        this.protocolCode = protocol;
    }

    @Override
    public UUID getProtocolCode() {
        return protocolCode;
    }  
    
    @Override    
    public void setTreeLocation(UUID treenode) {
        this.treenode = treenode;
    }   
    
    @Override    
    public UUID getTreeLocation() {
        return treenode;
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
    public void setPropertie(String id, String str) {
        properties.put(id, str);
    }    

    @Override
    public String getPropertie(String id) {
        return properties.get(id);
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public void addTagGroup(TagGroupRecord tagg) {
        ic.add(tagg);
        this.pcs.firePropertyChange(Plc4xEventEnum.ADD_TAGGROUP.name(), null, tagg);         
        updateLastUpdateInstant();
    }

    @Override
    public Optional<TagGroupRecord> getTagGroup(TagGroupRecord tagg) {
        Optional<TagGroupRecord> optagg = (Optional<TagGroupRecord>) lk.lookupAll(TagGroupRecord.class).stream().
                filter(t -> t.equals(tagg)).
                findFirst();      
        return optagg;
    }

    @Override
    public Optional<TagGroupRecord> getTagGroup(UUID uuid) {
        Optional<TagGroupRecord> optagg = (Optional<TagGroupRecord>) lk.lookupAll(TagGroupRecord.class).stream().
                filter(t -> t.getUUID().equals(uuid)).
                findFirst();      
        return optagg;
    }

    @Override
    public Optional<TagGroupRecord> getTagGroup(String name) {
        Optional<TagGroupRecord> optagg = (Optional<TagGroupRecord>) lk.lookupAll(TagGroupRecord.class).stream().
                filter(t -> t.getTagGroupName().equals(name)).
                findFirst();      
        return optagg;
    }

    @Override
    public Collection<TagGroupRecord> getTagGroups() {
        return (Collection<TagGroupRecord>) lk.lookupAll(TagGroupRecord.class);
    }    
    
    @Override
    public void removeTagGroup(TagGroupRecord tagg) {
        ic.remove(tagg);
        this.pcs.firePropertyChange(Plc4xEventEnum.REMOVE_TAGGROUP.name(), tagg, null);         
        updateLastUpdateInstant();
    }
   
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
         this.pcs.removePropertyChangeListener(listener);
    }    
           
    @JsonIgnore
    @Override
    public int getTransmits() {
        return transmits;
    }

    @JsonIgnore    
    @Override
    public int getReceives() {
        return receives;
    }

    @JsonIgnore    
    @Override
    public int getErrors() {
        return errors;
    }

    @JsonIgnore    
    @Override
    public int getNumberOfTagGroups() {
       // return tagg.size();
       return 12;
    }

    @JsonIgnore
    @Override
    public int getNumberOfTags() {
        int[] ntags = new int[1];
       // tagg.entrySet().stream()
       //     .forEach(item -> ntags[0] += item.getValue().getNumberOfTags());
        return ntags[0];
    }

    @JsonIgnore
    @Override
    public Instant getStartInstant() {
        return startInstant;
    }

    @JsonIgnore
    @Override
    public Instant getCurrentInstant() {
        return Instant.now();
    }

    @Override
    public void updateLastUpdateInstant() {
        lastUpdateInstant = Instant.now();
    }    
    
    @JsonIgnore
    @Override
    public Instant getLastUpdateInstant() {
        return lastUpdateInstant;
    }

    @JsonIgnore
    @Override
    public Lookup getLookup() {
        return lk;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DeviceRecord) {
            return (uuid.equals(((DeviceRecord) obj).getUUID())) ? true:false;
        } else return false;
    }
    
    
    
}
