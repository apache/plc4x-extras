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
package org.apache.plc4x.merlot.db.api;



import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.plc4x.merlot.api.PlcItem;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdatabase.PVRecord;


public class DBRecord extends PVRecord  {   
    protected static final String MONITOR_FIELDS = "field(write_value,"+         
            "id,"+
            "offset,"+
            "description,"+
            "scan_time,"+  
            "scan_enable,"+
            "write_enable,"+               
            "display{limitLow,limitHigh},"+
            "control{limitLow,limitHigh,minStep})";      
    
    protected static final String MONITOR_VALUE_FIELD = "field(value)";
    protected static final String MONITOR_WRITE_FIELD = "field(write_value)"; 
    
    
    protected PlcItem plcItem = null; 
    protected ByteBuf innerBuffer = null; 
    protected ByteBuf innerWriteBuffer = null;     
    protected  int offset = 0;      
    
    public DBRecord(String recordName, PVStructure pvStructure) {
        super(recordName, pvStructure);
    }
    
    public Optional<PlcItem> getPlcItem(){
        if (null == plcItem) Optional.empty();
        return Optional.of(plcItem);
    };
    
    public Optional<ByteBuf> getInnerBuffer(){
        if (null == innerBuffer) Optional.empty();
        return Optional.of(innerBuffer);
    };  

    public Optional<ByteBuf> getWriteBuffer(){
        if (null == innerWriteBuffer) Optional.empty();
        return Optional.of(innerWriteBuffer);
    };    
    
    public int getOffset(){
        return offset;
    }
    
    public String getFieldsToMonitor(){
        return MONITOR_VALUE_FIELD;
    };
    
    
}
