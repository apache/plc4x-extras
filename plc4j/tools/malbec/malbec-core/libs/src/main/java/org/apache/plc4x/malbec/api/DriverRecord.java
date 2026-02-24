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
import org.apache.plc4x.java.api.PlcDriver;
import org.openide.util.Lookup;

/**
 * Interface representing a driver record in the Malbec system.
 * <p>
 * This interface extends {@link Lookup.Provider} and serves as a contract for managing
 * PLC driver instances. It provides methods to configure protocol details, manage associated
 * devices, enable/disable the driver, and track operational statistics such as transmission
 * counts and error rates.
 * </p>
 */
public interface DriverRecord extends Lookup.Provider {
    
    /**
     * Sets the protocol code for this driver.
     * 
     * @param protocol the protocol code string
     */   
    public void setProtocolCode(String protocol); 
    
    /**
     * Gets the protocol code of this driver.
     * 
     * @return the protocol code string
     */    
    public String getProtocolCode();
    
    /**
     * Sets the human-readable name of the protocol.
     * 
     * @param name the protocol name
     */    
    public void setProtocolName(String name);
    
    /**
     * Gets the human-readable name of the protocol.
     * 
     * @return the protocol name
     */    
    public String getProtocolName();
    
    /**
     * Sets the unique identifier (UUID) for this driver record.
     * 
     * @param uuid the new UUID
     */    
    public void setUUID(UUID uuid);
    
    /**
     * Gets the unique identifier (UUID) of this driver record.
     * 
     * @return the UUID
     */    
    public UUID getUUID();
    
    /**
     * Retrieves the underlying PLC4X driver instance.
     * 
     * @return the {@link PlcDriver} instance
     */     
    public PlcDriver getPlcDriver();

    /**
     * Enables or disables the driver.
     * 
     * @param enable {@code true} to enable the driver, {@code false} to disable it
     */    
    public void setEnable(Boolean enable);
    
    /**
     * Checks if the driver is currently enabled.
     * 
     * @return {@code true} if enabled, {@code false} otherwise
     */    
    public Boolean getEnable(); 
    
    /**
     * Adds a device to be managed by this driver.
     * 
     * @param device the {@link DeviceRecord} to add
     */    
    public void addDevice(DeviceRecord  device);
    
    /**
     * Retrieves a specific device record based on an existing record instance.
     * 
     * @param device the device record to look up
     * @return an {@link Optional} containing the found device record, or empty if not found
     */    
    public Optional<DeviceRecord> getDevice(DeviceRecord device);

    /**
     * Retrieves a device record by its unique identifier.
     * 
     * @param uuid the UUID of the device
     * @return an {@link Optional} containing the found device record, or empty if not found
     */    
    public Optional<DeviceRecord> getDevice(UUID uuid);
    
    /**
     * Retrieves a device record by its name.
     * 
     * @param name the name of the device
     * @return an {@link Optional} containing the found device record, or empty if not found
     */    
    public Optional<DeviceRecord> getDevice(String name);
    
    /**
     * Gets all devices managed by this driver.
     * 
     * @return a collection of {@link DeviceRecord}s
     */    
    public Collection<DeviceRecord> getDevices();

    /**
     * Removes a device from this driver.
     * 
     * @param device the {@link DeviceRecord} to remove
     */     
    public void removeDevice(DeviceRecord  device);

    /**
     * Adds a property change listener to this driver record.
     * 
     * @param listener the listener to add
     */    
    public void addPropertyChangeListener(PropertyChangeListener listener);
    
    /**
     * Removes a property change listener from this driver record.
     * 
     * @param listener the listener to remove
     */    
    public void removePropertyChangeListener(PropertyChangeListener listener);    
       
    /**
     * Gets the total number of transmission operations performed by this driver.
     * 
     * @return the number of transmits
     */    
    public int getTransmits();
    
    /**
     * Gets the total number of reception operations performed by this driver.
     * 
     * @return the number of receives
     */     
    public int getReceives();
    
    /**
     * Gets the total number of errors encountered by this driver.
     * 
     * @return the number of errors
     */    
    public int getErrors();

    /**
     * Gets the count of devices associated with this driver.
     * 
     * @return the number of devices
     */     
    public int getNumberOfDevice();
    
    /**
     * Gets the total count of tag groups across all devices managed by this driver.
     * 
     * @return the number of tag groups
     */     
    public int getNumberOfTagGroups(); 
    
    /**
     * Gets the total count of tags across all devices managed by this driver.
     * 
     * @return the number of tags
     */    
    public int getNumberOfTags();
    
    /**
     * Gets the instant when this driver record was started or initialized.
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
    public void updateLastUpdateInstant();    
    
    /**
     * Gets the instant when the driver record was last updated.
     * 
     * @return the last update {@link Instant}
     */    
    public Instant getLastUpdateInstant();     
    
}
