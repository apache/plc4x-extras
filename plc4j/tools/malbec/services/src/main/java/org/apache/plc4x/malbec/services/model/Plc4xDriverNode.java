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
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.services.core.Plc4xAddDeviceAction;
import org.apache.plc4x.malbec.services.core.Plc4xDriverChildFactory;
import org.apache.plc4x.malbec.services.core.Plc4xPropertiesNotifier;
import org.apache.plc4x.java.api.PlcDriver;
import org.openide.actions.OpenLocalExplorerAction;
import org.openide.actions.PropertiesAction;
import org.openide.actions.ToolsAction;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.AbstractLookup;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.DriverRecord;
import org.apache.plc4x.malbec.services.model.Bundle;


public class Plc4xDriverNode  extends BeanNode  implements LookupListener {

    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);    
    private final DriverRecord bean;     
    private PropertyChangeListener listener; 
    private final Lookup.Result<DeviceRecord> plc4xresult;
    private final Lookup.Template template = new Lookup.Template(DeviceRecord.class);      

    @Messages("HINT_Plc4xDriverNode=Represents one Plc4x driver.")    
    public Plc4xDriverNode(DriverRecord bean) throws IntrospectionException {
        super(bean, Children.create(new Plc4xDriverChildFactory(bean), false));
        this.bean = bean;   
        setIconBaseWithExtension("org/apache/plc4x/malbec/services/Driver_16x16.png"); 
        super.setName(bean.getProtocolName());         
        setShortDescription(Bundle.HINT_Plc4xDriverNode()); 
        
        plc4xresult = bean.getLookup().lookup(template);
        plc4xresult.addLookupListener(this);

    }
    
    @Override     
    public Action[] getActions(boolean context) {
        Action[] result = new Action[]{
            SystemAction.get(OpenLocalExplorerAction.class),            
            new Plc4xAddDeviceAction(this),
            null,
            null,
            SystemAction.get(ToolsAction.class),
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
            return new Plc4xDriverNode(bean);
        } catch (IntrospectionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

    @Messages({"PROP_Driver_value=Value",
        "HINT_Driver_value=Value of this system property."})     
    @Override     
    protected Sheet createSheet() {

        Sheet sheet = super.createSheet();
        Sheet.Set props = sheet.get(Sheet.PROPERTIES);
        /*
        if (props == null) {
            props = Sheet.createPropertiesSet();
            sheet.put(props);
        }         
        props.put(new PropertySupport.Name(this));
        
        class ValueProp extends PropertySupport.ReadWrite {
            public ValueProp() {
                super("value", String.class, Bundle.PROP_Driver_value(), Bundle.HINT_Driver_value());
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
        Plc4xPropertiesNotifier.addChangeListener(listener = new ChangeListener() {
            @Override             
            public void stateChanged(ChangeEvent ev) {
                firePropertyChange("value", null, null);
            }         
        });
        */
        return sheet;
    }    

    @Override     
    protected void finalize() throws Throwable {
        super.finalize();
        if (listener != null) {
            bean.removePropertyChangeListener(listener);
        }
    } 
    
    @Override     
    public boolean canRename() {
        return true;     
    }    
    
    @Override     
    public void setName(String nue) {

    }   
    
    @Override    
    public boolean canDestroy() {
        return true;     
    }
    
    @Override     
    public void destroy() throws IOException {
        
    } 
    
    public DriverRecord getDriverRecord() {
        return bean;
    }

    @Override
    public void resultChanged(LookupEvent ev) {

    }
    
}
