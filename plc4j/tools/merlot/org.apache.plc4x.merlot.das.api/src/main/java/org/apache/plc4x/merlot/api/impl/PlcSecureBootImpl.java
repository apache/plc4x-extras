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
package org.apache.plc4x.merlot.api.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.merlot.api.PlcDevice;
import org.apache.plc4x.merlot.api.PlcGeneralFunction;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcSecureBoot;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.apache.plc4x.merlot.scheduler.api.JobContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.jdbc.DataSourceFactory;
import org.slf4j.LoggerFactory;


public class PlcSecureBootImpl implements PlcSecureBoot, Job {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(PlcSecureBootImpl.class);
    private static final String DB_URL = "jdbc:sqlite:data/boot.db";
            
    private static final String SQL_CREATE_TABLE_DEVICES = 
            "CREATE TABLE IF NOT EXISTS Devices("
            + "DeviceUuId TEXT NOT NULL PRIMARY KEY,"
            + "DriverName TEXT,"            
            + "DeviceKey TEXT,"
            + "DeviceUrl TEXT,"
            + "DeviceName TEXT,"
            + "DeviceDescription TEXT,"
            + "DeviceEnable TEXT,"            
            + "Md5 TEXT)";
    
    private static final String SQL_CREATE_TABLE_GROUPS = 
            "CREATE TABLE IF NOT EXISTS Groups("
            + "GroupUuid TEXT NOT NULL PRIMARY KEY,"
            + "DeviceUuid TEXT,"            
            + "GroupName TEXT,"
            + "GroupDescription TEXT,"
            + "GroupScantime TEXT,"
            + "GroupEnable TEXT,"            
            + "Md5 TEXT)";  

    private static final String SQL_CREATE_TABLE_ITEMS = 
            "CREATE TABLE IF NOT EXISTS Items("
            + "ItemUuid TEXT NOT NULL PRIMARY KEY,"
            + "DeviceUuid TEXT,"
            + "GroupUuid TEXT,"
            + "ItemName TEXT,"
            + "ItemDescription TEXT,"
            + "ItemTag TEXT,"
            + "ItemEnable TEXT,"             
            + "Md5 TEXT)";  
    
    private static final String SQL_SELECT_DEVICES = 
            "SELECT * FROM Devices";
    
    private static final String SQL_SELECT_GROUPS = 
            "SELECT * FROM Groups WHERE DeviceUuid = '?'";  
    
    private static final String SQL_SELECT_ITEMS = 
            "SELECT * FROM Items WHERE GroupUuid = '?'";     
    
    private static final String SQL_INSERT_DEVICE = 
            "INSERT INTO Devices(DeviceUuId, DriverName, DeviceKey, DeviceUrl, DeviceName, DeviceDescription, DeviceEnable, Md5)"
            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(DeviceUuId) "
            + "DO "
            + "UPDATE SET "
            + "DriverName = excluded.DriverName, "
            + "DeviceKey = excluded.DeviceKey, "
            + "DeviceUrl =   excluded.DeviceUrl, "
            + "DeviceName =  excluded.DeviceName, "
            + "DeviceDescription= excluded.DeviceDescription, "
            + "DeviceEnable= excluded.DeviceEnable, "            
            + "Md5 =        excluded.Md5;";
              
    
    private static final String SQL_INSERT_GROUP = 
            "INSERT INTO Groups(GroupUuid, DeviceUuid, GroupName, GroupDescription, GroupScantime, GroupEnable, Md5)"
            + "VALUES(?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(GroupUuid) "
            + "DO "
            + "UPDATE SET "
            + "GroupUuid  =         excluded.GroupUuid, "
            + "DeviceUuid  =        excluded.DeviceUuid, "
            + "GroupName =          excluded.GroupName, "
            + "GroupDescription =   excluded.GroupDescription, "
            + "GroupScantime    =   excluded.GroupScantime, "
            + "GroupEnable    =     excluded.GroupEnable, "            
            + "Md5 =                excluded.Md5;";
    
    private static final String SQL_INSERT_ITEM = 
            "INSERT INTO Items(ItemUuid, DeviceUuid, GroupUuid, ItemName, ItemDescription, ItemTag, ItemEnable, Md5)"
            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(ItemUuid) "
            + "DO "
            + "UPDATE SET "
            + "ItemUuid =           excluded.ItemUuid, "
            + "DeviceUuid =         excluded.DeviceUuid, "
            + "GroupUuid =          excluded.GroupUuid, "
            + "ItemName =           excluded.ItemName, "
            + "ItemDescription  =   excluded.ItemDescription, "
            + "ItemTag  =           excluded.ItemTag, "  
            + "ItemEnable  =        excluded.ItemEnable, "
            + "Md5 =                excluded.Md5;";   
    
    private Map<String, PlcDriver> delayedBootPlcDivers = new ConcurrentHashMap<>();
    
    private final BundleContext ctx;
    private final PlcGeneralFunction plcGeneralFunction;
    
    DataSourceFactory dsFactory = null;
    Connection dbConnection = null;

