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

import org.apache.plc4x.malbec.services.model.Plc4xTagNode;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.api.Plc4xEventEnum;
import static org.apache.plc4x.malbec.api.Plc4xEventEnum.ADD_TAGGROUP;
import static org.apache.plc4x.malbec.api.Plc4xEventEnum.REMOVE_TAGGROUP;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.apache.plc4x.malbec.api.TagRecord;
import org.apache.plc4x.malbec.api.TagGroupRecord;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;


public class Plc4xTagGroupChildFactory extends ChildFactory.Detachable<TagRecord>  implements LookupListener, PropertyChangeListener {
   
    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);     
    private final TagGroupRecord tagg; 
    private final Lookup.Result<TagRecord> plc4xresult;
    private final Lookup.Template template = new Lookup.Template(TagRecord.class);      
    private PropertyChangeListener listener;

    public Plc4xTagGroupChildFactory(TagGroupRecord tagg) {
        this.tagg = tagg;
        plc4xresult = tagg.getLookup().lookup(template);
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
    protected Node createNodeForKey(TagRecord key) {         
        try {     
            return new Plc4xTagNode(key);
        } catch (IntrospectionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Node.EMPTY;
    }    
    
    @Override
    protected boolean createKeys(List<TagRecord> toPopulate) {
        tagg.getTags().stream().forEach(b -> toPopulate.add(b));
        return true;
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        //this.refresh(true);
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
         switch (Plc4xEventEnum.valueOf(pce.getPropertyName())) {
            case ADD_TAG:       this.refresh(true);
                                break;
            case REMOVE_TAG:    this.refresh(true);
                                break;
        }
    }
    
}
