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
package org.apache.plc4x.merlot.db.core;

import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.api.DBRecordFactory;
import org.epics.pvdata.copy.CreateRequest;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVDouble;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdatabase.PVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: Replace string processing with with regex.
public class DBBaseFactory implements DBRecordFactory  {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBBaseFactory.class); 
    
    @Override
    public DBRecord create(String recordName) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DBRecord createArray(String recordName, int length) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DBRecord create(String recordName, String[] fields) {
        
        DBRecord dbRecord = null;
        PVStructure structure = null;
        PVString pvString = null;
        PVBoolean pvBoolean = null;
        PVDouble pvDouble = null;
        boolean isArray = false;
        int lengthArray = 0;     
        
        try {
            if (fields.length < 6) return null;
            
            if (!fields[0].isEmpty()) {        
                int start = fields[0].indexOf('[')+1;
                int end = fields[0].indexOf(']');
                if ((start>0) && (end!=-1)){
                    String strLength = fields[0].substring(start, end);
                    lengthArray = Integer.parseInt(strLength);
                    isArray = true;
                }

                if (!isArray){
                    dbRecord = create(recordName);
                } else {
                    dbRecord = createArray(recordName,lengthArray);
                }

                structure = dbRecord.getPVStructure();
                pvString = structure.getStringField("id");
                pvString.put(fields[1]);   
            } else return null;

            if (!fields[2].isEmpty()) {          
                PVInt pvInt = structure.getIntField("offset");
                pvInt.put(Integer.parseInt(fields[2]));
            } else return null;             
            
            if (!fields[3].isEmpty()) {          
                pvString = structure.getStringField("descriptor");
                pvString.put(fields[3]);
            } else return null;

            //TODO: Agregar el offset e incrementar indice en +1
            
            
            
            if (!fields[4].isEmpty()) {          
                pvString = structure.getStringField("scan_time");
                pvString.put(fields[4]);
            } else return null;   

            if (!fields[5].isEmpty()) {          
                pvBoolean = structure.getBooleanField("scan_enable");
                pvBoolean.put(Boolean.parseBoolean(fields[5]));
            } else return null; 

            if (!fields[6].isEmpty()) {          
                pvBoolean = structure.getBooleanField("write_enable");
                pvBoolean.put(Boolean.parseBoolean(fields[6]));
            } else return null;                 

            if (!fields[7].isEmpty()) {          
                pvDouble = structure.getDoubleField("display.limitLow");
                pvDouble.put(Double.parseDouble(fields[7]));
            }           

            if (!fields[8].isEmpty()) {          
                pvDouble = structure.getDoubleField("display.limitHigh");
                pvDouble.put(Double.parseDouble(fields[8]));
            }    

            if (!fields[9].isEmpty()) {          
                pvString = structure.getStringField("display.description");
                pvString.put(fields[9]);
            }    

            if (!fields[10].isEmpty()) {          
                pvString = structure.getStringField("display.format");
                pvString.put(fields[10]);
            }      

            if (!fields[11].isEmpty()) {          
                pvString = structure.getStringField("display.units");
                pvString.put(fields[11]);
            }   

            if (!fields[12].isEmpty()) {          
                pvDouble = structure.getDoubleField("control.limitLow");
                pvDouble.put(Double.parseDouble(fields[12]));
            }   

            if (!fields[13].isEmpty()) {          
                pvDouble = structure.getDoubleField("control.limitHigh");
                pvDouble.put(Double.parseDouble(fields[13]));
            }   

            if (!fields[14].isEmpty()) {          
                pvDouble = structure.getDoubleField("control.minStep");
                pvDouble.put(Double.parseDouble(fields[14]));
            }           
            
            return dbRecord;
        } catch (Exception ex) {
            ex.printStackTrace();
            LOGGER.info(ex.toString());
        }
        return null;
    }
    
}
