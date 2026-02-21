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
package org.apache.plc4x.malbec.api;

import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.openide.util.Lookup;

/* 
 *
 */
public interface DeviceRecord extends Lookup.Provider {
    
    /*
     *
     */    
    public void setDeviceName(String name);

    /*
     *
     */    
    public String getDeviceName();

    /*
     *
     */    
    public void setDeviceDescription(String desc); 
    
    /*
     *
     */     
    public String getDeviceDescription();    
    
    /*
     *
     */    
    public void setUUID(UUID uuid);
    
    /*
     *
     */    
    public UUID getUUID();
    
    /*
     *
     */    
    public void setProtocolCode(UUID protocol);
    
    /*
     *
     */    
    public UUID getProtocolCode();

    /*
     *
     */   
    public void setTreeLocation(UUID treenode);
    
    /*
     *
     */   
    public UUID getTreeLocation();
    
    /*
     *
     */    
    public void setEnable(Boolean enable);
    
    /*
     *
     */    
    public Boolean getEnable();    
    
    /*
     *
     */    
    public void setPropertie(String id, String str);
    
    /*
     *
     */    
    public String getPropertie(String id);
    
    /*
     *
     */     
    public Map<String, String> getProperties();
        
    /*
     *
     */    
    public void addTagGroup(TagGroupRecord  tagg);

    /*
     *
     */   
    public Optional<TagGroupRecord> getTagGroup(TagGroupRecord tagg); 
    
    /*
     *
     */    
    public Optional<TagGroupRecord> getTagGroup(UUID uuid);
    
    /*
     *
     */     
    public Optional<TagGroupRecord> getTagGroup(String name);
    
    /*
     *
     */    
    public Collection<TagGroupRecord> getTagGroups();

    /*
     *
     */    
    public void removeTagGroup(TagGroupRecord  device);    
    
    /*
     *
     */ 
    public void addPropertyChangeListener(PropertyChangeListener listener);
    
    /*
     *
     */     
    public void removePropertyChangeListener(PropertyChangeListener listener);    
     
    /*
     *
     */    
    public int getTransmits();
    
    /*
     *
     */    
    public int getReceives();
    
    /*
     *
     */     
    public int getErrors();

    /*
     *
     */     
    public int getNumberOfTagGroups();  
    
    /*
     *
     */     
    public int getNumberOfTags();
    
    /*
     *
     */        
    public Instant getStartInstant();
    
    /*
     *
     */    
    public Instant  getCurrentInstant();
    
    /*
     *
     */     
    public void  updateLastUpdateInstant();     
    
    /*
     *
     */     
    public Instant  getLastUpdateInstant();   
    
    
}