    public PlcSecureBootImpl(BundleContext ctx, PlcGeneralFunction plcGeneralFunction) {
        this.ctx = ctx;
        this.plcGeneralFunction = plcGeneralFunction;
    }
        
    @Override
    public void init() {
        if (null != dsFactory) {
            Properties props = new Properties();
            props.setProperty(DataSourceFactory.JDBC_URL, DB_URL);

            try {
                DataSource ds = dsFactory.createDataSource(props);
                dbConnection = ds.getConnection();
                if (null != dbConnection) {
                    var databaseMetaData = dbConnection.getMetaData();
                    LOGGER.info("Boot driver name is [%s].", databaseMetaData.getDriverName());
                    createTables();
                    //Catalog,Schema, Table pattern,types of tables
                    try(ResultSet resultSet = databaseMetaData.getTables(null, null, null, new String[]{"TABLE"})){ 
                      while(resultSet.next()) { 
                        String tableName = resultSet.getString("TABLE_NAME"); 
                        String remarks = resultSet.getString("REMARKS"); 
                      }
                    }                    
                    dbConnection.commit();
                    dbConnection.close();                    
                }
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        if (null != dbConnection) {
            try {
                dbConnection.close();
            } catch (SQLException ex) {
                LOGGER.info(ex.getMessage());
            }
        }    
    }

    @Override
    public void bindPlcDriver(PlcDriver plcDriver) {
        LOGGER.info("Loading driver: [%s].",plcDriver.getProtocolCode());
        if (null != dbConnection) {
            restore(plcDriver.getProtocolCode());
        } else {
            LOGGER.info("Delayed start of driver [%s].",plcDriver.getProtocolCode());
            delayedBootPlcDivers.put(plcDriver.getProtocolCode(), plcDriver);
        }
    }

    @Override
    public void unbindPlcDriver(PlcDriver plcDriver) {
        
    }
    
    @Override
    public void bindDataSourceFactory(DataSourceFactory dsFactory) {
        this.dsFactory = dsFactory;
        init();
    }  
    
    @Override
    public void execute(JobContext context) {
        boolean res = false;
        if (null != dbConnection) {
            if (!delayedBootPlcDivers.isEmpty()) {
                if (null != dbConnection) {
                    Set<String> keys = delayedBootPlcDivers.keySet();
                    for (String key:keys) {
                        res = restore(key);   
                        delayedBootPlcDivers.remove(key);                     
                    }
                }
            }            
        }        
    }

    @Override
    public void persist() {
        var plcDrivers = plcGeneralFunction.getPlcDrivers();
        plcDrivers.forEach( (k, d) -> store(k));
        ServiceReference ref = ctx.getServiceReference(EventAdmin.class.getName());
        if (ref != null){
            EventAdmin eventAdmin = (EventAdmin) ctx.getService(ref);
            Event eventPersist = new Event(EVENT_STORE, (Map) null); 
            eventAdmin.sendEvent(eventPersist);            
        }
    }

    
    @Override
    public void store(String plcDriver) {
        var plcDevices = plcGeneralFunction.getPlcDevices(plcDriver);
        plcDevices.forEach((duid, dname) ->{
            try {
                var plcDevice = plcGeneralFunction.getPlcDevice(duid);
                insertDevice(plcDriver, plcDevice);
                
                var plcGroups = plcGeneralFunction.getPlcDeviceGroups(duid);

                plcGroups.forEach((guid, gname) -> {
                    var plcGroup = plcGeneralFunction.getPlcGroup(guid);
                    try {
                        insertGroup(plcGroup);
                    } catch (SQLException ex) {
                        LOGGER.info(ex.getMessage());
                    }
                    var plcItems = plcGeneralFunction.getPlcGroupItems(guid);
                    plcItems.forEach((iuid, iname) -> {
                        var plcItem = plcGeneralFunction.getPlcItem(iuid);
                        try {
                            //Store item
                            insertItem(duid.toString(), guid.toString(), plcItem.get());
                        } catch (SQLException ex) {
                            LOGGER.info(ex.getMessage());
                        }
                    });
                });
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage());
            }
        
        });
    }
    
    @Override
    public boolean restore(String plcDriver) {
        boolean res = false;
        if (null != dbConnection) {        
            try {

                var stmt = dbConnection.createStatement();
                //PlcDevice
                var rsDevices = stmt.executeQuery(SQL_SELECT_DEVICES);                
                while (rsDevices.next()) {
                    String isDeviceEnable = rsDevices.getString("DeviceEnable");
                    Optional<PlcDevice> optPlcDevice = plcGeneralFunction.createDevice(
                                            rsDevices.getString("DeviceUuid"),
                                            rsDevices.getString("DriverName"),
                                            rsDevices.getString("DeviceKey"),
                                            rsDevices.getString("DeviceUrl"),
                                            rsDevices.getString("DeviceName"),
                                            rsDevices.getString("DeviceDescription"),
                                            rsDevices.getString("DeviceEnable"));
                    
                    if (optPlcDevice.isPresent()) {
                        LOGGER.info("Created PlcDevice [{}].", optPlcDevice.get().getDeviceKey());
                                
                        //PlcGroups
                        String queryGroups = SQL_SELECT_GROUPS.replace("?", optPlcDevice.get().getUid().toString());

                        var rsGroups = stmt.executeQuery(queryGroups);
                        while (rsGroups.next()) {
                            Optional<PlcGroup> optPlcGroup =  plcGeneralFunction.createGroup(
                                                rsGroups.getString("GroupUuid"),
                                                rsGroups.getString("DeviceUuid"),
                                                rsGroups.getString("GroupName"), 
                                                rsGroups.getString("GroupDescription"),
                                                rsGroups.getString("GroupScanTime"),
                                                rsGroups.getString("GroupEnable"));
                            
                            if (optPlcGroup.isPresent()) {
                                LOGGER.info("Created PlcGroup [{}].", optPlcGroup.get().getGroupName());
//                                String isGroupEnable = rsGroups.getString("GroupEnable");
//                                if (isGroupEnable.equals("true")) optPlcGroup.get().enable();
                                    
                                //PlcItems
                                String queryItems = SQL_SELECT_ITEMS.replace("?", optPlcGroup.get().getGroupUid().toString());   

                                var rsItems = stmt.executeQuery(queryItems);
                                while (rsItems.next()) {
                                    Optional<PlcItem> optPlcItem = plcGeneralFunction.createItem(
                                                    rsItems.getString("ItemUuid"),
                                                    rsItems.getString("GroupUuid"),
                                                    rsItems.getString("DeviceUuid"),
                                                    rsItems.getString("ItemName"),
                                                    rsItems.getString("ItemDescription"),
                                                    rsItems.getString("ItemTag"),
                                                    rsItems.getString("ItemEnable"));
                                    
                                   if (optPlcItem.isPresent())
                                        LOGGER.info("Created PlcItem [{}].", optPlcItem.get().getItemName());                               
                                }
                            }
                        }
                    }
                    
                    if (isDeviceEnable.equals("true")) optPlcDevice.get().enable();  
                                      
                }
                
                ServiceReference ref = ctx.getServiceReference(EventAdmin.class.getName());
                if (ref != null){                   
                    EventAdmin eventAdmin = (EventAdmin) ctx.getService(ref);
                    Event eventPersist = new Event(EVENT_RESTORE, (Map) null); 
                    eventAdmin.sendEvent(eventPersist);  
                    LOGGER.info("Allow clients to restore their state.");
                } 

                res = true;
                
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage());
            }
        } else {
            LOGGER.info("Database don't created.");
        }
       
        return res;
    }
        
