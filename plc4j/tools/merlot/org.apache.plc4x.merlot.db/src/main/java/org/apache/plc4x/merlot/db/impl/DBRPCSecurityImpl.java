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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.apache.plc4x.merlot.scheduler.api.JobContext;
import org.epics.nt.NTScalar;
import org.epics.nt.NTScalarBuilder;
import org.epics.pvaccess.server.rpc.RPCRequestException;
import org.epics.pvaccess.server.rpc.RPCService;
import org.epics.pvaccess.server.rpc.Service;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVLong;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Status;
import org.epics.pvdatabase.PVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DBRPCSecurityImpl extends PVRecord implements RPCService, Job {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBRPCSecurityImpl.class);
    private static final String RPC_NAME = "_Security"; 
    
    private static final String COMMAND_LOGIN       = "login";
    private static final String COMMAND_LOGOUT      = "logout";   
    private static final String COMMAND_CHECK_LOGIN = "checklogin";     
    
    Map<String, Integer> userSessions = new ConcurrentHashMap<>();
    
    private PVStructure pvTop;    
    private long request_counter = 0; 
    private boolean result = false;
    
    public static DBRPCSecurityImpl create() {
        FieldCreate fieldCreate = FieldFactory.getFieldCreate();
        PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();

        PVStructure pvTop = ntScalarBuilder
                .value(ScalarType.pvBoolean)
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
        return this;
    }
        
    @Override
    public PVStructure request(PVStructure pvs) throws RPCRequestException {
        PVString pvOp = pvs.getSubField(PVString.class, "op");
        PVString pvUserName = pvs.getSubField(PVString.class, "username");        
        PVString pvQuery = pvs.getSubField(PVString.class, "query");  
        
        if (pvOp == null) {
            throw new RPCRequestException(Status.StatusType.ERROR,
                    "PVString field with name 'op' expected.");
        }
        if (pvQuery == null) {
            throw new RPCRequestException(Status.StatusType.ERROR,
                    "PVString field with name 'query' expected.");
        }        
        return execute(pvOp, pvUserName, pvQuery);
    }

    @Override
    public void process() {
        super.process();     
        request_counter++;      
    }
               
    //TODO: lock() unlock()
    private PVStructure execute(PVString pvOp, PVString pvUserName, PVString pvQuery) {  
        
        String operation = pvOp.get();
        
        if (operation.equals(COMMAND_LOGIN)) {
            try {
                result = login(pvUserName.get(), pvQuery.get());
            } catch (Exception ex) {
                LOGGER.info(ex.getMessage());
            }
        } else if (operation.equals(COMMAND_LOGOUT)) {
            try {
                result = logout(pvUserName.get());
            } catch (Exception ex) {
                LOGGER.info(ex.getMessage());
            }
        } else if (operation.equals(COMMAND_CHECK_LOGIN )) {
            result = checkUserLogin(pvUserName.get());
        }
        
        PVBoolean pvBoolean = pvTop.getBooleanField("value");
        pvBoolean.put(result);
        
        PVStructure pvResult = PVDataFactory.getPVDataCreate().createPVStructure(pvTop);
        return pvResult;
        
    }  
    
    public boolean login(String username, String password) throws Exception {
        LoginContext loginContext = new LoginContext("karaf", callbacks -> {
           for (Callback callback : callbacks) {
               if (callback instanceof NameCallback) {
                   ((NameCallback) callback).setName(username);
               } else if (callback instanceof PasswordCallback) {
                   ((PasswordCallback) callback).setPassword(password.toCharArray());
               } else {
                   throw new UnsupportedCallbackException(callback);
               }
           }
        });
        loginContext.login();
        result = loginContext.getSubject() != null;
        if (result) userSessions.put(username, 0);
        return result;
    }  

    public boolean logout(String username) throws Exception {       
        userSessions.remove(username);
        LOGGER.info("User [{}] logout.", username);         
        return true;
    }    
    
    public boolean checkUserLogin(String username){
        result = userSessions.containsKey(username);
        if (result) userSessions.replace(username, 0);
        return result;
    }

    @Override
    public void execute(JobContext context) {
        userSessions.forEach((k, i) -> i++);
        Set<String> keys = userSessions.keySet();
        for (String key:keys) {
            if (userSessions.get(key) > 60){
                LOGGER.info("User [{}] session timeout.", key);
                userSessions.remove(key);
            }
        }
    }
    
  
    
    
    
    
}
