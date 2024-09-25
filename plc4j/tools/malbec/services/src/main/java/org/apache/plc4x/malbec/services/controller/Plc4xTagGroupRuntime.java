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
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.ENABLE;
import org.apache.plc4x.malbec.api.TagGroupRecord;
import org.apache.plc4x.malbec.api.TagRecord;


public class Plc4xTagGroupRuntime implements PropertyChangeListener, Runnable  {

    private final TagGroupRecord tagg;
    private final HashMap<TagRecord, Plc4xTagRuntime> tags = new HashMap();
     private final ScheduledExecutorService ses;
    private ScheduledFuture<?> sf = null;    
    
    
    public Plc4xTagGroupRuntime(TagGroupRecord tagg, ScheduledExecutorService ses) {
        this.tagg = tagg;
        this.ses = ses;
        this.tagg.addPropertyChangeListener(this);
    }


    public void enableTagGroup(){
        tags.forEach((tagg, runtime)->{
            tagg.setEnable(Boolean.TRUE);
        });
        startScanning();
    }
    
    public void disableTagGroup(){
        tags.forEach((tagg, runtime)->{
            tagg.setEnable(Boolean.FALSE);
        });
        if (null != sf){
            sf.cancel(true);
        }
        stopScanning();
    }
    
    private void startScanning(){
        sf = ses.scheduleAtFixedRate(this, 0, tagg.getScanTime(), TimeUnit.MILLISECONDS);       
    }

    private void stopScanning(){
        sf.cancel(true);
    }      
    
    public void setMyFuture(ScheduledFuture<?> sf) {
        this.sf = sf;
    }

    
    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        switch (Plc4xPropertyEnum.valueOf(pce.getPropertyName())) {
            case ADD_TAG:   {    System.out.println("add_tag");
                                final TagRecord tag = (TagRecord) pce.getNewValue();
                                tags.put(tag, new Plc4xTagRuntime(tag));
                                break;
                            }
            case REMOVE_TAG:{    System.out.println("remove_tag");
                                final TagRecord tag = (TagRecord) pce.getNewValue();
                                tags.remove(tag);
                                break;
                            }
            case ENABLE:    {
                                System.out.println("TagGroup habilitado");
                                Boolean oldvalue = (Boolean) pce.getOldValue();
                                Boolean newvalue = (Boolean) pce.getNewValue();
                                if ((!oldvalue) && (newvalue)) enableTagGroup();
                                if ((oldvalue) && (!newvalue)) disableTagGroup();
                            };
                                break;
            default:;
        }
    }

    @Override
    public void run() {
        System.out.println("Ejecutando tarea: " + tagg.getTagGroupName());
    }
    
}
