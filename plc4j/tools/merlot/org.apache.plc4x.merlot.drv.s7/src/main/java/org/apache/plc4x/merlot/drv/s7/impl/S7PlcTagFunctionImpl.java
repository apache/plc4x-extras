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
package org.apache.plc4x.merlot.drv.s7.impl;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.java.api.model.PlcTag;
import static org.apache.plc4x.java.api.types.PlcValueType.BOOL;
import static org.apache.plc4x.java.s7.readwrite.MemoryArea.DATA_BLOCKS;
import static org.apache.plc4x.java.s7.readwrite.MemoryArea.DIRECT_PERIPHERAL_ACCESS;
import static org.apache.plc4x.java.s7.readwrite.MemoryArea.FLAGS_MARKERS;
import static org.apache.plc4x.java.s7.readwrite.MemoryArea.INPUTS;
import static org.apache.plc4x.java.s7.readwrite.MemoryArea.OUTPUTS;
import org.apache.plc4x.java.s7.readwrite.tag.S7Tag;
import org.apache.plc4x.merlot.api.PlcTagFunction;
import org.osgi.framework.BundleContext;
import org.osgi.service.dal.OperationMetadata;
import org.osgi.service.dal.PropertyMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class S7PlcTagFunctionImpl implements PlcTagFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(S7PlcTagFunctionImpl.class);
    private BundleContext bc; 
    
    int byteOffset = 0;
    byte bitOffset = 0;
            
    public S7PlcTagFunctionImpl(BundleContext bc) {
        this.bc = bc;
    }   

    @Override
    public ImmutablePair<String, Object[]> getStringTag(PlcTag plcTag, ByteBuf byteBuf, int offset) {
        LOGGER.info("PlcTag class {} and type {} ", plcTag.getClass(),  plcTag.getPlcValueType());
        short tempValue = 0;
        if (plcTag instanceof S7Tag){
            final S7Tag s7Tag = (S7Tag) plcTag;
            LOGGER.info("Processing S7Tag: {}", s7Tag.toString()); 
            Object[] objValues = new Object[byteBuf.capacity()];
            StringBuilder strTagBuilder = new StringBuilder();
            switch (s7Tag.getPlcValueType()) { 
                case BOOL:
                        byteOffset = s7Tag.getByteOffset() + (offset / 8);
                        bitOffset = (byte) ((s7Tag.getBitOffset() + offset) % 8);
                        switch (s7Tag.getMemoryArea()){
                            case DATA_BLOCKS:;
                                strTagBuilder.append("%DB").
                                    append(s7Tag.getBlockNumber()).
                                    append(".DBX").
                                    append(byteOffset).
                                    append(".").
                                    append(bitOffset).
                                    append(":").
                                    append(s7Tag.getDataType().name()).
                                    append("[").
                                    append(byteBuf.capacity()).
                                    append("]");
                                break;
                            case DIRECT_PERIPHERAL_ACCESS:
                            case INPUTS:
                            case OUTPUTS:
                            case FLAGS_MARKERS:
                                strTagBuilder.append("%").
                                    append(s7Tag.getMemoryArea().getShortName()).
                                    append(s7Tag.getDataType().getDataTransportSize()).
                                    append(byteOffset).
                                    append(".").
                                    append(bitOffset).
                                    append(":").
                                    append(s7Tag.getDataType().name()).
                                    append("[").
                                    append(byteBuf.capacity()).
                                    append("]");                                        
                                break;
                            default:; 
                        }
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity(); i++){
                            objValues[i] = byteBuf.readBoolean();
                        }                        
                    break;
                case BYTE:  
                        byteOffset = s7Tag.getByteOffset() + offset * byteBuf.capacity();                    
                        switch (s7Tag.getMemoryArea()){
                            case DATA_BLOCKS:;
                                strTagBuilder.append("%DB").
                                    append(s7Tag.getBlockNumber()).
                                    append(".DBB").
                                    append(byteOffset).
                                    append(":").
                                    append(s7Tag.getDataType().name()).
                                    append("[").
                                    append(byteBuf.capacity()).
                                    append("]");                           
                                break;
                            case DIRECT_PERIPHERAL_ACCESS:
                            case INPUTS:
                            case OUTPUTS:
                            case FLAGS_MARKERS:
                                strTagBuilder.append("%").
                                    append(s7Tag.getMemoryArea().getShortName()).
                                    append(s7Tag.getDataType().getDataTransportSize()).
                                    append(byteOffset).
                                    append(":").
                                    append(s7Tag.getDataType().name()).
                                    append("[").
                                    append(byteBuf.capacity()).
                                    append("]");                                        
                                break;                                
                            default:; 
                        }
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity(); i++){
                            tempValue = (short) (byteBuf.readByte() & 0xFF);                            
                            objValues[i] = tempValue;
                        }                                  
                    break;
                default:;
                
            }
            LOGGER.info("Writing tag : {}",strTagBuilder.toString() );
            return new ImmutablePair<>(strTagBuilder.toString(), objValues);            
        }
        return null;
    }

    @Override
    public ImmutablePair<PlcTag, Object[]> getPlcTag(PlcTag plcTag, ByteBuf byteBuf, int offset) {
        LOGGER.info("PlcTag class {} and type {} ", plcTag.getClass(),  plcTag.getPlcValueType());
        short tempValue = 0;
        S7Tag s7PlcTag = null;
        if (plcTag instanceof S7Tag){
            final S7Tag s7Tag = (S7Tag) plcTag;
            LOGGER.info("Processing S7Tag: {}", s7Tag.toString()); 
            Object[] objValues = new Object[byteBuf.capacity()];
            switch (s7Tag.getPlcValueType()) { 
                case BOOL:                    
                        byteOffset = s7Tag.getByteOffset() + (offset / 8);
                        bitOffset = (byte) ((s7Tag.getBitOffset() + offset) % 8);
                        s7PlcTag = new S7Tag(s7Tag.getDataType(),
                                            s7Tag.getMemoryArea(),
                                            s7Tag.getBlockNumber(),
                                            byteOffset,
                                            bitOffset,
                                            byteBuf.capacity());
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity(); i++){
                            objValues[i] = byteBuf.readBoolean();
                        }                        
                    break;
                case BYTE:  
                        byteOffset = s7Tag.getByteOffset() + offset * byteBuf.capacity();                    
                        s7PlcTag = new S7Tag(s7Tag.getDataType(),
                                            s7Tag.getMemoryArea(),
                                            s7Tag.getBlockNumber(),
                                            byteOffset,
                                            (byte) 0,
                                            byteBuf.capacity());
                        byteBuf.resetReaderIndex();
                        for (int i=0; i < byteBuf.capacity(); i++){
                            tempValue = (short) (byteBuf.readByte() & 0xFF);                            
                            objValues[i] = tempValue;
                        }                                  
                    break;
                default:;
                
            }
            if (null != s7PlcTag)
            LOGGER.info("Writing tag : {}", s7PlcTag.toString());
            return new ImmutablePair<>(s7PlcTag, objValues);            
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
