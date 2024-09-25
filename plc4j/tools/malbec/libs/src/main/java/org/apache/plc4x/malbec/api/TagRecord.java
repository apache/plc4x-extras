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
import java.util.UUID;

/* 
 *
 */
public interface TagRecord {
    
    /*
     *
     */ 
    public void setTagName(String name);  
    
    /*
     *
     */  
    public String getTagName();

    /*
     *
     */    
    public void setTagDesc(String desc);   
    
    /*
     *
     */    
    public String getTagDesc();

    /*
     *
     */   
    public void setTagID(String id);  
    
    /*
     *
     */    
    public String getTagID();    
    
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
    public void setEnable(Boolean enable);
    
    /*
     *
     */    
    public Boolean getEnable();

    /*
     *
     */    
    public void setDisableOutput(Boolean disableOutput);
    
    /*
     *
     */    
    public Boolean getDisableOutput();     
    
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
    public void setTagGroup(UUID tagguuid);    
    
    /*
     *
     */     
    public UUID getTagGroup();
   
    /*
     *
     */     
    public void updateTransmits();    
    
    /*
     *
     */     
    public int getTransmits();
   
    /*
     *
     */     
    public void updateReceives();    
    
    /*
     *
     */     
    public int getReceives();
    
    /*
     *
     */     
    public void updateErrors();    
    
    /*
     *
     */     
    public int getErrors();
    
    /*
     *
     */     
    public void updateLastReadInstant();    
    
    /*
     *
     */     
    public Instant getLastReadInstant();
    
    /*
     *
     */     
    public void updateLastWriteInstant();    
    
    /*
     *
     */     
    public Instant getLastWriteInstant();
 
    /*
     *
     */     
    public void updateLastErrorInstant(); 
    
    /*
     *
     */     
    public Instant getLastErrorInstant();      
    
}
