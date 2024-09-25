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
package org.apache.plc4x.app.s7.impl;

import java.util.HashMap;
import java.util.Map;
import org.apache.plc4x.app.api.Plc4xMetaData;
import org.apache.plc4x.java.api.types.PlcValueType;


public enum S7MetaData implements Plc4xMetaData {
    
   
    NULL(PlcValueType.NULL,
            new String[] {  "A*.*", "A*.*_*",
                            "AB*", "AB*_*",
                            "AW*", "AW*_*",
                            "DB*.DBB*", "DB*.DBB*_*",
                            "DB*.DBD*", "DB*.DBD*_*",
                            "DB*.DBD*F", "DB*.DBB*F_*",
                            "DB*.DBW*", "DB*.DBW_*",   
                            "DB*.DBX*.*", "DB*.DBX*.*_*",
                            "E*.*", "E*.*_*",
                            "EB*", "EB*_*",
                            "EW*", "EW*_*",  
                            "I*.*", "I*.*_*",
                            "IB*", "IB*_*",
                            "IW*", "IW*_*",  
                            "M*.*", "M*.*_*", 
                            "M_DB*.DBB*_*",
                            "M_MB*_*", 
                            "M_MV*_*",
                            "M_SM*_*",
                            "M_VB*_*",
                            "MB*", "MB*_*",
                            "MD*", "MD*_*", 
                            "MD*F", "MD*F_*",
                            "MF*", "MF*_*", 
                            "MV*", "MV*_*",
                            "MW*", "MW*_*",
                            "Q*.*", "Q*.*_*",
                            "QB*", "QB*_*",
                            "QW*", "QW*_*",
                            "SM*", "SM*_*", 
                            "SM*.*", "SM*.*_*", 
                            "SMW*", "SMW*_*", 
                            "T*", "T*_*", 
                            "VB*", "VB*_*", 
                            "VB*.*", "VB*.*_*",  
                            "VD*", "VD*_*",
                            "VF*", "VF*_*",
                            "VW*", "VW*_*",
                            "Z*", "Z*_*"}),
    BOOL(PlcValueType.BOOL,
            new String[] {""}),
    BYTE(PlcValueType.BYTE,
            new String[] {""}),
    WORD(PlcValueType.WORD,
            new String[] {""}),
    DWORD(PlcValueType.DWORD,
            new String[] {""}),
    LWORD(PlcValueType.LWORD,
            new String[] {""}), 
    USINT(PlcValueType.USINT,
            new String[] {""}),
    UINT(PlcValueType.UINT,
            new String[] {""}),
    UDINT(PlcValueType.UDINT,
            new String[] {""}),
    ULINT(PlcValueType.ULINT,
            new String[] {""}),
    SINT(PlcValueType.SINT,
            new String[] {""}),
    INT(PlcValueType.INT,
            new String[] {""}),
    DINT(PlcValueType.DINT,
            new String[] {""}),
    LINT(PlcValueType.LINT,
            new String[] {""}),
    REAL(PlcValueType.REAL,
            new String[] {""}),
    LREAL(PlcValueType.LREAL,
            new String[] {""}),
    CHAR(PlcValueType.CHAR,
            new String[] {""}),
    WCHAR(PlcValueType.WCHAR,
            new String[] {""}),
    STRING(PlcValueType.STRING,
            new String[] {""}),
    WSTRING(PlcValueType.WSTRING,
            new String[] {""}),
    TIME(PlcValueType.TIME,
            new String[] {""}),
    LTIME(PlcValueType.LTIME,
            new String[] {""}),
    DATE(PlcValueType.DATE,
            new String[] {""}),
    LDATE(PlcValueType.LDATE,
            new String[] {""}),
    TIME_OF_DAY(PlcValueType.TIME_OF_DAY,
            new String[] {""}),
    LTIME_OF_DAY(PlcValueType.LTIME_OF_DAY,
            new String[] {""}),
    DATE_AND_TIME(PlcValueType.DATE_AND_TIME,
            new String[] {""}),
    LDATE_AND_TIME(PlcValueType.LDATE_AND_TIME,
            new String[] {""}),
    RAW_BYTE_ARRAY(PlcValueType.RAW_BYTE_ARRAY,
            new String[] {""});

  private static final Map<PlcValueType, S7MetaData> map;

  static {
    map = new HashMap<>();
    for (S7MetaData value : S7MetaData.values()) {
      map.put(value.getPlcType(), value);
    }
  }    

    
    private PlcValueType type;
    private String[]  ids;    
    
    S7MetaData(PlcValueType t, String... ids) {
        this.type = t;
        this.ids = ids;
    }    
    
    public PlcValueType getPlcType() {
        return type;
    }
    
    public String[] getIds() {
        return ids;
    }
    
    
    @Override
    public String[] getTypeIds(PlcValueType type) {
        return null;
    }

    @Override
    public String[] getAllIds() {
        return null;
    }

    @Override
    public PlcValueType getType(String type) {
        return null;
    }
    
}
