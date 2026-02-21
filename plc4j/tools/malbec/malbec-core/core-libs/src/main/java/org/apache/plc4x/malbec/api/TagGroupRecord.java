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
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.openide.util.Lookup;


/* 
 *
 */
public interface TagGroupRecord  extends Lookup.Provider {
    
    
    /*
     *
     */    
    public void setTagGroupName(String name);  
    
    /*
     *
     */    
    public String getTagGroupName();
    
    /*
     *
     */   
    public void setTagGroupDesc(String desc);
    
    /*
     *
     */   
    public String getTagGroupDesc();    
    
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
    public void setScanTime(int ms);
    
    /*
     *
     */   
    public int getScanTime(); 
        
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
    public void addTag(TagRecord  tag);
    
    /*
     *
     */   
    public Optional<TagRecord> getTag(TagRecord tag);

    /*
     *
     */   
    public Optional<TagRecord> getTag(UUID uuid);
    
    /*
     *
     */
    public Optional<TagRecord> getTag(String name);
    
    /*
     *
     */   
    public Collection<TagRecord> getTags(); 
    
    /*
     *
     */   
    public void removeTag(TagRecord  tag); 
    
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
    public void setDeviceRecord(UUID deviceuuid);    
    
    /*
     *
     */   
    public UUID getDeviceRecord();    

    /*
     *
     */   
    public int getJitter();
    
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
    public int getNumberOfTags();
    
    /*
     *
     */  
    public Instant getStartInstant();
    
    /*
     *
     */  
    public Instant getCurrentInstant();
  
    /*
     *
     */   
    public void updateLastUpdateDateTime();
            
    /*
     *
     */   
    public Instant getLastUpdateDateTime();      
    
}
