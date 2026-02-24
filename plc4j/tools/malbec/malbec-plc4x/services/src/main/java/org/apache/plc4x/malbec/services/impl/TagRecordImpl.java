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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.UUID;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import org.apache.plc4x.malbec.api.TagRecord;


/**
 *
 * @author cgarcia
 */
public class TagRecordImpl implements TagRecord {

    private Plc4xPropertyEnum P;    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);    
    private String tagname;
    private String tagdesc;
    private String id;
    
    private UUID uuid;
    private UUID tagguuid;

    private Boolean enable = false;    
    private Boolean disableOutput = true;   
    
    private int transmits = 0;
    private int receives = 0;
    private int errors = 0;  

    private Instant startInstant;
    private Instant currentInstant;
    private Instant lastUpdateInstant; 
    private Instant lastReadInstant;    
    private Instant lastWriteInstant;    
    private Instant lastErrorInstant;

    public TagRecordImpl(UUID tagguuid) {
        this.tagguuid = tagguuid;
    }
    
    @Override
    public void setTagName(String name) {
        String oldValue = this.tagname;
        this.tagname = name;
        this.pcs.firePropertyChange(P.NAME.name(), oldValue, name);   
    }

    @Override
    public String getTagName() {
        return tagname;
    }

    @Override
    public void setTagDesc(String desc) {
        String oldValue = this.tagdesc;        
        this.tagdesc = desc;
        this.pcs.firePropertyChange(P.DESCRIPTION.name(), oldValue, desc);
    }

    @Override
    public String getTagDesc() {
        return tagdesc;
    }

    @Override
    public void setTagID(String id) {
        this.id = id;
    }

    @Override
    public String getTagID() {
        return id;
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
    public void setEnable(Boolean enable) {
        Boolean oldValue = this.enable; 
        this.enable = enable;
        if ((!oldValue) && enable) startInstant = Instant.now();
        lastUpdateInstant = startInstant;
        this.pcs.firePropertyChange(P.ENABLE.name(), oldValue, enable); 
    }

    @Override
    public Boolean getEnable() {
        return enable;
    }

    @Override
    public void setDisableOutput(Boolean disableOutput) {
        Boolean oldValue = this.disableOutput ;         
        this.disableOutput = disableOutput;
        this.pcs.firePropertyChange(P.DISABLE_OUTPUT.name(), oldValue, disableOutput);         
    }

    @Override
    public Boolean getDisableOutput() {
        return disableOutput;
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
    public void setTagGroup(UUID tagguuid) {
        this.tagguuid = tagguuid;
    }

    @Override
    public UUID getTagGroup() {
        return tagguuid;
    }
    
    @Override
    public void updateTransmits() {
        transmits++;
    }
    
    @Override
    public int getTransmits() {
        return transmits;
    }

    @Override
    public void updateReceives() {
        receives++;
    }    
    
    @Override
    public int getReceives() {
        return receives;
    }

    @Override
    public void updateErrors() {
        errors++;
    }    
    
    @Override
    public int getErrors() {
        return errors;
    }

    @Override
    public void updateLastReadInstant() {
        lastReadInstant = Instant.now();
    }    
    
    @Override
    public Instant getLastReadInstant() {
        return lastReadInstant;
    }

    @Override
    public void updateLastWriteInstant() {
        lastWriteInstant = Instant.now();
    }    
    
    @Override
    public Instant getLastWriteInstant() {
        return lastWriteInstant; 
    }

    @Override
    public void updateLastErrorInstant() {
        lastErrorInstant = Instant.now();
    }    
    
    @Override
    public Instant getLastErrorInstant() {
        return lastUpdateInstant;
    }












   
    
    
}
