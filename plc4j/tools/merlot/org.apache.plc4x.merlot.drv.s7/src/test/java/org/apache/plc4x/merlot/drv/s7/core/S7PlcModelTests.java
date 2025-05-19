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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Set;
import org.apache.plc4x.java.s7.readwrite.MemoryArea;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.core.DBBaseFactory;
import org.apache.plc4x.merlot.drv.s7.impl.S7PlcModelImpl;
import org.epics.pvdata.pv.PVString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;


/**
 *
 * @author cgarcia
 */
public class S7PlcModelTests {
        
    private static S7PlcModelImpl model;
    private DBRecord AI;    
    
    public S7PlcModelTests() {
    }
    
    //@BeforeAll
    public static void setUpClass() {
        model = new S7PlcModelImpl(null, null);
    }
    
    //@AfterAll
    public static void tearDownClass() {
    }
    
    //@BeforeEach
    public void setUp() {
    }
    
    //@AfterEach
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
//         assertEquals(model.MemoryAreaId("C"), 0x1C);
//         assertEquals(model.MemoryAreaId("T"), 0x1D);
//         assertEquals(model.MemoryAreaId("D"), 0x80);
//         assertEquals(model.MemoryAreaId("I"), 0x81);
//         assertEquals(model.MemoryAreaId("Q"), 0x82);
//         assertEquals(model.MemoryAreaId("M"), 0x83);
//         assertEquals(model.MemoryAreaId("DB"), 0x84);
//         assertEquals(model.MemoryAreaId("DBI"), 0x85);
//         assertEquals(model.MemoryAreaId("LD"), 0x86);         
     }     
     
     @Test
     public void testCreateMemoryAreas() { 
        DBBaseFactory AIFactory = new S7DBAiFactory();
        AI = AIFactory.create("AI"); 
        PVString pvId = AI.getPVRecordStructure().getPVStructure().getStringField("id");
        PVString pvSCanTime = AI.getPVRecordStructure().getPVStructure().getStringField("scan_time");
        
        pvId.put("DEVICE:%I2500:BYTE[100]");
        model.CreateMemoryArea(AI); 
        
        pvId.put("DEVICE:%Q1900:BYTE[100]");
        model.CreateMemoryArea(AI);
        
        pvId.put("DEVICE:%M3000:BYTE[100]");
        model.CreateMemoryArea(AI); 

        pvId.put("DEVICE:%DB100:10:BYTE[100]");
        model.CreateMemoryArea(AI);
                                            
     }  
     
     @Test
     public void testPlcItemBufferWrapper() { 
        ByteBuf modelByteBuf = Unpooled.buffer(100);
        
        modelByteBuf.writeZero(modelByteBuf.capacity());
        
        PlcItem item01 = new PlcItemImpl.PlcItemBuilder("item1").
                setItemDescription("Desc").
                setItemId("").
                setItemByteBuf(modelByteBuf.slice(80,10)).                
                build();
        
        PlcItem item02 = new PlcItemImpl.PlcItemBuilder("item2").
                setItemDescription("Desc").
                setItemId("").                
                setItemByteBuf(modelByteBuf.slice(80,10)).                
                build();
        
        PlcItem item03 = new PlcItemImpl.PlcItemBuilder("item3").
                setItemDescription("Desc").
                setItemId("").                
                setItemByteBuf(modelByteBuf.slice(0,20)).                
                build();  
        
        modelByteBuf.setByte(80, 255);
        modelByteBuf.setByte(19, 123);
        
        assertEquals(modelByteBuf.getByte(80), item01.getItemByteBuf().getByte(0));
        assertEquals(modelByteBuf.getByte(80), item02.getItemByteBuf().getByte(0));        
        assertEquals(modelByteBuf.getByte(19), item03.getItemByteBuf().getByte(19));          
        
        item01.getItemByteBuf().setFloat(4, (float) 3.1416);
        item03.getItemByteBuf().setFloat(4, (float) 3.1416);  
        
        assertEquals(Float.valueOf(modelByteBuf.getFloat(84)), Float.valueOf(item01.getItemByteBuf().getFloat(4)));
        assertEquals(Float.valueOf(modelByteBuf.getFloat(84)), Float.valueOf(item02.getItemByteBuf().getFloat(4)));       
        assertEquals(Float.valueOf(modelByteBuf.getFloat(4)), Float.valueOf(item01.getItemByteBuf().getFloat(4)));         
        
        
     }    
     
     @Test
     public void testDBRecord() { 
         
         DBBaseFactory factory = new S7DBAiFactory();
         DBRecord dbRecord = factory.create("record01");
         
         PVString pvId = dbRecord.getPVRecordStructure().getPVStructure().getStringField("id");
         PVString pvScanTime = dbRecord.getPVRecordStructure().getPVStructure().getStringField("scan_time");
         
         pvId.put("XX");
         pvScanTime.put("SCANTime");
     }     
     
     
}
