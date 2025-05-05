/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.drv.s7.core;

import java.util.Set;
import org.apache.plc4x.java.s7.readwrite.MemoryArea;
import org.apache.plc4x.java.s7.readwrite.tag.S7Tag;
import org.apache.plc4x.merlot.drv.s7.impl.S7PlcModelImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author cgarcia
 */
public class S7PlcModelTests {
        
    private static S7PlcModelImpl model;
    
    public S7PlcModelTests() {
    }
    
    @BeforeAll
    public static void setUpClass() {
        model = new S7PlcModelImpl();
    }
    
    @AfterAll
    public static void tearDownClass() {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

     @Test
     public void testAreaModels() {     
        Set<String> areas =  model.ListMemoryAreas();  
        assertTrue(areas.contains(MemoryArea.COUNTERS.getShortName()));
        assertTrue(areas.contains(MemoryArea.TIMERS.getShortName()));
        assertTrue(areas.contains(MemoryArea.DIRECT_PERIPHERAL_ACCESS.getShortName()));
        assertTrue(areas.contains(MemoryArea.INPUTS.getShortName()));
        assertTrue(areas.contains(MemoryArea.OUTPUTS.getShortName()));
        assertTrue(areas.contains(MemoryArea.FLAGS_MARKERS.getShortName()));
        assertTrue(areas.contains(MemoryArea.DATA_BLOCKS.getShortName()));
        assertTrue(areas.contains(MemoryArea.INSTANCE_DATA_BLOCKS.getShortName()));
        assertTrue(areas.contains(MemoryArea.LOCAL_DATA.getShortName()));
                               
     }
     
     @Test
     public void testAreaModelsId() {    
         assertEquals(model.MemoryAreaId("C"), 0x1C);
         assertEquals(model.MemoryAreaId("T"), 0x1D);
         assertEquals(model.MemoryAreaId("D"), 0x80);
         assertEquals(model.MemoryAreaId("I"), 0x81);
         assertEquals(model.MemoryAreaId("Q"), 0x82);
         assertEquals(model.MemoryAreaId("M"), 0x83);
         assertEquals(model.MemoryAreaId("DB"), 0x84);
         assertEquals(model.MemoryAreaId("DBI"), 0x85);
         assertEquals(model.MemoryAreaId("LD"), 0x86);         
     }     
     
     @Test
     public void testCreateMemoryAreas() { 
         S7Tag s7TagC = null;
         S7Tag s7TagT = null;
         S7Tag s7TagD = null;
         S7Tag s7TagI = S7Tag.of("%I2500:BYTE[100]");         
         S7Tag s7TagQ = S7Tag.of("%Q1900:BYTE[100]");         
         S7Tag s7TagM = S7Tag.of("%M3000:BYTE[100]");         
         S7Tag s7TagDB = S7Tag.of("%DB100:10:BYTE[100]");         
         S7Tag s7TagDBI = null;         
         S7Tag s7TagLD = null;  
         
         model.CreateMemoryArea(s7TagI);           
         model.CreateMemoryArea(s7TagQ);           
         model.CreateMemoryArea(s7TagM);         
         model.CreateMemoryArea(s7TagDB);
         
         
         
         
     }     
     
     
}
