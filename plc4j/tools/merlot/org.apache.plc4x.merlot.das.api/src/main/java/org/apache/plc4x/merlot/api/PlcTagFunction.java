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
package org.apache.plc4x.merlot.api;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.java.api.model.PlcTag;
import org.osgi.service.dal.Function;

/**
 * This function is intended for the construction of the write Tags. 
 * It should expect a String representing a Tag or a PlcTag.
 * In the case of BYTE-oriented drivers such as Modbus or S7, 
 * priority should be given to writing bit arrays or byte arrays.
 * In the case of Tag-oriented drivers such as Ethernet/IP, 
 * handling should be done for each particular case. 
 * 
 */
public interface PlcTagFunction  extends Function {
        
    /*
    * PlcTag reference for constructing the String that represents 
    * the write tag.
    *
    * @param plcTag PlcTag reference PlcTag 
    * @paraf byteBuf ByteBuf
    * @paraf offset 
    * @return 
    */
    public ImmutablePair<String, Object[]> getStringTag(PlcTag plcTag, ByteBuf byteBuf, int offset);
    
    /*
    * PlcTag reference for constructing the String that represents 
    * the write tag.
    *
    * @param plcTag PlcTag reference PlcTag 
    * @paraf byteBuf ByteBuf
    * @paraf offset 
    * @return 
    */
    public ImmutablePair<PlcTag, Object[]> getPlcTag(PlcTag plcTag, ByteBuf byteBuf, int offset);    
    
}
