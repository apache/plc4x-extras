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
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.DriverRecord;
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;

/**
 *
 *
 */
public class Plc4xDriverRuntime implements PropertyChangeListener  {

    private final DriverRecord driver;
    private final HashMap<DeviceRecord, Plc4xDeviceRuntime> devices = new HashMap();     
    
     
    public Plc4xDriverRuntime(DriverRecord driver) {
        this.driver = driver;
        this.driver.addPropertyChangeListener(this);
    }

    public void enableDriver(){
        devices.forEach((dev, runtime)->{
            dev.setEnable(Boolean.TRUE);
            });
    }
    
    public void disableDriver(){
        devices.forEach((dev, runtime)->{
            dev.setEnable(Boolean.FALSE);
            });        
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        switch (Plc4xPropertyEnum.valueOf(pce.getPropertyName())) {
            case ADD_DEVICE:    {            
                                    final DeviceRecord dev = (DeviceRecord) pce.getNewValue();
                                    devices.put(dev, new Plc4xDeviceRuntime(driver, dev));
                                    break;
                                }
            case REMOVE_DEVICE: { 
                                    final DeviceRecord dev = (DeviceRecord) pce.getOldValue();            
                                    devices.remove(dev);
                                    break;
                                }
            case ENABLE:        {
                                    System.out.println("Driver habilitado");
                                    Boolean oldvalue = (Boolean) pce.getOldValue();
                                    Boolean newvalue = (Boolean) pce.getNewValue();
                                    if ((!oldvalue) && (newvalue)) enableDriver();
                                    if ((oldvalue) && (!newvalue)) disableDriver();
                                };
                
                                
            default:;
        }
    }
    
}
