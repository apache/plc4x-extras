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

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.listener.EventListener;
import org.osgi.service.dal.Function;

/**
 * This function performs a wrapper around 
 * the connection event subscription methods, 
 * typically "connected" and "disconnected".
 * 
 * Currently only the EIP and S7 drivers implement this interface.
 * 
 * It is considered provisional until drivers implement 
 * connection state management.
 */
public interface PlcEventConnectionFunction  extends Function {
    
    void addEventListener(PlcConnection plcConnection, EventListener listener);

    void removeEventListener(PlcConnection plcConnection, EventListener listener);    
}