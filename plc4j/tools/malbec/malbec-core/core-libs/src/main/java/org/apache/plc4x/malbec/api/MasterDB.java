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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 
 *
 */
public interface MasterDB {

    /*
     *
     */ 
    public void addDriver(DriverRecord driver);
    
    /*
     *
     */    
    public Optional<DriverRecord> getDriver(UUID uuid);
    
    /*
     *
     */     
    public Optional<DriverRecord> getDriver(String drvname);
    
    /*
     *
     */     
    public Collection<DriverRecord> getDrivers();
    
    /*
     *
     */     
    public void removeDriver(UUID uuid);
 
    /*
     *
     */    
    public void addDevice(UUID driver, DeviceRecord device);
    
    /*
     *
     */    
    public Optional<DeviceRecord> getDevice(UUID uuid);
    
    /*
     *
     */    
    public Optional<DeviceRecord> getDevice(String devicename);
    
    /*
     *
     */    
    public void removeDevice(DeviceRecord device);
    
    /*
     *
     */    
    public void removeDevice(UUID uuid);      
    
    /*
     *
     */    
    public void addTagGroup(UUID device, TagGroupRecord taggroup);
    
    /*
     *
     */    
    public Optional<TagGroupRecord>  getTagGroup(UUID uuid);
    
    /*
     *
     */    
    public Optional<TagGroupRecord>  getTagGroup(String taggname);
    
    /*
     *
     */    
    public void removeTagGroup(TagGroupRecord taggroup);
    
    /*
     *
     */    
    public void removeTagGroup(UUID uuid);    
    
    /*
     *
     */     
    public void addTag(UUID taggroup, TagRecord tag);
    
    /*
     *
     */    
    public Optional<TagRecord> getTag(UUID uuid);
    
    /*
     *
     */    
    public Optional<TagRecord> getTag(String  tagname);
    
    /*
     *
     */    
    public void removeTag(TagRecord tag);
    
    /*
     *
     */     
    public void removeTag(UUID uuid);     
    
    /*
     *
     */   
    public DriverRecord getDriverByCode(String code);
    
    /*
     *
     */    
    public DriverRecord getDriverByName(String name);  
    
    /*
     *
     */     
    public DeviceRecord createDeviceDBRecord();
    
    /*
     *
     */   
    public TagGroupRecord createTagGroupDBRecord();

    /*
     *
     */   
    public TagRecord createTagDBRecord(); 
    
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
    public List<String> getDriverCodes();

    /*
     *
     */    
    public List<String> getDriverNames();    
    
    /*
     *
     */    
    public int getNumberOfDrivers();
    
    /*
     *
     */   
    public int getNumberOfDevice();
    
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
    public int getTransmits();
    
    /*
     *
     */   
    public int getReceives();
    
    /*
     *
     */    
    public int getErrors();
    
    
}
