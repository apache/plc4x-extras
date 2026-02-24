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


/**
 * Interface representing a group of tags in the Malbec system.
 * <p>
 * A {@code TagGroupRecord} organizes a collection of {@link TagRecord}s that are polled
 * or processed together, typically sharing a common scan cycle time.
 * This interface extends {@link Lookup.Provider} and provides methods for managing
 * the group's configuration (name, description, scan time), its contained tags,
 * and monitoring its runtime statistics.
 * </p>
 */
public interface TagGroupRecord  extends Lookup.Provider {
    
    
    /**
     * Sets the name of the tag group.
     * 
     * @param name the new name for the tag group
     */    
    public void setTagGroupName(String name);  
    
    /**
     * Gets the name of the tag group.
     * 
     * @return the name of the tag group
     */    
    public String getTagGroupName();
    
    /**
     * Sets the description of the tag group.
     * 
     * @param desc the new description for the tag group
     */   
    public void setTagGroupDesc(String desc);
    
    /**
     * Gets the description of the tag group.
     * 
     * @return the description of the tag group
     */   
    public String getTagGroupDesc();    
    
    /**
     * Sets the unique identifier (UUID) for this tag group.
     * 
     * @param uuid the new UUID
     */   
    public void setUUID(UUID uuid);  
    
    /**
     * Gets the unique identifier (UUID) of this tag group.
     * 
     * @return the UUID of the tag group
     */   
    public UUID getUUID();

    /**
     * Sets the scan time (polling interval) for this tag group in milliseconds.
     * 
     * @param ms the scan time in milliseconds
     */   
    public void setScanTime(int ms);
    
    /**
     * Gets the scan time (polling interval) of this tag group.
     * 
     * @return the scan time in milliseconds
     */   
    public int getScanTime(); 
        
    /**
     * Enables or disables the tag group.
     * 
     * @param enable {@code true} to enable the group, {@code false} to disable it
     */    
    public void setEnable(Boolean enable);
    
    /**
     * Checks if the tag group is currently enabled.
     * 
     * @return {@code true} if enabled, {@code false} otherwise
     */   
    public Boolean getEnable(); 

    /**
     * Adds a tag record to this group.
     * 
     * @param tag the {@link TagRecord} to add
     */  
    public void addTag(TagRecord  tag);
    
    /**
     * Retrieves a specific tag record from this group based on an existing record instance.
     * 
     * @param tag the tag record to look up
     * @return an {@link Optional} containing the found tag record, or empty if not found
     */   
    public Optional<TagRecord> getTag(TagRecord tag);

    /**
     * Retrieves a tag record from this group by its unique identifier.
     * 
     * @param uuid the UUID of the tag
     * @return an {@link Optional} containing the found tag record, or empty if not found
     */   
    public Optional<TagRecord> getTag(UUID uuid);
    
    /**
     * Retrieves a tag record from this group by its name.
     * 
     * @param name the name of the tag
     * @return an {@link Optional} containing the found tag record, or empty if not found
     */
    public Optional<TagRecord> getTag(String name);
    
    /**
     * Gets all tag records in this group.
     * 
     * @return a collection of all {@link TagRecord}s in the group
     */   
    public Collection<TagRecord> getTags(); 
    
    /**
     * Removes a tag record from this group.
     * 
     * @param tag the {@link TagRecord} to remove
     */   
    public void removeTag(TagRecord  tag); 
    
    /**
     * Adds a property change listener to this tag group.
     * 
     * @param listener the listener to add
     */   
    public void addPropertyChangeListener(PropertyChangeListener listener);
    
    /**
     * Removes a property change listener from this tag group.
     * 
     * @param listener the listener to remove
     */   
    public void removePropertyChangeListener(PropertyChangeListener listener);    
    
    /**
     * Associates this tag group with a parent device record.
     * 
     * @param deviceuuid the UUID of the parent {@link DeviceRecord}
     */   
    public void setDeviceRecord(UUID deviceuuid);    
    
    /**
     * Gets the UUID of the parent device record associated with this group.
     * 
     * @return the UUID of the parent device
     */   
    public UUID getDeviceRecord();    

    /**
     * Gets the jitter (variation in scan time) observed for this group.
     * 
     * @return the jitter value in milliseconds
     */   
    public int getJitter();
    
    /**
     * Gets the total number of transmission operations performed for this group.
     * 
     * @return the number of transmits
     */  
    public int getTransmits();
    
    /**
     * Gets the total number of reception operations performed for this group.
     * 
     * @return the number of receives
     */   
    public int getReceives();
    
    /**
     * Gets the total number of errors encountered by this group.
     * 
     * @return the number of errors
     */   
    public int getErrors();

    /**
     * Gets the number of tags currently in this group.
     * 
     * @return the count of tags
     */
    public int getNumberOfTags();
    
    /**
     * Gets the instant when this tag group was started or initialized.
     * 
     * @return the start {@link Instant}
     */  
    public Instant getStartInstant();
    
    /**
     * Gets the current instant.
     * 
     * @return the current {@link Instant}
     */  
    public Instant getCurrentInstant();
  
    /**
     * Updates the last update timestamp to the current time.
     */   
    public void updateLastUpdateDateTime();
            
    /**
     * Gets the instant when the tag group was last updated.
     * 
     * @return the last update {@link Instant}
     */   
    public Instant getLastUpdateDateTime();      
    
}
