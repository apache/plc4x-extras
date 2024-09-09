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
package org.apache.plc4x.merlot.drv.mb.impl;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.java.api.model.PlcTag;
import static org.apache.plc4x.java.api.types.PlcValueType.BOOL;
import static org.apache.plc4x.java.api.types.PlcValueType.BYTE;
import org.apache.plc4x.java.modbus.base.tag.ModbusTag;
import org.apache.plc4x.java.modbus.base.tag.ModbusTagCoil;
import org.apache.plc4x.java.modbus.base.tag.ModbusTagDiscreteInput;
import org.apache.plc4x.java.modbus.base.tag.ModbusTagExtendedRegister;
import org.apache.plc4x.java.modbus.base.tag.ModbusTagHoldingRegister;
import org.apache.plc4x.java.modbus.base.tag.ModbusTagInputRegister;
import org.apache.plc4x.merlot.api.PlcTagFunction;
import org.osgi.framework.BundleContext;
import org.osgi.service.dal.OperationMetadata;
import org.osgi.service.dal.PropertyMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModbusPlcTagFunctionImpl implements PlcTagFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModbusPlcTagFunctionImpl.class);
    private BundleContext bc; 
    
    int byteOffset = 0;
    int bitOffset = 0;
            
    public ModbusPlcTagFunctionImpl(BundleContext bc) {
        this.bc = bc;
    } 
    
    @Override
    public ImmutablePair<PlcTag, Object[]> getPlcTag(PlcTag plcTag, ByteBuf byteBuf, int offset) {
        LOGGER.info("PlcTag class {} and type {} ", plcTag.getClass(),  plcTag.getPlcValueType());
        ModbusTag mbPlcTag = null;
        short tempValue = 0;
        if (plcTag instanceof ModbusTag){
            final ModbusTag mbTag = (ModbusTag) plcTag;
            LOGGER.info("Processing ModbusTag: {}", mbTag.toString());
            Object[] objValues = new Object[byteBuf.capacity()];
            switch (mbTag.getPlcValueType()) { 
                case BOOL:           
                        byteOffset = mbTag.getAddress() + offset;
                        if (mbTag instanceof ModbusTagCoil) {
                            mbPlcTag = new ModbusTagCoil(
                                            byteOffset,
                                            byteBuf.capacity(),
                                            mbTag.getDataType(),
                                            null);                                
                        } else if (mbTag instanceof ModbusTagDiscreteInput) {
                            mbPlcTag = new ModbusTagCoil(
                                            byteOffset,
                                            byteBuf.capacity(),
                                            mbTag.getDataType(),
                                            null);                             
                        }
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity(); i++) {
                            objValues[i] = byteBuf.readBoolean();
                        }                        
                    break;
                case BYTE:  
                        byteOffset = mbTag.getAddress() + offset * 2;                    
                        if (mbTag instanceof ModbusTagHoldingRegister) {
                            mbPlcTag = new ModbusTagHoldingRegister(
                                            byteOffset,
                                            byteBuf.capacity(),
                                            mbTag.getDataType(),
                                            null);                              
                        } else if (mbTag instanceof ModbusTagInputRegister){
                            mbPlcTag = new ModbusTagInputRegister(
                                            byteOffset,
                                            byteBuf.capacity(),
                                            mbTag.getDataType(),
                                            null);                            
                        } else if (mbTag instanceof  ModbusTagExtendedRegister) {
                           mbPlcTag = new ModbusTagExtendedRegister(
                                            byteOffset,
                                            byteBuf.capacity(),
                                            mbTag.getDataType(),
                                            null);                            
                        }
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity(); i++){
                            tempValue = (short) (byteBuf.readByte() & 0xFF);                            
                            objValues[i] = tempValue;
                        }                                  
                    break;
                default:;
                
            }
            if (null != mbPlcTag)
            LOGGER.info("Writing tag : {}", mbPlcTag.toString());
            return new ImmutablePair<>(mbPlcTag, objValues);                  

        }        
        
        return null;
    }
    
   

    @Override
    public PropertyMetadata getPropertyMetadata(String propertyName) {
        throw new UnsupportedOperationException("Not supported yet."); 
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
    
}
