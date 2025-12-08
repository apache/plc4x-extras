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
package org.apache.plc4x.merlot.api.core;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.plc4x.java.api.types.PlcValueType;
import static org.apache.plc4x.java.api.types.PlcValueType.BOOL;
import static org.apache.plc4x.java.api.types.PlcValueType.BYTE;
import static org.apache.plc4x.java.api.types.PlcValueType.CHAR;
import static org.apache.plc4x.java.api.types.PlcValueType.DATE;
import static org.apache.plc4x.java.api.types.PlcValueType.DATE_AND_LTIME;
import static org.apache.plc4x.java.api.types.PlcValueType.DATE_AND_TIME;
import static org.apache.plc4x.java.api.types.PlcValueType.DINT;
import static org.apache.plc4x.java.api.types.PlcValueType.DWORD;
import static org.apache.plc4x.java.api.types.PlcValueType.INT;
import static org.apache.plc4x.java.api.types.PlcValueType.LDATE;
import static org.apache.plc4x.java.api.types.PlcValueType.LDATE_AND_TIME;
import static org.apache.plc4x.java.api.types.PlcValueType.LINT;
import static org.apache.plc4x.java.api.types.PlcValueType.LREAL;
import static org.apache.plc4x.java.api.types.PlcValueType.LTIME;
import static org.apache.plc4x.java.api.types.PlcValueType.LTIME_OF_DAY;
import static org.apache.plc4x.java.api.types.PlcValueType.LWORD;
import static org.apache.plc4x.java.api.types.PlcValueType.List;
import static org.apache.plc4x.java.api.types.PlcValueType.NULL;
import static org.apache.plc4x.java.api.types.PlcValueType.RAW_BYTE_ARRAY;
import static org.apache.plc4x.java.api.types.PlcValueType.REAL;
import static org.apache.plc4x.java.api.types.PlcValueType.SINT;
import static org.apache.plc4x.java.api.types.PlcValueType.STRING;
import static org.apache.plc4x.java.api.types.PlcValueType.Struct;
import static org.apache.plc4x.java.api.types.PlcValueType.TIME;
import static org.apache.plc4x.java.api.types.PlcValueType.TIME_OF_DAY;
import static org.apache.plc4x.java.api.types.PlcValueType.UDINT;
import static org.apache.plc4x.java.api.types.PlcValueType.UINT;
import static org.apache.plc4x.java.api.types.PlcValueType.ULINT;
import static org.apache.plc4x.java.api.types.PlcValueType.USINT;
import static org.apache.plc4x.java.api.types.PlcValueType.WCHAR;
import static org.apache.plc4x.java.api.types.PlcValueType.WORD;
import static org.apache.plc4x.java.api.types.PlcValueType.WSTRING;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcBOOL;
import org.apache.plc4x.java.spi.values.PlcBYTE;
import org.apache.plc4x.java.spi.values.PlcCHAR;
import org.apache.plc4x.java.spi.values.PlcDINT;
import org.apache.plc4x.java.spi.values.PlcDWORD;
import org.apache.plc4x.java.spi.values.PlcINT;
import org.apache.plc4x.java.spi.values.PlcLINT;
import org.apache.plc4x.java.spi.values.PlcLREAL;
import org.apache.plc4x.java.spi.values.PlcLWORD;
import org.apache.plc4x.java.spi.values.PlcList;
import org.apache.plc4x.java.spi.values.PlcREAL;
import org.apache.plc4x.java.spi.values.PlcSINT;
import org.apache.plc4x.java.spi.values.PlcUDINT;
import org.apache.plc4x.java.spi.values.PlcUINT;
import org.apache.plc4x.java.spi.values.PlcULINT;
import org.apache.plc4x.java.spi.values.PlcUSINT;
import org.apache.plc4x.java.spi.values.PlcWCHAR;
import org.apache.plc4x.java.spi.values.PlcWORD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cgarcia
 */
