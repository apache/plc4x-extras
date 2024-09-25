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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import javax.swing.Action;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.services.core.Plc4xAddTagGroupAction;
import org.apache.plc4x.malbec.services.core.Plc4xDelDeviceAction;
import org.apache.plc4x.malbec.services.core.Plc4xDelTagGroupAction;
import org.apache.plc4x.malbec.services.core.Plc4xDeviceChildFactory;
import org.openide.actions.OpenLocalExplorerAction;
import org.openide.actions.PropertiesAction;
import org.openide.actions.RenameAction;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.SystemAction;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;


public class Plc4xDeviceNode  extends BeanNode implements PropertyChangeListener{

    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);
     Plc4xPropertyEnum P;
    private final DeviceRecord bean;      
    private String key;     
    private PropertyChangeListener listener;  

    @Messages("HINT_Plc4xDeviceNode=Represents one Plc4x driver.")    
    public Plc4xDeviceNode(DeviceRecord bean) throws IntrospectionException {
        super(bean, Children.create(new Plc4xDeviceChildFactory(bean), false));       
        this.bean = bean;   
        this.bean.addPropertyChangeListener(this);        
        setIconBaseWithExtension("org/apache/plc4x/app/services/Device_16x16.png"); 
        super.setName(this.bean.getDeviceName());  
        setShortDescription(this.bean.getDeviceDescription()); 
        final BeanInfo info = Introspector.getBeanInfo(DeviceRecord.class);
    }
    
    @Override     
    public Action[] getActions(boolean context) {
        Action[] result = new Action[]{
            SystemAction.get(OpenLocalExplorerAction.class),
            new Plc4xAddTagGroupAction(this),
            null,
            SystemAction.get(RenameAction.class),
            null,
            new Plc4xDelDeviceAction(this),
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
            return new Plc4xDeviceNode(bean);
        } catch (IntrospectionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Node.EMPTY;
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
        super.setName(nue);    
    }   
    
    @Override    
    public boolean canDestroy() {
        return true;     
    }
    
    @Override     
    public void destroy() throws IOException {
        System.out.println("DESTROY: " + bean.getUUID().toString());
        db.removeDevice(bean); 
        System.out.println("Ya lo elimino");
        bean.removePropertyChangeListener(this); 
        System.out.println("Remueve los listener");       
    }    

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        try {
            switch (Plc4xPropertyEnum.valueOf(pce.getPropertyName())) {
                case NAME: setName((String) this.bean.getDeviceName());
                break;
                case DESCRIPTION: setShortDescription(this.bean.getDeviceDescription());
                break;
                case ENABLE: {
                    final Boolean b = (Boolean) pce.getNewValue();
                    String str = b ? "org/apache/plc4x/app/services/tag_verde_16x16.png" :
                            "org/apache/plc4x/app/services/tag_roja_16x16.png";
                    setIconBaseWithExtension(str);
                }
                default:;
            }
        }
        catch (Exception ex){
            System.out.println(ex.toString());
        }
    }
    
}
