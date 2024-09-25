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
package org.apache.plc4x.malbec.services.controller;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import org.apache.plc4x.malbec.api.DriverRecord;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import org.openide.util.Lookup;
import org.openide.util.Lookup.Result;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=Plc4xController.class)
public class Plc4xController implements LookupListener, PropertyChangeListener {

    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);
    private final Lookup.Template devicetemplate = new Lookup.Template(DriverRecord.class);    
    private final Lookup.Result<DriverRecord> deviceresult;
    
    private final HashMap<DriverRecord, Plc4xDriverRuntime> drivers = new HashMap();
    
    public Plc4xController() {      
        deviceresult = ((Lookup.Provider) db).getLookup().lookup(devicetemplate);
        deviceresult.addLookupListener(this);
        db.addPropertyChangeListener(this);       
        for (DriverRecord driver:deviceresult.allInstances()) {
            drivers.put(driver, new Plc4xDriverRuntime(driver));
        }       
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        Lookup.Result result = (Result) ev.getSource();
        System.out.println("ResultChanged Numero de instancias: " + result.allInstances().size());
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        switch (Plc4xPropertyEnum.valueOf(pce.getPropertyName())) {
            case ADD_DRIVER:    System.out.println("add_driver");
                                break;
            case REMOVE_DRIVER: System.out.println("remove_driver");
                                break;                               
            default:;
        }
    }
       
}
    