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

import java.awt.datatransfer.Transferable;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Properties;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.api.MasterDB;
import org.apache.plc4x.malbec.api.Plc4xPropertyEnum;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.DESCRIPTION;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.ENABLE;
import static org.apache.plc4x.malbec.api.Plc4xPropertyEnum.NAME;
import org.apache.plc4x.malbec.services.core.Plc4xPropertiesNotifier;
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
import org.apache.plc4x.malbec.api.TagRecord;
import org.apache.plc4x.malbec.services.core.Plc4xAddTagAction;
import org.apache.plc4x.malbec.services.core.Plc4xDelTagAction;
import org.apache.plc4x.malbec.services.model.Bundle;
import org.openide.util.Lookup;

/**
 *
 * @author cgarcia
 */
public class Plc4xTagNode  extends BeanNode  implements PropertyChangeListener  {
    
    private final MasterDB db = Lookup.getDefault().lookup(MasterDB.class);      
    private final TagRecord bean;
    private String key;     
    private ChangeListener listener;    

    @Messages("HINT_Plc4xTagNode=Represents one Plc4x driver.")    
    public Plc4xTagNode(TagRecord bean)  throws IntrospectionException {
        super(bean, Children.LEAF);        
        this.bean = bean; 
        this.bean.addPropertyChangeListener(this);
        setIconBaseWithExtension("org/apache/plc4x/app/services/tag_amarilla_linea_16x16.png"); 
        super.setName(bean.getTagName());         
        setShortDescription(Bundle.HINT_Plc4xTagNode());        
    }
    
    @Override     
    public Action[] getActions(boolean context) {
        Action[] result = new Action[]{
            SystemAction.get(OpenLocalExplorerAction.class),
            null,
            null,
            SystemAction.get(RenameAction.class),
            null,
            new Plc4xDelTagAction(this),
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
            return new Plc4xTagNode(bean);
        } catch (IntrospectionException ex) {
            Exceptions.printStackTrace(ex);
        }
        return Node.EMPTY;
    }

    @Messages({"PROP_TagNode_value=Value",
        "HINT_TagNode_value=Value of this system property."})     
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
                super("value", String.class, Bundle.PROP_TagNode_value(), Bundle.HINT_TagNode_value());
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
        
        return sheet;
    }    

    @Override     
    protected void finalize() throws Throwable {
        super.finalize();
        if (listener != null) {
            Plc4xPropertiesNotifier.removeChangeListener(listener);
        }
    } 
    
    @Override     
    public boolean canRename() {
        return true;     
    }    
    
    @Override     
    public void setName(String name) {
        this.setDisplayName(name);
    }   
    
    @Override    
    public boolean canDestroy() {
        return true;     
    }
    
    @Override     
    public void destroy() throws IOException {
        db.removeTag(this.bean.getUUID());
        this.bean.removePropertyChangeListener(this);
    }    

    @Override
    public Transferable drag() throws IOException {
        System.out.println("tratando de hacer un drag...");
        return super.drag(); 
    }

    
    
    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        switch (Plc4xPropertyEnum.valueOf(pce.getPropertyName())) {
            case NAME: setName((String) this.bean.getTagName());
            break;
            case DESCRIPTION: setShortDescription(this.bean.getTagDesc());
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
    
}
