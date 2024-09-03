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
import org.apache.plc4x.java.simulated.tag.SimulatedTag;
import org.apache.plc4x.merlot.api.PlcTagFunction;
import org.osgi.framework.BundleContext;
import org.osgi.service.dal.OperationMetadata;
import org.osgi.service.dal.PropertyMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SimulatedPlcTagFunctionImpl implements PlcTagFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatedPlcTagFunctionImpl.class);
    private BundleContext bc;   
    
    public SimulatedPlcTagFunctionImpl(BundleContext bc) {
        this.bc = bc;
    }    
        
    /*
    *
    */
    @Override
    public  ImmutablePair<String, Object[]> getStringTag(PlcTag plcTag, ByteBuf byteBuf, int offset) {
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
                case BYTE:
                    strTagBuilder.append("STDOUT/").
                            append("merlot").
                            append(":BYTE[").
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
            LOGGER.info("Writing tag : {}",strTagBuilder.toString() );
            return new ImmutablePair<>(strTagBuilder.toString(), objValues);
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