    private void createTables() throws SQLException{
        Statement statement;
        statement = dbConnection.createStatement();
        
        statement.execute(SQL_CREATE_TABLE_DEVICES);
        statement.execute(SQL_CREATE_TABLE_GROUPS);        
        statement.execute(SQL_CREATE_TABLE_ITEMS);                 
    }
    
    private void insertDevice(String driverName, PlcDevice plcDevice) throws SQLException{
        if (null != dbConnection) {
            var query = dbConnection.prepareStatement(SQL_INSERT_DEVICE);
            query.setString(1, plcDevice.getUid().toString());
            query.setString(2, driverName);             
            query.setString(3, plcDevice.getDeviceKey());   
            query.setString(4, plcDevice.getUrl()); 
            query.setString(5, plcDevice.getDeviceName());  
            query.setString(6, plcDevice.getDeviceDescription());  
            query.setString(7, Boolean.toString(plcDevice.isEnable()));             
            query.setString(8,"");
            query.executeUpdate();
        }
    }
    
    private void insertGroup(PlcGroup plcGroup) throws SQLException{
        if (null != dbConnection) {
            var query = dbConnection.prepareStatement(SQL_INSERT_GROUP);
            query.setString(1, plcGroup.getGroupUid().toString());
            query.setString(2, plcGroup.getGroupDeviceUid().toString());             
            query.setString(3, plcGroup.getGroupName());   
            query.setString(4, plcGroup.getGroupDescription()); 
            query.setString(5, Long.toString(plcGroup.getPeriod()));  
            query.setString(6, Boolean.toString(plcGroup.isEnable()));             
            query.setString(7, "");    
            query.executeUpdate();
        }
    }  
    
    private void insertItem(String uuidDevice, String uuidGroup, PlcItem plcItem) throws SQLException{
        if (null != dbConnection) {
            var query = dbConnection.prepareStatement(SQL_INSERT_ITEM);
            query.setString(1, plcItem.getItemUid().toString());
            query.setString(2, uuidDevice);             
            query.setString(3, uuidGroup);   
            query.setString(4, plcItem.getItemName()); 
            query.setString(5, plcItem.getItemDescription());  
            query.setString(6, plcItem.getItemId()); 
            query.setString(7, Boolean.toString(plcItem.isEnable()));              
            query.setString(8, "");    
            query.executeUpdate();
        }
    }      
    
}