public class PlcStaticHelper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PlcStaticHelper.class);    
  
    /*
    *
    *
    */
    public static Optional<PlcValue> ByteBufToPlcValue(ByteBuf byteBuf, PlcValueType dataType) {
        short tempValue = 0; 
        List<PlcValue> values = null;
        Integer numberOfValues = 0;
        byteBuf.resetReaderIndex();
        switch (dataType) { 
            case NULL:  return Optional.empty();
                        
            case BOOL:
                        numberOfValues = byteBuf.capacity();
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcBOOL(byteBuf.readBoolean()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcBOOL(byteBuf.readBoolean()));
                            }                                 
                        }
                        break;                    
            case BYTE:
                        numberOfValues = byteBuf.capacity();
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcBYTE(byteBuf.readByte()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcBYTE(byteBuf.readByte()));
                            }                                  
                        }
                        break;                         
            case SINT:
                        numberOfValues = byteBuf.capacity();
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcSINT(byteBuf.readByte()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcSINT(byteBuf.readByte()));
                            }                                  
                        }
                        break;                    
            case USINT:
                        numberOfValues = byteBuf.capacity();
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcUSINT(byteBuf.readByte()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcUSINT(byteBuf.readByte()));
                            }                                  
                        }
                        break;   
            case INT:   
                        numberOfValues = byteBuf.capacity() / Short.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcINT(byteBuf.readShort()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcINT(byteBuf.readShort()));
                            }                                  
                        }
                        break;                                                                  
            case UINT:   
                        numberOfValues = byteBuf.capacity() / Short.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcUINT(byteBuf.readShort()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcUINT(byteBuf.readShort()));
                            }                                  
                        }
                        break;                 
            case WORD:    
                        numberOfValues = byteBuf.capacity() / Short.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcWORD(byteBuf.readShort()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcWORD(byteBuf.readShort()));
                            }                                  
                        }
                        break;   
            case DINT:    
                        numberOfValues = byteBuf.capacity() / Integer.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcDINT(byteBuf.readInt()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcDINT(byteBuf.readInt()));
                            }                                  
                        }
                        break; 
                
            case UDINT:    
                        numberOfValues = byteBuf.capacity() / Integer.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcUDINT(byteBuf.readInt()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcUDINT(byteBuf.readInt()));
                            }                                  
                        }
                        break;                  
            case DWORD:    
                        numberOfValues = byteBuf.capacity() / Integer.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcDWORD(byteBuf.readInt()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcDWORD(byteBuf.readInt()));
                            }                                  
                        }
                        break;   
            case LINT:    
                        numberOfValues = byteBuf.capacity() / Long.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcLINT(byteBuf.readLong()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcLINT(byteBuf.readLong()));
                            }                                  
                        }
                        break;      
            case ULINT:    
                        numberOfValues = byteBuf.capacity() / Long.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcULINT(byteBuf.readLong()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcULINT(byteBuf.readLong()));
                            }                                  
                        }
                        break;                 
            case LWORD:    
                        numberOfValues = byteBuf.capacity() / Long.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcLWORD(byteBuf.readLong()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcLWORD(byteBuf.readLong()));
                            }                                  
                        }
                        break;  
            case REAL:    
                        numberOfValues = byteBuf.capacity() / Float.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcREAL(byteBuf.readFloat()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcLREAL(byteBuf.readFloat()));
                            }                                  
                        }
                        break;                     
            case LREAL:    
                        numberOfValues = byteBuf.capacity() / Double.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcLREAL(byteBuf.readDouble()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcLREAL(byteBuf.readDouble()));
                            }                                  
                        }
                        break;                          
            case CHAR: 
                        numberOfValues = byteBuf.capacity();
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcCHAR(byteBuf.readChar()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcCHAR(byteBuf.readChar()));
                            }                                  
                        }
                        break;                 
            case WCHAR:
                        numberOfValues = byteBuf.capacity() / Short.BYTES; 
                        if (numberOfValues == 1) {
                            return Optional.of(new PlcWCHAR(byteBuf.readShort()));
                        } else {
                            values = new ArrayList<>(numberOfValues);
                            for (int i=0; i < numberOfValues; i++){
                                values.add(new PlcWCHAR(byteBuf.readShort()));
                            }                                  
                        }
                        break;                  
            case STRING: 
            case WSTRING:
            case TIME:
            case LTIME:
            case DATE: 
            case LDATE:
            case TIME_OF_DAY:
            case LTIME_OF_DAY:
            case DATE_AND_TIME:
            case DATE_AND_LTIME:
            case LDATE_AND_TIME:
            case Struct:
            case List:
            case RAW_BYTE_ARRAY:
            default:;                                    
        }  
        
    if (null == values) {
        return Optional.empty();
    }
    return Optional.of(new PlcList(values));
  }  
  
    /*
    *
    *
    */    
    public static void PlcValueToByteBuf(ByteBuf byteBuf, PlcValue value, PlcValueType dataType){
        Integer numberOfValues = 0;
        PlcValueType valueType = null;
        PlcList plcList = null;
        if (value instanceof PlcList){
            plcList = (PlcList) value;
            valueType = plcList.getIndex(0).getPlcValueType();
            numberOfValues = ((PlcList) value).getLength();
        } else {
            valueType = value.getPlcValueType();
            numberOfValues = 1;
        }
        byteBuf.resetReaderIndex();        
        switch (valueType) { 
            case NULL:  break;
                        
            case BOOL:
                        if (numberOfValues == 1) {
                            byteBuf.writeBoolean(value.getBoolean());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeBoolean(plcList.getIndex(i).getBoolean());
                            }                                 
                        }
                        break;                    
            case BYTE:
            case SINT:  
            case USINT:                
                        if (numberOfValues == 1) {
                            byteBuf.writeByte(value.getByte());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeByte(plcList.getIndex(i).getByte());
                            }                                 
                        }
                        break;                         
            case INT:  
            case UINT: 
            case WORD: 
                        if (numberOfValues == 1) {
                            byteBuf.writeShort(value.getShort());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeShort(plcList.getIndex(i).getShort());
                            }                                 
                        }
                        break;                                                               
            case DINT:
            case UDINT:  
            case DWORD:                  
                        if (numberOfValues == 1) {
                            byteBuf.writeInt(value.getInt());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeInt(plcList.getIndex(i).getInt());
                            }                                 
                        }
                        break;     
            case LINT:  
            case ULINT:   
            case LWORD:                 
                        if (numberOfValues == 1) {
                            byteBuf.writeLong(value.getLong());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeLong(plcList.getIndex(i).getLong());
                            }                                 
                        }
                        break;     
            case REAL:    
                        if (numberOfValues == 1) {
                            byteBuf.writeFloat(value.getFloat());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeFloat(plcList.getIndex(i).getFloat());
                            }                                 
                        }
                        break;                       
            case LREAL:    
                        if (numberOfValues == 1) {
                            byteBuf.writeDouble(value.getDouble());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeDouble(plcList.getIndex(i).getDouble());
                            }                                 
                        }
                        break;                            
            case CHAR: 
                        if (numberOfValues == 1) {
                            byteBuf.writeChar(value.getByte());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeChar(plcList.getIndex(i).getByte());
                            }                                 
                        }
                        break;                     
            case WCHAR:
                        if (numberOfValues == 1) {
                            byteBuf.writeShort(value.getShort());
                        } else {
                            for (int i=0; i < numberOfValues; i++){
                                byteBuf.writeShort(plcList.getIndex(i).getShort());
                            }                                 
                        }
                        break;                 
            case STRING: 
            case WSTRING:
            case TIME:
            case LTIME:
            case DATE: 
            case LDATE:
            case TIME_OF_DAY:
            case LTIME_OF_DAY:
            case DATE_AND_TIME:
            case DATE_AND_LTIME:
            case LDATE_AND_TIME:
            case Struct:
            case List:
            case RAW_BYTE_ARRAY:
            default:;                                    
        }          
  }  
    
    /*
    *
    *
    */    
    public static void WritePlcValue(ByteBuf byteBuf, PlcValue value, int index) {
        Integer byteIndex = 0;
        PlcValueType valueType = null;
        PlcList plcList = null;
        if (value instanceof PlcList){
            plcList = (PlcList) value;
            byteIndex = index * getPlcValueLength(plcList.getIndex(0));
            for (PlcValue plcValue:value.getList()) {
                byteBuf.writeBytes(plcValue.getRaw(), byteIndex, plcValue.getRaw().length);                
            }
        } else {
            byteIndex = index * getPlcValueLength(value);
            byteBuf.writeBytes(value.getRaw(), byteIndex, value.getRaw().length);
        }    
                
    }
    
    /*
    *
    *
    */    
    public static Optional<PlcValue> ReadPlcValue(ByteBuf byteBuf, PlcValueType valueType, int index){
        int byteIndex = 0 ;
        Integer numberOfValues = 0;
 
        switch (valueType) { 
            case NULL:  break;
                        
            case BOOL:  return Optional.of(new PlcBOOL(byteBuf.getBoolean(index)));                  
            
            case BYTE:  return Optional.of(new PlcBYTE(byteBuf.getByte(index))); 
            case SINT:  return Optional.of(new PlcSINT(byteBuf.getByte(index)));   
            case USINT: return Optional.of(new PlcUSINT(byteBuf.getByte(index)));                                        
            
            case INT:   return Optional.of(new PlcINT(byteBuf.getShort(index * Short.BYTES)));   
            case UINT:  return Optional.of(new PlcUINT(byteBuf.getShort(index * Short.BYTES)));
            case WORD:  return Optional.of(new PlcWORD(byteBuf.getShort(index * Short.BYTES)));                
            
            case DINT:  return Optional.of(new PlcDINT(byteBuf.getInt(index * Integer.BYTES)));   
            case UDINT: return Optional.of(new PlcUDINT(byteBuf.getInt(index * Integer.BYTES)));  
            case DWORD: return Optional.of(new PlcDWORD(byteBuf.getInt(index * Integer.BYTES)));                  
  
            case LINT:  return Optional.of(new PlcLINT(byteBuf.getLong(index * Long.BYTES)));   
            case ULINT: return Optional.of(new PlcULINT(byteBuf.getLong(index * Long.BYTES)));      
            case LWORD: return Optional.of(new PlcLWORD(byteBuf.getLong(index * Long.BYTES)));                    
   
            case REAL:  return Optional.of(new PlcREAL(byteBuf.getFloat(index * Float.BYTES)));    
            
            case LREAL: return Optional.of(new PlcLREAL(byteBuf.getDouble(index * Double.BYTES)));    
                        
            case CHAR:  return Optional.of(new PlcCHAR(byteBuf.getByte(index)));  
                 
            case WCHAR: return Optional.of(new PlcWCHAR(byteBuf.getShort(index * Short.BYTES))); 
               
            case STRING: 
            case WSTRING:
            case TIME:
            case LTIME:
            case DATE: 
            case LDATE:
            case TIME_OF_DAY:
            case LTIME_OF_DAY:
            case DATE_AND_TIME:
            case DATE_AND_LTIME:
            case LDATE_AND_TIME:
            case Struct:
            case List:
            case RAW_BYTE_ARRAY:
            default:;                                    
        }   

        return Optional.empty();
    }

    /*
    *
    *
    */        
    public static int getPlcValueLength(PlcValue value){
        int length = 0 ;
        Integer numberOfValues = 0;
        PlcValueType valueType = null;
        PlcList plcList = null;
        if (value instanceof PlcList){
            plcList = (PlcList) value;
            valueType = plcList.getIndex(0).getPlcValueType();
            numberOfValues = ((PlcList) value).getLength();
        } else {
            valueType = value.getPlcValueType();
            numberOfValues = 1;
        }    
        switch (valueType) { 
            case NULL:  break;
                        
            case BOOL:
                        length = 1;
                        break;                    
            case BYTE:
            case SINT:  
            case USINT:                
                        length = Byte.BYTES;
                        break;                         
            case INT:  
            case UINT:
            case WORD:
                        length = Short.BYTES;                        
                        break;                                                               
            case DINT:
            case UDINT:  
            case DWORD:                  
                        length = Integer.BYTES;
                        break;     
            case LINT:  
            case ULINT:   
            case LWORD:                 
                        length = Long.BYTES;
                        break;     
            case REAL:    
                        length = Float.BYTES;
                        break;                       
            case LREAL:    
                        length = Double.BYTES;
                        break;                            
            case CHAR: 
                        length = 1;
                        break;                     
            case WCHAR:
                        length = Short.BYTES;
                        break;                 
            case STRING: 
            case WSTRING:
            case TIME:
            case LTIME:
            case DATE: 
            case LDATE:
            case TIME_OF_DAY:
            case LTIME_OF_DAY:
            case DATE_AND_TIME:
            case DATE_AND_LTIME:
            case LDATE_AND_TIME:
            case Struct:
            case List:
            case RAW_BYTE_ARRAY:
            default:;                                    
        }   
        length = numberOfValues * length;
        return length;
    }
    
}
