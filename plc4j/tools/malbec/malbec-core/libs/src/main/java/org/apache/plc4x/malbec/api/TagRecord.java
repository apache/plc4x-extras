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

/**
 * Interface representing a single tag in the Malbec system.
 * <p>
 * A {@code TagRecord} defines a specific data point or variable within a device that can be
 * read from or written to. It contains configuration details such as name, description,
 * identifier (address), and UUID.
 * </p>
 * <p>
 * It also tracks runtime statistics including transmission/reception counts, error counts,
 * and timestamps for the last read, write, and error events.
 * </p>
 */
public interface TagRecord {
    
    /**
     * Sets the human-readable name of the tag.
     * 
     * @param name the new name for the tag
     */ 
    public void setTagName(String name);  
    
    /**
     * Gets the human-readable name of the tag.
     * 
     * @return the name of the tag
     */  
    public String getTagName();

    /**
     * Sets the description of the tag.
     * 
     * @param desc the new description for the tag
     */    
    public void setTagDesc(String desc);   
    
    /**
     * Gets the description of the tag.
     * 
     * @return the description of the tag
     */    
    public String getTagDesc();

    /**
     * Sets the unique string identifier or address for the tag (e.g., a PLC address).
     * 
     * @param id the identifier string
     */   
    public void setTagID(String id);  
    
    /**
     * Gets the unique string identifier or address of the tag.
     * 
     * @return the identifier string
     */    
    public String getTagID();    
    
    /**
     * Sets the unique identifier (UUID) for this tag record.
     * 
     * @param uuid the new UUID
     */    
    public void setUUID(UUID uuid);  
    
    /**
     * Gets the unique identifier (UUID) of this tag record.
     * 
     * @return the UUID
     */     
    public UUID getUUID();

    /**
     * Enables or disables the tag for processing.
     * 
     * @param enable {@code true} to enable the tag, {@code false} to disable it
     */    
    public void setEnable(Boolean enable);
    
    /**
     * Checks if the tag is currently enabled.
     * 
     * @return {@code true} if enabled, {@code false} otherwise
     */    
    public Boolean getEnable();

    /**
     * Sets whether output (writing) to this tag is disabled.
     * 
     * @param disableOutput {@code true} to disable writing to this tag, {@code false} to allow it
     */    
    public void setDisableOutput(Boolean disableOutput);
    
    /**
     * Checks if output (writing) to this tag is disabled.
     * 
     * @return {@code true} if output is disabled, {@code false} otherwise
     */    
    public Boolean getDisableOutput();     
    
    /**
     * Adds a property change listener to this tag record.
     * 
     * @param listener the listener to add
     */    
    public void addPropertyChangeListener(PropertyChangeListener listener);
    
    /**
     * Removes a property change listener from this tag record.
     * 
     * @param listener the listener to remove
     */   
    public void removePropertyChangeListener(PropertyChangeListener listener);    
    
    /**
     * Associates this tag with a parent tag group.
     * 
     * @param tagguuid the UUID of the parent {@link TagGroupRecord}
     */     
    public void setTagGroup(UUID tagguuid);    
    
    /**
     * Gets the UUID of the parent tag group associated with this tag.
     * 
     * @return the UUID of the parent tag group
     */     
    public UUID getTagGroup();
   
    /**
     * Increments the count of successful transmissions (writes) for this tag.
     */     
    public void updateTransmits();    
    
    /**
     * Gets the total number of successful transmissions (writes) for this tag.
     * 
     * @return the number of transmits
     */     
    public int getTransmits();
   
    /**
     * Increments the count of successful receptions (reads) for this tag.
     */     
    public void updateReceives();    
    
    /**
     * Gets the total number of successful receptions (reads) for this tag.
     * 
     * @return the number of receives
     */     
    public int getReceives();
    
    /**
     * Increments the count of errors encountered for this tag.
     */     
    public void updateErrors();    
    
    /**
     * Gets the total number of errors encountered for this tag.
     * 
     * @return the number of errors
     */     
    public int getErrors();
    
    /**
     * Updates the timestamp of the last successful read operation to the current time.
     */     
    public void updateLastReadInstant();    
    
    /**
     * Gets the timestamp of the last successful read operation.
     * 
     * @return the {@link Instant} of the last read
     */     
    public Instant getLastReadInstant();
    
    /**
     * Updates the timestamp of the last successful write operation to the current time.
     */     
    public void updateLastWriteInstant();    
    
    /**
     * Gets the timestamp of the last successful write operation.
     * 
     * @return the {@link Instant} of the last write
     */     
    public Instant getLastWriteInstant();
 
    /**
     * Updates the timestamp of the last error occurrence to the current time.
     */     
    public void updateLastErrorInstant(); 
    
    /**
     * Gets the timestamp of the last error occurrence.
     * 
     * @return the {@link Instant} of the last error
     */     
    public Instant getLastErrorInstant();      
    
}
