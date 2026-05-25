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
package org.apache.plc4x.malbec.s88.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.xmlbeans.XmlException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class S88ManagerTest {

    @Test
    public void testCreateSaveLoad() throws IOException, XmlException {
        S88Manager manager = new S88Manager();
        S88PlantModel model = manager.createNew("TestPlant");

        S88Element root = model.getRoot();
        S88Element area = new S88ElementImpl("Area1", S88Level.AREA);
        root.addChild(area);
        
        area.setProperty("commDriver", "S7");
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        manager.save(model, bos);
        
        String xml = bos.toString();
        assertTrue(xml.contains("TestPlant"));
        assertTrue(xml.contains("Area1"));
        assertTrue(xml.contains("commDriver"));
        
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        S88PlantModel loadedModel = manager.load(bis);
        
        assertNotNull(loadedModel.getRoot());
        assertEquals("TestPlant", loadedModel.getRoot().getId());
        assertEquals(1, loadedModel.getRoot().getChildren().size());
        
        S88Element loadedArea = loadedModel.getRoot().getChildren().get(0);
        assertEquals("Area1", loadedArea.getId());
        assertEquals("S7", loadedArea.getProperty("commDriver"));
    }
}
