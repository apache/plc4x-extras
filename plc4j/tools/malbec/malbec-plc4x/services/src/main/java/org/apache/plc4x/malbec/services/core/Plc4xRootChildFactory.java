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
package org.apache.plc4x.malbec.services.core;

import org.apache.plc4x.malbec.services.model.Plc4xDriverNode;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 *
 * @author cgarcia
 */
public class Plc4xRootChildFactory extends ChildFactory.Detachable<String> implements LookupListener, PropertyChangeListener {

    private Plc4xEventEnum E;    
    private ChangeListener listener;
    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);
    
    @Override     
    protected void addNotify() {
        db.addPropertyChangeListener(this);
    }    
   
    @Override     
    protected void removeNotify() {
        db.removePropertyChangeListener(this);
    }   
    
    @Override     
    protected Node createNodeForKey(String key) {         
        try {     
            return new Plc4xDriverNode(db.getDriverByName(key));
        } catch (IntrospectionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }    
    
    @Override
    protected boolean createKeys(List<String> toPopulate) {
        
        List<String> keys = new ArrayList<String>();
     
        for (String name:db.getDriverNames()) {
            keys.add(name);
            
        }
        
        Collections.sort(keys);         
        toPopulate.addAll(keys); 

        return true;
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        //this.refresh(true);
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        switch (Plc4xEventEnum.valueOf(pce.getPropertyName())) {
            case ADD_DRIVER:    this.refresh(true);
                                break;
            case REMOVE_DRIVER: this.refresh(true);
                                break;
        }
    }
    
}
