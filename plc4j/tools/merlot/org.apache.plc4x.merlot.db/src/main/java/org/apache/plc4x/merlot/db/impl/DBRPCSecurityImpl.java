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
package org.apache.plc4x.merlot.db.impl;

import org.epics.nt.NTScalar;
import org.epics.nt.NTScalarBuilder;
import org.epics.pvaccess.server.rpc.RPCRequestException;
import org.epics.pvaccess.server.rpc.RPCService;
import org.epics.pvaccess.server.rpc.Service;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVLong;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Status;
import org.epics.pvdatabase.PVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DBRPCSecurityImpl extends PVRecord implements RPCService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBRPCSecurityImpl.class);
    private static final String RPC_NAME = "security";    
    
    private PVStructure pvTop;    
    private long request_counter = 0;    
    
    public static DBRPCSecurityImpl create() {
        FieldCreate fieldCreate = FieldFactory.getFieldCreate();
        PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();

        PVStructure pvTop = ntScalarBuilder
                .value(ScalarType.pvLong)
                .add("res", fieldCreate.createScalarArray(ScalarType.pvString))
                .addTimeStamp()
                .createPVStructure();

        DBRPCSecurityImpl pvRecord = new DBRPCSecurityImpl(RPC_NAME, pvTop);     
        return pvRecord;
    }    
    
    private DBRPCSecurityImpl(String recordName, PVStructure pvStructure) {
        super(recordName, pvStructure);
        this.pvTop = pvStructure;
    }    

    @Override
    public Service getService(PVStructure pvRequest) {
        System.out.println("GETSERVICE");         
        return this;
    }
        
    @Override
    public PVStructure request(PVStructure pvs) throws RPCRequestException {
        System.out.println("REQUEST");         
        System.out.println("Request: " + pvs.toString());
        PVString pvOp = pvs.getSubField(PVString.class, "op");
        PVString pvQuery = pvs.getSubField(PVString.class, "query");  
        
        if (pvOp == null) {
            throw new RPCRequestException(Status.StatusType.ERROR,
                    "PVString field with name 'op' expected.");
        }
        if (pvQuery == null) {
            throw new RPCRequestException(Status.StatusType.ERROR,
                    "PVString field with name 'query' expected.");
        }        
        return execute(pvOp, pvQuery);
    }

    @Override
    public void process() {
        super.process();
        System.out.println("PROCESS");        
        request_counter++;
        PVLong pvCounter = pvTop.getSubField(PVLong.class, "value");
        pvCounter.put(request_counter);        
    }
               
    //TODO: lock() unlock()
    private PVStructure execute(PVString op, PVString query) {
        PVLong pvLong = (PVLong) pvTop.getScalarArrayField("value", ScalarType.pvLong);
        System.out.println("EXECUTE");
        pvLong.put(123L);
        process();
        PVStructure pvResult = PVDataFactory.getPVDataCreate().createPVStructure(pvTop);
        return pvResult;
        
    }    
    
}
