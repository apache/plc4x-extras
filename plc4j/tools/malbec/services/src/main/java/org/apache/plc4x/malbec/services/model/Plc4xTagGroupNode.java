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
package org.apache.plc4x.malbec.services.model;

import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Properties;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.services.core.Plc4xAddTagAction;
import org.apache.plc4x.malbec.services.core.Plc4xDelTagAction;
import org.apache.plc4x.malbec.services.core.Plc4xPropertiesNotifier;
import org.apache.plc4x.malbec.services.core.Plc4xTagGroupChildFactory;
import org.openide.actions.DeleteAction;
import org.openide.actions.OpenLocalExplorerAction;
import org.openide.actions.PropertiesAction;
import org.openide.actions.RenameAction;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.SystemAction;
import org.apache.plc4x.malbec.api.TagGroupRecord;
import org.apache.plc4x.malbec.services.core.Plc4xAddTagGroupAction;
import org.apache.plc4x.malbec.services.core.Plc4xDelTagGroupAction;
import org.apache.plc4x.malbec.services.model.Bundle;
import org.openide.util.Lookup;

/**
 *
 * @author cgarcia
 */
public class Plc4xTagGroupNode  extends BeanNode implements PropertyChangeListener {

    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);    
    private final TagGroupRecord bean;
    private String key;     
    private PropertyChangeListener listener;    

    @Messages("HINT_Plc4xTagGroupNode=Represents one Plc4x driver.")    
    public Plc4xTagGroupNode(TagGroupRecord bean)  throws IntrospectionException {
        super(bean, Children.create(new Plc4xTagGroupChildFactory(bean), false));       
        this.bean = bean;   
        setIconBaseWithExtension("org/apache/plc4x/malbec/services/tags_doble_16x16.png"); 
        super.setName(bean.getTagGroupName());         
        setShortDescription(Bundle.HINT_Plc4xTagGroupNode());        
    }
    
    @Override     
    public Action[] getActions(boolean context) {
        Action[] result = new Action[]{
            SystemAction.get(OpenLocalExplorerAction.class),
            new Plc4xAddTagAction(this),
            null,
            SystemAction.get(RenameAction.class),
            null,
             new Plc4xDelTagGroupAction(this),
            SystemAction.get(PropertiesAction.class),
        };         
        return result;     
    } 
     
    @Override     
    public Action getPreferredAction() {
        return SystemAction.get(PropertiesAction.class);
    } 
    
    @Override     
    public Node cloneNode() {         
        try {     
            return new Plc4xTagGroupNode(bean);
        } catch (IntrospectionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Node.EMPTY;
    }

    @Messages({"PROP_TagGroup_value=Value",
        "HINT_TagGroup_value=Value of this system property."})     
    @Override     
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set props = sheet.get(Sheet.PROPERTIES);
        if (props == null) {
            props = Sheet.createPropertiesSet();
            sheet.put(props);
        }         
        props.put(new PropertySupport.Name(this));
        
        class ValueProp extends PropertySupport.ReadWrite {
            public ValueProp() {
                super("value", String.class, Bundle.PROP_TagGroup_value(), Bundle.HINT_TagGroup_value());
            }             
            
            @Override             
            public Object getValue() {
                return System.getProperty(key);
            }             
            
            @Override             
            public void setValue(Object nue) {
                System.setProperty(key, (String) nue);
                Plc4xPropertiesNotifier.changed();
            }         
        }         
        
        props.put(new ValueProp());
        
        /*
        Plc4xPropertiesNotifier.addChangeListener(listener = new ChangeListener() {
            @Override             
            public void stateChanged(ChangeEvent ev) {
                firePropertyChange("value", null, null);
            }         
        }); */
        
        return sheet;
    }    

    @Override     
    protected void finalize() throws Throwable {
        super.finalize();
    } 
    
    @Override     
    public boolean canRename() {
        return true;     
    }    
    
    @Override     
    public void setName(String nue) {
        Properties p = System.getProperties();
        String value = p.getProperty(key);
        p.remove(key);         
        
        if (value != null) {
            p.setProperty(nue, value);
        }         
        
        System.setProperties(p);         
        
        Plc4xPropertiesNotifier.changed();     
    }   
    
    @Override    
    public boolean canDestroy() {
        return true;     
    }
    
    @Override     
    public void destroy() throws IOException {
        db.removeTagGroup(bean.getUUID());        
        bean.removePropertyChangeListener(listener);
    }    

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
}
