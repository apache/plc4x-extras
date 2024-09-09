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

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.listener.EventListener;
import org.apache.plc4x.java.s7.readwrite.protocol.S7HPlcConnection;
import org.apache.plc4x.merlot.api.PlcEventConnectionFunction;
import org.osgi.service.dal.OperationMetadata;
import org.osgi.service.dal.PropertyMetadata;


public class S7PlcEventConnectionFunctionImpl implements PlcEventConnectionFunction {

    public S7PlcEventConnectionFunctionImpl() {
    }

    @Override
    public void addEventListener(PlcConnection plcConnection, EventListener listener) {
        if (plcConnection instanceof S7HPlcConnection) {
            ((S7HPlcConnection) plcConnection).addEventListener(listener);
        }
    }

    @Override
    public void removeEventListener(PlcConnection plcConnection, EventListener listener) {
        if (plcConnection instanceof S7HPlcConnection) {
            ((S7HPlcConnection) plcConnection).removeEventListener(listener);
        }
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
