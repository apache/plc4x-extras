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
package org.apache.plc4x.malbec.api;

/* 
 *
 */
public enum Plc4xPropertyEnum {
    
    //Commons fields in dialogs
    NAME,
    DESCRIPTION,
    SCANTIME,
    ENABLE,
    DISABLE_OUTPUT,
    
    //Serial connection parameters
    SERIAL_PORT,
    BAUD_RATE,
    DATA_BITS,
    STOP_BITS,
    PARITY,
    
    //TCP or UDP Connections parameters
    IS_TCP,
    IS_UDP,
    IS_REDUNDANCE,
    TCP_MAIN,
    TCP_SECONDARY,
    PORT_MAIN,
    PORT_SECONDARY,
    
    //Common parameters
    TIMEOUT,
    
    //Driver Events
    ADD_DRIVER,
    REMOVE_DRIVER,
    
    //Device Events
    ADD_DEVICE,
    REMOVE_DEVICE,
    
    //TagGroups Events
    ADD_TAGGROUP,
    REMOVE_TAGGROUP,
    
    //Tag Events
    ADD_TAG,
    REMOVE_TAG;    
    
    
}
