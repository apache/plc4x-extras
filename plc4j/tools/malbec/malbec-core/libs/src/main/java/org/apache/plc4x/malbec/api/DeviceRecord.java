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

/**
 * Interface representing a device record in the Malbec system.
 * <p>
 * This interface extends {@link Lookup.Provider} and provides methods for managing
 * device properties, such as name, description, UUID, protocol code, and tag groups.
 * It also supports property change listeners and tracking device statistics.
 * </p>
 */
public interface DeviceRecord extends Lookup.Provider {
    
    /**
     * Sets the name of the device.
     * 
     * @param name the new name of the device
     */    
    public void setDeviceName(String name);

    /**
     * Gets the name of the device.
     * 
     * @return the name of the device
     */    
    public String getDeviceName();

    /**
     * Sets the description of the device.
     * 
     * @param desc the new description of the device
     */    
    public void setDeviceDescription(String desc); 
    
    /**
     * Gets the description of the device.
     * 
     * @return the description of the device
     */     
    public String getDeviceDescription();    
    
    /**
     * Sets the UUID of the device.
     * 
     * @param uuid the new UUID of the device
     */    
    public void setUUID(UUID uuid);
    
    /**
     * Gets the UUID of the device.
     * 
     * @return the UUID of the device
     */    
    public UUID getUUID();
    
    /**
     * Sets the protocol code associated with the device.
     * 
     * @param protocol the UUID representing the protocol code
     */    
    public void setProtocolCode(UUID protocol);
    
    /**
     * Gets the protocol code associated with the device.
     * 
     * @return the UUID representing the protocol code
     */    
    public UUID getProtocolCode();

    /**
     * Sets the location of the device in the tree structure.
     * 
     * @param treenode the UUID of the tree node location
     */   
    public void setTreeLocation(UUID treenode);
    
    /**
     * Gets the location of the device in the tree structure.
     * 
     * @return the UUID of the tree node location
     */   
    public UUID getTreeLocation();
    
    /**
     * Enables or disables the device.
     * 
     * @param enable {@code true} to enable the device, {@code false} to disable it
     */    
    public void setEnable(Boolean enable);
    
    /**
     * Checks if the device is enabled.
     * 
     * @return {@code true} if the device is enabled, {@code false} otherwise
     */    
    public Boolean getEnable();    
    
    /**
     * Sets a specific property for the device.
     * 
     * @param id the identifier of the property
     * @param str the value of the property
     */    
    public void setPropertie(String id, String str);
    
    /**
     * Gets a specific property of the device.
     * 
     * @param id the identifier of the property
     * @return the value of the property, or {@code null} if not found
     */    
    public String getPropertie(String id);
    
    /**
     * Gets all properties of the device.
     * 
     * @return a map containing all device properties
     */     
    public Map<String, String> getProperties();
        
    /**
     * Adds a tag group to the device.
     * 
     * @param tagg the tag group record to add
     */    
    public void addTagGroup(TagGroupRecord  tagg);

    /**
     * Retrieves a specific tag group from the device.
     * 
     * @param tagg the tag group record to retrieve
     * @return an {@link Optional} containing the tag group if found, otherwise empty
     */   
    public Optional<TagGroupRecord> getTagGroup(TagGroupRecord tagg); 
    
    /**
     * Retrieves a tag group by its UUID.
     * 
     * @param uuid the UUID of the tag group
     * @return an {@link Optional} containing the tag group if found, otherwise empty
     */    
    public Optional<TagGroupRecord> getTagGroup(UUID uuid);
    
    /**
     * Retrieves a tag group by its name.
     * 
     * @param name the name of the tag group
     * @return an {@link Optional} containing the tag group if found, otherwise empty
     */     
    public Optional<TagGroupRecord> getTagGroup(String name);
    
    /**
     * Gets all tag groups associated with the device.
     * 
     * @return a collection of all tag group records
     */    
    public Collection<TagGroupRecord> getTagGroups();

    /**
     * Removes a tag group from the device.
     * 
     * @param device the tag group record to remove
     */    
    public void removeTagGroup(TagGroupRecord  device);    
    
    /**
     * Adds a property change listener to the device.
     * 
     * @param listener the listener to add
     */ 
    public void addPropertyChangeListener(PropertyChangeListener listener);
    
    /**
     * Removes a property change listener from the device.
     * 
     * @param listener the listener to remove
     */     
    public void removePropertyChangeListener(PropertyChangeListener listener);    
     
    /**
     * Gets the number of transmissions.
     * 
     * @return the count of transmissions
     */    
    public int getTransmits();
    
    /**
     * Gets the number of receptions.
     * 
     * @return the count of receptions
     */    
    public int getReceives();
    
    /**
     * Gets the number of errors encountered.
     * 
     * @return the count of errors
     */     
    public int getErrors();

    /**
     * Gets the total number of tag groups associated with the device.
     * 
     * @return the number of tag groups
     */     
    public int getNumberOfTagGroups();  
    
    /**
     * Gets the total number of tags associated with the device.
     * 
     * @return the number of tags
     */     
    public int getNumberOfTags();
    
    /**
     * Gets the instant when the device started.
     * 
     * @return the start {@link Instant}
     */        
    public Instant getStartInstant();
    
    /**
     * Gets the current instant.
     * 
     * @return the current {@link Instant}
     */    
    public Instant  getCurrentInstant();
    
    /**
     * Updates the last update instant to the current time.
     */     
    public void  updateLastUpdateInstant();     
    
    /**
     * Gets the instant of the last update.
     * 
     * @return the last update {@link Instant}
     */     
    public Instant  getLastUpdateInstant();   
    
    
}
