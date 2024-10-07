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
package org.apache.plc4x.merlot.db.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.jdbc.DataSourceFactory;
import org.slf4j.LoggerFactory;

public class DBPersistImpl implements EventHandler{    
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DBPersistImpl.class);
    private static final String DB_URL = "jdbc:sqlite:data/boot.db";
    private static final String EVENT_PERSIST = "org/apache/plc4x/merlot/PERSIST";   
    private static final String EVENT_RECOVER = "org/apache/plc4x/merlot/RECOVER"; 

    private static final String SQL_CREATE_TABLE_PVRECORDS = 
            "CREATE TABLE IF NOT EXISTS PvRecords("
            + "DeviceUuId TEXT NOT NULL PRIMARY KEY,"
            + "DriverName TEXT,"            
            + "DeviceName TEXT,"
            + "DeviceId TEXT,"
            + "ShortName TEXT,"
            + "Description TEXT,"
            + "Enable TEXT,"            
            + "Md5 TEXT)";

    private static final String SQL_SELECT_PVRECORDS = 
            "SELECT * FROM PvRecords WHERE DriverName = ?";
    
    private static final String SQL_INSERT_PVRECORDS  = 
            "INSERT INTO Devices(DeviceUuId, DriverName, DeviceName, DeviceId, ShortName, Description, Enable, Md5)"
            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(DeviceUuId) "
            + "DO "
            + "UPDATE SET "
            + "DriverName = excluded.DriverName, "
            + "DeviceName = excluded.DeviceName, "
            + "DeviceId =   excluded.DeviceId, "
            + "ShortName =  excluded.ShortName, "
            + "Description =excluded.Description, "
            + "Md5 = excluded.Md5;";    
                    
    DataSourceFactory dsFactory = null;
    Connection dbConnection = null;    
    
    public void init() {
        LOGGER.info("INIT");  
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
    
    public void destroy() {
        LOGGER.info("DESTROY");        
    }    
    
    public void bindDataSourceFactory(DataSourceFactory dsFactory) {
        this.dsFactory = dsFactory;
        init();
    }      
    
    
    @Override
    public void handleEvent(Event event) {
        LOGGER.info("HANDLE EVENT");
    }
    
    private void createTables() throws SQLException{
        LOGGER.info("CREATE TABLES");        
    }    
    
}
