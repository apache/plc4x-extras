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

/**
 * Interface representing the master database for the Malbec system.
 * <p>
 * This interface acts as the central registry and management point for the entire
 * configuration hierarchy, including {@link DriverRecord}s, {@link DeviceRecord}s,
 * {@link TagGroupRecord}s, and {@link TagRecord}s.
 * </p>
 * <p>
 * It provides methods to create, add, retrieve, and remove these records, as well as
 * to query system-wide statistics and manage property change listeners.
 * </p>
 */
public interface MasterDB {

    /**
     * Adds a driver record to the master database.
     * 
     * @param driver the {@link DriverRecord} to add
     */ 
    public void addDriver(DriverRecord driver);
    
    /**
     * Retrieves a driver record by its unique identifier.
     * 
     * @param uuid the UUID of the driver
     * @return an {@link Optional} containing the driver if found, otherwise empty
     */    
    public Optional<DriverRecord> getDriver(UUID uuid);
    
    /**
     * Retrieves a driver record by its name.
     * 
     * @param drvname the name of the driver
     * @return an {@link Optional} containing the driver if found, otherwise empty
     */     
    public Optional<DriverRecord> getDriver(String drvname);
    
    /**
     * Retrieves all driver records in the database.
     * 
     * @return a collection of all {@link DriverRecord}s
     */     
    public Collection<DriverRecord> getDrivers();
    
    /**
     * Removes a driver record by its unique identifier.
     * 
     * @param uuid the UUID of the driver to remove
     */     
    public void removeDriver(UUID uuid);

    /**
     * Adds a device to a specific driver.
     * 
     * @param driver the UUID of the driver to which the device will be added
     * @param device the {@link DeviceRecord} to add
     */    
    public void addDevice(UUID driver, DeviceRecord device);
    
    /**
     * Retrieves a device record by its unique identifier.
     * 
     * @param uuid the UUID of the device
     * @return an {@link Optional} containing the device if found, otherwise empty
     */    
    public Optional<DeviceRecord> getDevice(UUID uuid);
    
    /**
     * Retrieves a device record by its name.
     * 
     * @param devicename the name of the device
     * @return an {@link Optional} containing the device if found, otherwise empty
     */    
    public Optional<DeviceRecord> getDevice(String devicename);
    
    /**
     * Removes a specific device record.
     * 
     * @param device the {@link DeviceRecord} to remove
     */    
    public void removeDevice(DeviceRecord device);
    
    /**
     * Removes a device record by its unique identifier.
     * 
     * @param uuid the UUID of the device to remove
     */    
    public void removeDevice(UUID uuid);      
    
    /**
     * Adds a tag group to a specific device.
     * 
     * @param device the UUID of the device to which the tag group will be added
     * @param taggroup the {@link TagGroupRecord} to add
     */    
    public void addTagGroup(UUID device, TagGroupRecord taggroup);
    
    /**
     * Retrieves a tag group record by its unique identifier.
     * 
     * @param uuid the UUID of the tag group
     * @return an {@link Optional} containing the tag group if found, otherwise empty
     */    
    public Optional<TagGroupRecord>  getTagGroup(UUID uuid);
    
    /**
     * Retrieves a tag group record by its name.
     * 
     * @param taggname the name of the tag group
     * @return an {@link Optional} containing the tag group if found, otherwise empty
     */    
    public Optional<TagGroupRecord>  getTagGroup(String taggname);
    
    /**
     * Removes a specific tag group record.
     * 
     * @param taggroup the {@link TagGroupRecord} to remove
     */    
    public void removeTagGroup(TagGroupRecord taggroup);
    
    /**
     * Removes a tag group record by its unique identifier.
     * 
     * @param uuid the UUID of the tag group to remove
     */    
    public void removeTagGroup(UUID uuid);    
    
    /**
     * Adds a tag to a specific tag group.
     * 
     * @param taggroup the UUID of the tag group to which the tag will be added
     * @param tag the {@link TagRecord} to add
     */     
    public void addTag(UUID taggroup, TagRecord tag);
    
    /**
     * Retrieves a tag record by its unique identifier.
     * 
     * @param uuid the UUID of the tag
     * @return an {@link Optional} containing the tag if found, otherwise empty
     */    
    public Optional<TagRecord> getTag(UUID uuid);
    
    /**
     * Retrieves a tag record by its name.
     * 
     * @param tagname the name of the tag
     * @return an {@link Optional} containing the tag if found, otherwise empty
     */    
    public Optional<TagRecord> getTag(String  tagname);
    
    /**
     * Removes a specific tag record.
     * 
     * @param tag the {@link TagRecord} to remove
     */    
    public void removeTag(TagRecord tag);
    
    /**
     * Removes a tag record by its unique identifier.
     * 
     * @param uuid the UUID of the tag to remove
     */     
    public void removeTag(UUID uuid);     
    
    /**
     * Retrieves a driver record by its protocol code.
     * 
     * @param code the protocol code of the driver
     * @return the {@link DriverRecord} matching the code
     */   
    public DriverRecord getDriverByCode(String code);
    
    /**
     * Retrieves a driver record by its name (exact match).
     * 
     * @param name the name of the driver
     * @return the {@link DriverRecord} matching the name
     */    
    public DriverRecord getDriverByName(String name);  
    
    /**
     * Creates a new, empty device record instance.
     * 
     * @return a new {@link DeviceRecord} instance
     */     
    public DeviceRecord createDeviceDBRecord();
    
    /**
     * Creates a new, empty tag group record instance.
     * 
     * @return a new {@link TagGroupRecord} instance
     */   
    public TagGroupRecord createTagGroupDBRecord();

    /**
     * Creates a new, empty tag record instance.
     * 
     * @return a new {@link TagRecord} instance
     */   
    public TagRecord createTagDBRecord(); 
    
    /**
     * Adds a property change listener to the master database.
     * 
     * @param listener the listener to add
     */  
    public void addPropertyChangeListener(PropertyChangeListener listener);
    
    /**
     * Removes a property change listener from the master database.
     * 
     * @param listener the listener to remove
     */   
    public void removePropertyChangeListener(PropertyChangeListener listener);
    
    /**
     * Gets a list of all driver protocol codes currently registered.
     * 
     * @return a list of protocol code strings
     */   
    public List<String> getDriverCodes();

    /**
     * Gets a list of all driver names currently registered.
     * 
     * @return a list of driver name strings
     */    
    public List<String> getDriverNames();    
    
    /**
     * Gets the total number of drivers in the database.
     * 
     * @return the count of drivers
     */    
    public int getNumberOfDrivers();
    
    /**
     * Gets the total number of devices across all drivers.
     * 
     * @return the count of devices
     */   
    public int getNumberOfDevice();
    
    /**
     * Gets the total number of tag groups across all devices.
     * 
     * @return the count of tag groups
     */   
    public int getNumberOfTagGroups();
    
    /**
     * Gets the total number of tags across all tag groups.
     * 
     * @return the count of tags
     */    
    public int getNumberOfTags(); 
    
    /**
     * Gets the total number of transmissions across the entire system.
     * 
     * @return the total transmit count
     */    
    public int getTransmits();
    
    /**
     * Gets the total number of receptions across the entire system.
     * 
     * @return the total receive count
     */   
    public int getReceives();
    
    /**
     * Gets the total number of errors recorded across the entire system.
     * 
     * @return the total error count
     */    
    public int getErrors();
    
    
}
