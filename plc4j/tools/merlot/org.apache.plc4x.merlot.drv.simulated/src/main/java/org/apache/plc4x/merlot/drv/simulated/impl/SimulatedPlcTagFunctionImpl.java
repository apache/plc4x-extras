/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.drv.simulated.impl;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.java.api.model.PlcTag;
import static org.apache.plc4x.java.api.types.PlcValueType.BOOL;
import static org.apache.plc4x.java.api.types.PlcValueType.BYTE;
import static org.apache.plc4x.java.api.types.PlcValueType.DINT;
import static org.apache.plc4x.java.api.types.PlcValueType.INT;
import static org.apache.plc4x.java.api.types.PlcValueType.LINT;
import static org.apache.plc4x.java.api.types.PlcValueType.SINT;
import static org.apache.plc4x.java.api.types.PlcValueType.UDINT;
import static org.apache.plc4x.java.api.types.PlcValueType.UINT;
import static org.apache.plc4x.java.api.types.PlcValueType.ULINT;
import static org.apache.plc4x.java.api.types.PlcValueType.USINT;
import org.apache.plc4x.java.simulated.tag.SimulatedTag;
import org.apache.plc4x.merlot.api.PlcTagFunction;
import org.osgi.framework.BundleContext;
import org.osgi.service.dal.OperationMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
* 
*/
public class SimulatedPlcTagFunctionImpl implements PlcTagFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatedPlcTagFunctionImpl.class);
    private static final boolean PLC4X_TAG = true;
    private BundleContext bc;   
    
    public SimulatedPlcTagFunctionImpl(BundleContext bc) {
        this.bc = bc;
    }    
        
    /*
    *
    */
    private ImmutablePair<PlcTag, Object[]> getStringPlcTag(PlcTag plcTag, ByteBuf byteBuf, int byteOffset, byte bitOffset) {
        LOGGER.info("PlcTag class {} and type {} ", plcTag.getClass(),  plcTag.getPlcValueType());
        short tempValue = 0;
        if (plcTag instanceof SimulatedTag){
            final SimulatedTag simTag = (SimulatedTag) plcTag;
            LOGGER.info("Processing SimulatedTag: {}", simTag.toString());
            Object[] objValues = new Object[byteBuf.capacity()];
            StringBuilder strTagBuilder = new StringBuilder();             
            switch (simTag.getPlcValueType()) { 
                case BOOL:
                    strTagBuilder.append("STDOUT/").
                            append("merlot").
                            append(":BOOL[").
                            append(byteBuf.capacity()).
                            append("]");
                    byteBuf.resetReaderIndex();
                    for (int i=0; i < byteBuf.capacity(); i++){
                        objValues[i] = byteBuf.readBoolean();
                    }
                    break;
                case SINT:
                    strTagBuilder.append("STDOUT/").
                            append("merlot").
                            append(":SINT[").
                            append(byteBuf.capacity()).
                            append("]");
                    byteBuf.resetReaderIndex();
                    for (int i=0; i < byteBuf.capacity(); i++){
                        tempValue = (short) (byteBuf.readByte() & 0xFF);
                        objValues[i] = tempValue;
                    }                   
                    break;
                default:;                                    
            }
            LOGGER.info("Writing tag : {}",strTagBuilder.toString());
            return new ImmutablePair<>(SimulatedTag.of(strTagBuilder.toString()), objValues);
        }
        return null; 
    }  

    
    /*
    * getPlc4xPlcTag: This function creates the PlcTag for writing to a 
    + specific memory area in the "simulated" driver.
    * 
    * The "simulated" driver addresses three specific memory areas, namely:    
    * STATE -   This holds in memory a value for a given alias. 
    *           This value can be read or written to, however this should 
    *           only be used in conjunction with a persistent connection. 
    *           Once the connection is closed the memory area is cleared.
    * RANDOM -  This provides a new random value for each read. When writing, 
    *           a log message is recorded and the value is discarded.
    * STDOUT -  Always returns a null value when reading. 
    *           When writing, a log message is recorded and the value is 
    *           discarded.
    * You must pay attention to write behavior.
    * It is useful for testing higher-level data structures.
    *
    * TODO: Change constructor of SimulatedTag to public.
    * TODO: Add getNumElements for the number of elements.
    */
    private ImmutablePair<PlcTag, Object[]> getPlc4xPlcTag(PlcTag plcTag, ByteBuf byteBuf, int byteOffset, byte bitOffset) {
        LOGGER.info("PlcTag class {} and type {} ", plcTag.getClass(),  plcTag.getPlcValueType());
        short tempValue = 0;
        SimulatedTag simPlcTag = null;
        if (plcTag instanceof SimulatedTag){
            final SimulatedTag simTag = (SimulatedTag) plcTag;
            LOGGER.info("Processing SimulatedTag: {}", simTag.toString());            
            Object[] objValues = new Object[byteBuf.capacity()];
            simPlcTag = SimulatedTag.of(simTag.getAddressString());            
            byteBuf.resetReaderIndex();
            switch (simTag.getPlcValueType()) { 
                case NULL: break;
                case BOOL:
                            for (int i=0; i < byteBuf.capacity(); i++){
                                objValues[i] = byteBuf.readBoolean();
                            }
                            break;                    
                case BYTE:
                case SINT:  
                case USINT:                    
                            for (int i=0; i < byteBuf.capacity(); i++){
                                tempValue = (short) (byteBuf.readByte() & 0xFF);
                                objValues[i] = tempValue;
                            }                   
                            break;
                case INT:  
                case UINT:                
                case WORD: 
                            for (int i=0; i < byteBuf.capacity() / Short.BYTES; i++){
                                objValues[i] =(short) (byteBuf.readShort() & 0xFFFF);
                            }                   
                            break;   
                case DINT:  
                case UDINT:                 
                case DWORD: 
                            for (int i=0; i < byteBuf.capacity() / Integer.BYTES; i++){
                                objValues[i] = byteBuf.readInt();
                            }                   
                            break;  
                case LINT:   
                case ULINT:                
                case LWORD:
                            for (int i=0; i < byteBuf.capacity() / Long.BYTES; i++){
                                objValues[i] = byteBuf.readLong();
                            }
                            break;
                case REAL:
                            for (int i=0; i < byteBuf.capacity() / Float.BYTES; i++){
                                objValues[i] = byteBuf.readFloat();
                            }
                            break;                    
                case LREAL:
                            for (int i=0; i < byteBuf.capacity() / Double.BYTES; i++){
                                objValues[i] = byteBuf.readDouble();
                            }
                            break;                        
                case CHAR:
                            for (int i=0; i < byteBuf.capacity(); i++){
                                objValues[i] = byteBuf.readChar();
                            }
                            break;                 
                case WCHAR:
                            for (int i=0; i < byteBuf.capacity() / Short.BYTES; i++){
                                objValues[i] = byteBuf.readShort();
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
            if (null != simPlcTag)
                LOGGER.info("Writing tag : {}", simPlcTag.toString() );
            return new ImmutablePair<>(simPlcTag, objValues);
        }
        return null; 
    }
    
    @Override
    public ImmutablePair<PlcTag, Object[]> getPlcTag(PlcTag plcTag, ByteBuf byteBuf, int byteOffset, byte bitOffset) {
        if (!PLC4X_TAG) {
            return getStringPlcTag(plcTag, byteBuf, byteOffset, bitOffset);
        } else {
            return getPlc4xPlcTag(plcTag, byteBuf, byteOffset, bitOffset);            
        }
    }    
    
   
    @Override
    public OperationMetadata getOperationMetadata(String operationName) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Object getServiceProperty(String propKey) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String[] getServicePropertyKeys() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public org.osgi.service.dal.PropertyMetadata getPropertyMetadata(String propertyName) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }






    
}
