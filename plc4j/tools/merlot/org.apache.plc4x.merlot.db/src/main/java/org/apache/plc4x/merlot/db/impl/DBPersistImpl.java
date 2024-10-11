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
import java.sql.Statement;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.plc4x.merlot.api.PlcGeneralFunction;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.api.PlcSecureBoot;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.api.DBRecordFactory;
import org.apache.plc4x.merlot.db.api.DBWriterHandler;
import org.epics.pvdata.pv.PVScalar;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVRecord;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.jdbc.DataSourceFactory;
import org.slf4j.LoggerFactory;

public class DBPersistImpl implements EventHandler{    
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DBPersistImpl.class);
    private static final String DB_URL = "jdbc:sqlite:data/boot.db";

    private static final String SQL_CREATE_TABLE_PVRECORDS = 
            "CREATE TABLE IF NOT EXISTS PvRecords("
            + "PvUuId TEXT NOT NULL PRIMARY KEY,"
            + "PvName TEXT,"            
            + "PvType TEXT,"
            + "PvId TEXT,"
            + "PvOffset TEXT,"
            + "PvDescriptor TEXT,"
            + "PvScanTime TEXT,"    
            + "PvScanEnable TEXT," 
            + "PvWriteEnable TEXT,"                                              
            + "PvDisplayLimitLow TEXT,"             
            + "PvDisplayLimitHigh TEXT,"  
            + "PvDisplayDescription TEXT,"  
            + "PvDisplayFormat TEXT,"  
            + "PvDisplayUnits TEXT,"   
            + "PvControlLimitLow TEXT," 
            + "PvControlLimitHigh TEXT,"  
            + "PvControlMinStep TEXT,"             
            + "Md5 TEXT)";

    private static final String SQL_SELECT_PVRECORDS = 
            "SELECT * FROM PvRecords WHERE PvName = ?";
    
    private static final String SQL_INSERT_PVRECORDS  = 
            "INSERT INTO Devices(PvUuId, PvName, PvType, PvId, PvOffset, PvDescriptor, PvScanTime, pvScanEnable,"
            + "PvWriteEnable, PvDisplayLimitLow, PvDisplayLimitHigh, PvDisplayDescription, PvDisplayFormat,"
            + "PvDisplayUnits, PvControlLimitLow, PvControlLimitHigh, PvControlMinStep, Mmd5)"
            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(PvUuId) "
            + "DO "
            + "UPDATE SET "
            + "PvName =         excluded.PvName, "
            + "PvType =         excluded.PvType, "
            + "PvId =           excluded.PvId, "
            + "PvOffset =       excluded.PvOffset, "
            + "PvDescriptor =   excluded.PvDescriptor, "
            + "PvScanTime =     excluded.PvScanRate, "            
            + "PvScanEnable =   excluded.PvScanEnable, "            
            + "PvWriteEnable =  excluded.PvWriteEnable, "            
            + "PvDisplayLimitLow =      excluded.PvDisplayLimitLow, "            
            + "PvDisplayLimitHigh =     excluded.PvDisplayLimitHigh, "                        
            + "PvDisplayDescription =   excluded.PvDisplayDescription, "                        
            + "PvDisplayFormat =        excluded.PvDisplayFormat, " 
            + "PvDisplayUnits =         excluded.PvDisplayUnits, " 
            + "PvControlLimitLow =      excluded.PvControlLimitLow, " 
            + "PvControlLimitHigh =     excluded.PvControlLimitHigh, "             
            + "PvControlMinStep =       excluded.PvControlMinStep, "             
            + "Md5 = excluded.Md5;";    
         
    private final BundleContext bc;
    private final PVDatabase master;
    private final PlcGeneralFunction plcGeneralFunction;
    private final DBWriterHandler writerHandler;
    DataSourceFactory dsFactory = null;
    Connection dbConnection = null;    

    public DBPersistImpl(BundleContext bc, 
            PVDatabase master, 
            PlcGeneralFunction plcGeneralFunction,
            DBWriterHandler writerHandler) {
        this.bc = bc;
        this.master = master;
        this.plcGeneralFunction = plcGeneralFunction;
        this.writerHandler = writerHandler;
    }
                
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
        if (event.getTopic().equals(PlcSecureBoot.EVENT_STORE)) {
            try {
                store();
            } catch (Exception ex){
                LOGGER.info(ex.getMessage());
            }
        } else if (event.getTopic().equals(PlcSecureBoot.EVENT_RESTORE)) {
            try {
                restore();
            } catch (Exception ex){
                LOGGER.info(ex.getMessage());
            }            
        }
    }
    
    private void createTables() throws SQLException {
        Statement statement;
        statement = dbConnection.createStatement();        
        statement.execute(SQL_CREATE_TABLE_PVRECORDS);     
    } 
    
    public void store() throws SQLException {
        String[] pvNames = master.getRecordNames();
        for (String pvName:pvNames){
            final PVRecord pvRecord = master.findRecord(pvName);
            insertPvRecord(pvRecord);
        }
    }    
    
    public void restore() throws SQLException, InvalidSyntaxException {
        if (null != dbConnection) {
            var stmt = dbConnection.createStatement(); 
            var rs = stmt.executeQuery(SQL_SELECT_PVRECORDS);
            while (rs.next()) {
                ServiceReference[] refs = bc.getServiceReferences(DBRecordFactory.class.getName(), "(db.record.type="+rs.getString("pvType")+")");
                if (null != refs) {
                    final DBRecordFactory recordFactory = (DBRecordFactory) bc.getService(refs[0]);
                    PVRecord pvRecord = recordFactory.create(rs.getString("PvName"));
                    pvRecord.getPVStructure().getStringField("id").put(rs.getString("PvId"));
                    pvRecord.getPVStructure().getStringField("descriptor").put(rs.getString("PvDescriptor"));                    
                    pvRecord.getPVStructure().getStringField("scan_time").put(rs.getString("pvScanTime")); 
                    pvRecord.getPVStructure().getBooleanField("scan_enable").put(Boolean.parseBoolean(rs.getString("PvScanEnable")));
                    pvRecord.getPVStructure().getBooleanField("write_enable").put(rs.getBoolean("PvWriteEnable"));
                    pvRecord.getPVStructure().getDoubleField("display.limitLow").put(rs.getDouble("PvDisplayLimitLow"));
                    pvRecord.getPVStructure().getDoubleField("display.limitHigh").put(rs.getDouble("PvDisplayLimitHigh")); 
                    pvRecord.getPVStructure().getStringField("display.description").put(rs.getString("PvDisplayDescription"));  
                    pvRecord.getPVStructure().getStringField("display.format").put(rs.getString("PvDisplayFormat")); 
                    pvRecord.getPVStructure().getStringField("display.units").put(rs.getString("PvDisplayUnits")); 
                    pvRecord.getPVStructure().getDoubleField("control.limitLow").put(rs.getDouble("PvControlLimitLow")); 
                    pvRecord.getPVStructure().getDoubleField("control.limitHigh").put(rs.getDouble("PvControlLimitHigh")); 
                    pvRecord.getPVStructure().getDoubleField("control.minStep").put(rs.getDouble("PvControlMinStep"));   
                    
                    //Talk to PLC4X
                    Optional<PlcItem> plcItem = plcGeneralFunction.getPlcItem(rs.getString("PvId"));
                    if (plcItem.isPresent()) {
                        plcItem.get().addItemListener((PlcItemListener) pvRecord);
                        master.addRecord(pvRecord);
                        writerHandler.putDBRecord((DBRecord) pvRecord);
                    } else {
                        LOGGER.error("PlcItem [?] don't exist.", rs.getString("PvId"));
                    }
                }
            }
        }        
    }    
    
    private void insertPvRecord(PVRecord pvRecord) throws SQLException{
        if (null != dbConnection) {
            var query = dbConnection.prepareStatement(SQL_INSERT_PVRECORDS);
            
            PVScalar value = (PVScalar) pvRecord.getPVStructure().getSubField("value");
            
            query.setString(1, UUID.randomUUID().toString());
            query.setString(2, pvRecord.getRecordName());             
            query.setString(3, value.getScalar().getScalarType().name());
            query.setString(4, pvRecord.getPVStructure().getSubField("id").toString()); 
            query.setString(5, pvRecord.getPVStructure().getSubField("offset").toString());  
            query.setString(6, pvRecord.getPVStructure().getSubField("descriptor").toString()); 
            query.setString(7, pvRecord.getPVStructure().getSubField("scan_time").toString());     
            query.setString(8, pvRecord.getPVStructure().getSubField("scan_enable").toString());   
            query.setString(9, pvRecord.getPVStructure().getSubField("write_enable").toString());                                            
            query.setString(10, pvRecord.getPVStructure().getSubField("display.limitLow").toString()); 
            query.setString(11, pvRecord.getPVStructure().getSubField("display.limitHigh").toString()); 
            query.setString(12, pvRecord.getPVStructure().getSubField("display.description").toString()); 
            query.setString(13, pvRecord.getPVStructure().getSubField("display.format").toString());
            query.setString(14, pvRecord.getPVStructure().getSubField("display.units").toString());  
            query.setString(15, pvRecord.getPVStructure().getSubField("control.limitLow").toString());  
            query.setString(16, pvRecord.getPVStructure().getSubField("control.limitHigh").toString());  
            query.setString(17, pvRecord.getPVStructure().getSubField("control.minStep").toString());                          
            query.executeUpdate();            
        }
    }    



    
}
