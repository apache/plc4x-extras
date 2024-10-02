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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static final String EVENT_PERSIST = "org/apache/plc4x/merlot/PERSIST";   
    private static final String EVENT_RECOVER = "org/apache/plc4x/merlot/RECOVER";     
        
    private static final String SQL_CREATE_TABLE_DEVICES = 
            "CREATE TABLE IF NOT EXISTS Devices("
            + "DeviceUuId TEXT NOT NULL PRIMARY KEY,"
            + "DriverName TEXT,"            
            + "DeviceName TEXT,"
            + "DeviceId TEXT,"
            + "ShortName TEXT,"
            + "Description TEXT,"
            + "Md5 TEXT)";
    
    private static final String SQL_CREATE_TABLE_GROUPS = 
            "CREATE TABLE IF NOT EXISTS Groups("
            + "GroupUuid TEXT NOT NULL PRIMARY KEY,"
            + "DeviceUuid TEXT,"            
            + "GroupName TEXT,"
            + "GroupDescripcion TEXT,"
            + "GroupScantime TEXT,"
            + "Md5 TEXT)";  
    
    private static final String SQL_CREATE_TABLE_ITEMS = 
            "CREATE TABLE IF NOT EXISTS Items("
            + "ItemUuid TEXT NOT NULL PRIMARY KEY,"
            + "DeviceUuid TEXT,"
            + "GroupUuid TEXT,"
            + "ItemName TEXT,"
            + "ItemDescripcion TEXT,"
            + "ItemTag TEXT,"
            + "Md5 TEXT)";  
    
    private static final String SQL_SELECT_DEVICES = 
            "SELECT * FROM Devices WHERE DriverName = ?";
    
    private static final String SQL_SELECT_GROUPS = 
            "SELECT * FROM Groups WHERE DeviceId = ?";  
    
    private static final String SQL_SELECT_ITEMS = 
            "SELECT * FROM  WHERE GroupId = ?";     
    
    private static final String SQL_INSERT_DEVICE = 
            "INSERT INTO Devices(DeviceUuId, DriverName, DeviceName, DeviceId, ShortName, Description, Md5)"
            + "VALUES(?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(DeviceUuId) "
            + "DO "
            + "UPDATE SET "
            + "DriverName = excluded.DriverName, "
            + "DeviceName = excluded.DeviceName, "
            + "DeviceId =   excluded.DeviceId, "
            + "ShortName =  excluded.ShortName, "
            + "Description =excluded.Description, "
            + "Md5 = excluded.Md5;";
              
    
    private static final String SQL_INSERT_GROUP = 
            "INSERT INTO Groups(GroupUuid, DeviceUuid, GroupName, GroupDescripcion, GroupScantime, Md5)"
            + "VALUES(?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(GroupUuid) "
            + "DO "
            + "UPDATE SET "
            + "GroupUuid  =     excluded.GroupUuid, "
            + "DeviceUuid  =    excluded.DeviceUuid, "
            + "GroupName =      excluded.GroupName, "
            + "GroupDescripcion = excluded.GroupDescripcion, "
            + "GroupScantime    = excluded.GroupScantime, "
            + "Md5 = excluded.Md5;";
    
    private static final String SQL_INSERT_ITEM = 
            "INSERT INTO Items(ItemUuid, DeviceUuid, GroupUuid, ItemName, ItemDescripcion, ItemTag, Md5)"
            + "VALUES(?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(ItemUuid) "
            + "DO "
            + "UPDATE SET "
            + "ItemUuid = excluded.ItemUuid, "
            + "DeviceUuid = excluded.DeviceUuid, "
            + "GroupUuid = excluded.GroupUuid, "
            + "ItemName = excluded.ItemName, "
            + "ItemDescripcion  = excluded.ItemDescripcion, "
            + "ItemTag  = excluded.ItemTag, "            
            + "Md5 = excluded.Md5;";   
    
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
                    LOGGER.info("Boot driver name is {}.", databaseMetaData.getDriverName());
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
                LOGGER.info(ex.getMessage());
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
        LOGGER.info("Loading driver: {}",plcDriver.getProtocolCode());
        if (null != dbConnection) {
            
        } else {
            LOGGER.info("Delayed start of driver {}.",plcDriver.getProtocolCode());
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
        if (null != dbConnection) {
            if (!delayedBootPlcDivers.isEmpty()) {
                Set<String> keys = delayedBootPlcDivers.keySet();
                for (String key:keys) {
                    delayedBootPlcDivers.remove(key);
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
            Event eventPersist = new Event(EVENT_PERSIST, (Map) null); 
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
                System.out.println("Tamano de grupos: " + plcGroups.size());
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
                LOGGER.info(ex.getMessage());
            }
        
        });
    }
    
    @Override
    public void restore(String plcDriver) {

        ServiceReference ref = ctx.getServiceReference(EventAdmin.class.getName());
        if (ref != null){
            EventAdmin eventAdmin = (EventAdmin) ctx.getService(ref);
            Event eventPersist = new Event(EVENT_RECOVER, (Map) null); 
            eventAdmin.sendEvent(eventPersist);            
        }        
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
            query.setString(3, plcDevice.getDeviceName());   
            query.setString(4, plcDevice.getUid().toString()); 
            query.setString(5, "");  
            query.setString(6, plcDevice.getDeviceDescription());    
            query.setString(7,"");
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
            query.setString(5, Long.toOctalString(plcGroup.getPeriod()));  
            query.setString(6, "");    
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
            query.setString(5, plcItem.getItemId());             
            query.setString(6, "");    
            query.executeUpdate();
        }
    }      
    
}
