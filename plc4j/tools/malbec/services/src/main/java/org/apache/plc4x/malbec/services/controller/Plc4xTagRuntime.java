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
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.DESCRIPTION;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.ENABLE;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.NAME;
import org.apache.plc4x.malbec.api.TagRecord;

/**
 *
 * 
 */
public class Plc4xTagRuntime  implements PropertyChangeListener  {

    private final TagRecord tag;

    public Plc4xTagRuntime(TagRecord tag) {
        this.tag = tag;
        this.tag.addPropertyChangeListener(this);
    }
    
    public void enableTag(){
        //tag.setEnable(true);
    }
    
    public void disableTag(){
        //tag.setEnable(false);       
    }     
    
    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        switch (Plc4xPropertyEnum.valueOf(pce.getPropertyName())) {
            case NAME:          System.out.println("tag_name");
                                break;
            case DESCRIPTION:   System.out.println("tag_description");
                                break;
            case ENABLE:    {
                                System.out.println("Tag habilitado");
                                Boolean oldvalue = (Boolean) pce.getOldValue();
                                Boolean newvalue = (Boolean) pce.getNewValue();
                                if ((!oldvalue) && (newvalue)) enableTag();
                                if ((oldvalue) && (!newvalue)) disableTag();
                            };
            default:;
        }
    }
    
}
