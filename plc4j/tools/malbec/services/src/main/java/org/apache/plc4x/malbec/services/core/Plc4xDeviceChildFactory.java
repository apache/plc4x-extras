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

import org.apache.plc4x.malbec.services.model.Plc4xTagGroupNode;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import static org.apache.plc4x.malbec.api.Plc4xEventEnum.ADD_DEVICE;
import static org.apache.plc4x.malbec.api.Plc4xEventEnum.REMOVE_DEVICE;
import org.apache.plc4x.malbec.api.TagGroupRecord;
import org.openide.util.Lookup;


public class Plc4xDeviceChildFactory extends ChildFactory.Detachable<TagGroupRecord> implements LookupListener, PropertyChangeListener  {
   
    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);      
    private final DeviceRecord device;
    private final Lookup.Result<TagGroupRecord> plc4xresult;
    private final Lookup.Template template = new Lookup.Template(TagGroupRecord.class);       
    private PropertyChangeListener listener;

    public Plc4xDeviceChildFactory(DeviceRecord device) {
        this.device = device;
        plc4xresult = device.getLookup().lookup(template);
        plc4xresult.addLookupListener(this);          
    }

    @Override     
    protected void addNotify() {
        db.addPropertyChangeListener(this);     
    }    
   
    @Override     
    protected void removeNotify() {
        db.removePropertyChangeListener(this);     
    }   
    
    @Override     
    protected Node createNodeForKey(TagGroupRecord key) {         
        try {     
            return new Plc4xTagGroupNode(key);
        } catch (IntrospectionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Node.EMPTY;
    }    
    
    @Override
    protected boolean createKeys(List<TagGroupRecord> toPopulate) {        
        device.getTagGroups().stream().forEach(b -> toPopulate.add(b));        
        return true;
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        //this.refresh(true);
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
         switch (Plc4xEventEnum.valueOf(pce.getPropertyName())) {
            case ADD_TAGGROUP:      this.refresh(true);
                                    break;
            case REMOVE_TAGGROUP:   this.refresh(true);
                                    break;
        }
    }
    
}
