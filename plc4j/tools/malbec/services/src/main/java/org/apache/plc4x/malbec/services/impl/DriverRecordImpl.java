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
import org.apache.plc4x.java.api.PlcDriver;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.DriverRecord;
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;

@JsonPropertyOrder({ "protocolCode","protocolName","uuid","enable","devices"})
@JsonIgnoreProperties(value = { "plcdriver","transmits","receives","errors",
    "startInstant","currentInstant","lastUpdateInstant"})
public class DriverRecordImpl implements DriverRecord {
    
    private Plc4xPropertyEnum P;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);    
    private final Lookup lk;   
    private final InstanceContent ic;
    
    private String protocolCode;
    private String protocolName;
    private final UUID uuid;
    
    private Boolean enable = false;    
    
    private final PlcDriver plcdriver;
    
    private int transmits = 0;
    private int receives = 0;
    private int errors = 0;
    
    private Instant startInstant;
    private Instant currentInstant;
    private Instant lastUpdateInstant;

     
    public DriverRecordImpl() {
        this.lk = null;
        this.ic = null;
        this.uuid = null;        
        this.plcdriver = null;
    }    

    public DriverRecordImpl(UUID uuid, PlcDriver plcdriver) {
        ic = new InstanceContent ();
        lk = new AbstractLookup (ic);        
        this.uuid = uuid;
        this.plcdriver = plcdriver;
    }

    @Override
    public void setProtocolCode(String protocol) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public String getProtocolCode() {
        return plcdriver.getProtocolCode();
    }

    @Override
    public void setProtocolName(String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getProtocolName() {
        return plcdriver.getProtocolName();
    }

    @Override
    public void setUUID(UUID uuid) {
        //this.uuid = uuid;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }
    
    @JsonIgnore
    @Override
    public PlcDriver getPlcDriver() {
        return plcdriver;
    }    

    @Override
    public void setEnable(Boolean enable) {
        Boolean oldValue = this.enable;
        this.enable = enable; 
        this.pcs.firePropertyChange(P.ENABLE.name(), oldValue, enable);
        updateLastUpdateInstant();        
    }

    @Override
    public Boolean getEnable() {
        return enable;
    }

    @Override
    public void addDevice(DeviceRecord device) {
        ic.add(device);
        this.pcs.firePropertyChange(P.ADD_DEVICE.name(), null, device);
        updateLastUpdateInstant();
    }

    @Override
    public Optional<DeviceRecord> getDevice(DeviceRecord device) {
        Optional<DeviceRecord> opdevice = (Optional<DeviceRecord>) lk.lookupAll(DeviceRecord.class).stream().
                filter(d -> d.equals(device)).
                findFirst();
        
        return opdevice;
    }
   
    @Override
    public Optional<DeviceRecord> getDevice(UUID uuid) {
        Optional<DeviceRecord> opdevice = (Optional<DeviceRecord>) lk.lookupAll(DeviceRecord.class).stream().
                filter(d -> { System.out.println("> " + d.getUUID().toString());
                return d.getUUID().equals(uuid);}).
                findFirst();
        if ( opdevice.isPresent()) System.out.println("Driver presente");
        return opdevice;
    }

    @Override
    public Optional<DeviceRecord> getDevice(String name) {
        Optional<DeviceRecord> opdevice = (Optional<DeviceRecord>) lk.lookupAll(DeviceRecord.class).stream().
                filter(d -> d.getDeviceName().equals(name)).
                findFirst();
        return opdevice;
    }
  
    @Override
    public Collection<DeviceRecord> getDevices() {
        return (Collection<DeviceRecord>) lk.lookupAll(DeviceRecord.class);
    }     

    @Override
    public void removeDevice(DeviceRecord device) {
        ic.remove(device);
        this.pcs.firePropertyChange(P.REMOVE_DEVICE.name(), device, null);        
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

    @Override
    public int getTransmits() {
        return transmits;
    }

    @Override
    public int getReceives() {
        return receives;
    }

    @Override
    public int getErrors() {
        return errors;
    }

    @Override
    public int getNumberOfDevice() {
        return 0;
    }

    @Override
    public int getNumberOfTagGroups() {
        int[] n = new int[1];
        n[0] = 0;
       // devices.entrySet().stream()
       //         .forEach(drv -> n[0] += drv.getValue().getNumberOfTagGroups());
        return n[0];
    }

    @Override
    public int getNumberOfTags() {
        int[] n = new int[1];
        n[0] = 0;
        //devices.entrySet().stream()
         //       .forEach(drv -> n[0] += drv.getValue().getNumberOfTags());
        return n[0];
    }

    @Override
    public Instant getStartInstant() {
        return startInstant;
    }

    @Override
    public Instant getCurrentInstant() {
        return currentInstant;
    }

    @Override
    public void updateLastUpdateInstant() {
        lastUpdateInstant = Instant.now();
    }    
    
    @Override
    public Instant getLastUpdateInstant() {
        return lastUpdateInstant;
    }    

    @JsonIgnore
    @Override
    public Lookup getLookup() {
        return lk;
    }



    
}
