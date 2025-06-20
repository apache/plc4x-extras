package org.apache.plc4x.merlot.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the internal data structure associated with a Programmable Logic Controller (PLC)
 * or Remote Terminal Unit (RTU). This model serves as the foundation for exception-based and 
 * time-based subscriptions.
 *
 */
public interface PlcModel {

    public static final String PLCMODEL_CATEGORY = "plc4x.plcmodel.category";
    
    public static final String PLCMODEL_DEVICE = "plc4x.plcmodel.device";    
    
    
    /**
     * Lists all available memory areas defined in this model.
     * These areas are typically defined by the specific driver implementation.
     *
     * @return A set of strings representing the available memory areas
     */
    Set<String> listMemoryAreas();

    /**
     * Retrieves the unique identifier assigned to a specific memory area.
     *
     * @param memoryArea The name of the memory area
     * @return The ID of the memory area, or null if not found
     * @throws IllegalArgumentException if memoryArea is null or empty
     */
    Integer getMemoryAreaId(String memoryArea);  
    
    /**
     * Creates memory areas associated with a specific PLC or device model.
     *
     * @param configuration The configuration object containing memory area specifications
     * @throws IllegalArgumentException if configuration is null or invalid
     * @throws PlcConfigurationException if creation fails
     */
    void createMemoryArea(Object dbRecord);
    
    /**
     * Takes the PlcItem representing the memory area.
     *
     * @param memoryArea The name of the memory area
     * @param indexThe specific index within the memory area
     * @throws IllegalArgumentException if configuration is null or invalid
     * @throws PlcConfigurationException if creation fails
     */
    Optional<PlcItem> getMemoryAreaPlcItem(String memoryArea, Integer index);    
    
    /**
     * Creates a scan group for monitoring specific memory areas in the PLC/device model.
     *
     * @param configuration The scan group configuration
     * @throws IllegalArgumentException if configuration is null or invalid
     * @throws PlcConfigurationException if creation fails
     */
    void createScanGroup(Object dbRecord);    
    
    /**
     * Registers a listener for a specific memory area to receive updates.
     *
     * @param memoryArea The name of the memory area to monitor
     * @param index The specific index within the memory area
     * @param listener The listener to receive updates
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    void addMemoryAreaListener(String memoryArea, Integer index, PlcItemListener listener); 
    
    /**
     * Removes a previously registered listener from a memory area.
     *
     * @param memoryArea The name of the memory area
     * @param index The specific index within the memory area
     * @param listener The listener to remove
     */
    void removeMemoryAreaListener(String memoryArea, Integer index, PlcItemListener listener);    
    
    /**
     * Returns the number of segments in a memory area. 
     * For example:
     * - MODBUS: Always returns 1 for any memory area type
     * - S7 driver: Returns the number of DB instances required for DBs
     *
     * @param memoryArea The name of the memory area
     * @return The number of segments in the memory area
     * @throws IllegalArgumentException if memoryArea is null or invalid
     */
    Integer getMemoryAreaSegmentCount(String memoryArea);      
    
    /**
     * Retrieves the list of indices associated with a memory area.
     *
     * @param memoryArea The name of the memory area
     * @return List of indices for the memory area
     * @throws IllegalArgumentException if memoryArea is null or invalid
     */
    Set<Integer> getMemoryAreaSegmentIds(String memoryArea);
    
    /**
     * Retrieves the UUIDs of scan groups created for this model.
     * Each model is responsible for creating and managing its own scan groups.
     *
     * @param memoryArea The name of the memory area
     * @return List of UUIDs representing the scan groups
     * @throws IllegalArgumentException if memoryArea is null or invalid
     */
    Set<UUID> getModelPlcGroupUuids(String memoryArea);   
    
    /**
     * Retrieves the UUIDs of PLC items created for model updates.
     *
     * @param memoryArea The name of the memory area
     * @return List of UUIDs representing the PLC items
     * @throws IllegalArgumentException if memoryArea is null or invalid
     */
    Set<UUID> getModelPlcItemUuids(String memoryArea);      
}