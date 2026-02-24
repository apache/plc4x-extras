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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.DriverRecord;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.ENABLE;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.REMOVE_TAGGROUP;
import org.apache.plc4x.malbec.api.TagGroupRecord;

/**
 *
 * @author cgarcia
 */
public class Plc4xDeviceRuntime implements PropertyChangeListener {
    
    private final DriverRecord driver;
    private final DeviceRecord device; 
    private final HashMap<TagGroupRecord, Plc4xTagGroupRuntime> taggs = new HashMap();  
    private final ScheduledExecutorService ses = Executors.newScheduledThreadPool(4);    

    public Plc4xDeviceRuntime(DriverRecord driver, DeviceRecord device) {
        this.driver = null;
        this.device = device;
        this.device.addPropertyChangeListener(this);
    }
    
    public void enableDevice(){
        taggs.forEach((tagg, runtime)->{
            tagg.setEnable(Boolean.TRUE);
        });
    }
    
    public void disableDevice(){
        taggs.forEach((tagg, runtime)->{
            tagg.setEnable(Boolean.FALSE);            
        });        
    }  
    
    private void startScanning(){
        taggs.forEach((tagg, runtime)->{
            ScheduledFuture<?> sf = ses.scheduleAtFixedRate(runtime, 0, tagg.getScanTime(), TimeUnit.MILLISECONDS);
            runtime.setMyFuture(sf);
        });          
    }

    private void stopScanning(){
        taggs.forEach((tagg, runtime)->{
            ses.scheduleAtFixedRate(runtime, 0, tagg.getScanTime(), TimeUnit.MILLISECONDS);
        });          
    }     
    
    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        try {
            switch (Plc4xPropertyEnum.valueOf(pce.getPropertyName())) {
                case ADD_TAGGROUP:  {            
                                        final TagGroupRecord tagg = (TagGroupRecord) pce.getNewValue();
                                        taggs.put(tagg, new Plc4xTagGroupRuntime(tagg, ses));
                                        break;
                                    }
                case REMOVE_TAGGROUP:  {            
                                        final TagGroupRecord tagg = (TagGroupRecord) pce.getOldValue();
                                        taggs.remove(tagg);
                                        break;
                                    }
                case ENABLE:        {
                                        System.out.println("Device habilitado");
                                        Boolean oldvalue = (Boolean) pce.getOldValue();
                                        Boolean newvalue = (Boolean) pce.getNewValue();
                                        if ((!oldvalue) && (newvalue)) enableDevice();
                                        if ((oldvalue) && (!newvalue)) disableDevice();
                                    };
                                    break;
                default:;
            }
        } catch (IllegalArgumentException ex) {
            
        }
    }
    
}
