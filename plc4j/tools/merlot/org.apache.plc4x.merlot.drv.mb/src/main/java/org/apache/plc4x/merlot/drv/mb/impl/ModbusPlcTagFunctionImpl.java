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
import java.util.HashMap;
import java.util.Map;
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
import org.apache.plc4x.java.modbus.readwrite.ModbusDataType;
import org.apache.plc4x.merlot.api.PlcTagFunction;
import org.osgi.framework.BundleContext;
import org.osgi.service.dal.OperationMetadata;
import org.osgi.service.dal.PropertyMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModbusPlcTagFunctionImpl implements PlcTagFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModbusPlcTagFunctionImpl.class);
    private BundleContext bc; 
    Map<String, String> config = new HashMap<>();
    int byteOffset = 0;
    int bitOffset = 0;
            
    public ModbusPlcTagFunctionImpl(BundleContext bc) {
        this.bc = bc;
    } 
    
    /*
    * MODBUS is a protocol oriented to data stored in bits or words, 
    * therefore the handling of individual bytes is considered a special case.
    * Byte reading is accomplished with the byteOffset over the read buffer, 
    * but a byte write is rejected since the process would overwrite its 
    * partner byte.
    * The user can read individual bytes, but must mask the write in a 
    * "short" for the write.
    * 
    */
    @Override
    public ImmutablePair<PlcTag, Object[]> getPlcTag(PlcTag plcTag, ByteBuf byteBuf, int byteOffset, byte bitOffset) {
        LOGGER.info("PlcTag class {} and type {} ", plcTag.getClass(),  plcTag.getPlcValueType());
        ModbusTag mbPlcTag = null;
        Object[] objValues = null;
        short tempValue = 0;
        if (plcTag instanceof ModbusTag){
            final ModbusTag mbTag = (ModbusTag) plcTag;         
            switch (mbTag.getPlcValueType()) { 
                case BOOL:   
                        objValues = new Object[byteBuf.capacity()];                    
                        byteOffset = mbTag.getAddress() + byteOffset;
                     
                        if (mbTag instanceof ModbusTagCoil) {
                            mbPlcTag = new ModbusTagCoil(
                                            byteOffset,
                                            byteBuf.capacity(),
                                            mbTag.getDataType(),
                                            config);                                
                        } else if (mbTag instanceof ModbusTagDiscreteInput) {
                            LOGGER.info("DiscreteInput does not allow writing.");
                            return null;
                        }
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity(); i++) {
                            objValues[i] = byteBuf.readBoolean();
                        }   

                    break;
                case UINT:  
                        objValues = new Object[byteBuf.capacity() / 2];                       
                        if (byteBuf.capacity() == 1) {
                            LOGGER.info("In MODBUS writing 'byte' types is rejected.");
                            return null;
                        }                     
                        byteOffset = mbTag.getAddress() + byteOffset / 2;                    
                        if (mbTag instanceof ModbusTagHoldingRegister) {                          
                            mbPlcTag = new ModbusTagHoldingRegister(
                                            byteOffset,
                                            byteBuf.capacity() / 2,
                                            ModbusDataType.INT,
                                            config);                                
                        } else if (mbTag instanceof ModbusTagInputRegister){
                            mbPlcTag = new ModbusTagInputRegister(
                                            byteOffset,
                                            byteBuf.capacity() / 2,
                                            ModbusDataType.INT,
                                            config);                            
                        } else if (mbTag instanceof  ModbusTagExtendedRegister) {
                           mbPlcTag = new ModbusTagExtendedRegister(
                                            byteOffset,
                                            byteBuf.capacity() / 2,
                                            ModbusDataType.INT,
                                            config);                            
                        }                       
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity() / 2; i++){
                            tempValue = (short) (byteBuf.readShort());                            
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
