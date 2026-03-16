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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import org.apache.karaf.features.BootFinished;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.s7.readwrite.MemoryArea;
import org.apache.plc4x.merlot.api.PlcDevice;
import org.apache.plc4x.merlot.api.PlcGeneralFunction;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcModel;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.api.DBRecordFactory;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.ops4j.pax.exam.Configuration;
import static org.ops4j.pax.exam.CoreOptions.keepCaches;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import static org.ops4j.pax.exam.OptionUtils.combine;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

/**
 *
 * @author cgarcia
 */
@RunWith(PaxExam.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ExamReactorStrategy(PerSuite.class)
@Ignore
public class S7PlcModelTest extends KarafTestSupport {
    
    
    // Wait for all the boot features to be installed.
    @Inject
    protected BootFinished bootFinished;
    
    private ExecutorService executor = Executors.newCachedThreadPool();

    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private PrintStream printStream = new PrintStream(byteArrayOutputStream);
    private PrintStream errStream = new PrintStream(byteArrayOutputStream);    
    
    
    final static String UNPACK_DIR_NAME = "merlot";
    
    /*
    *  
    */
    @Configuration
    public Option[] config() {
        return combine(new Option[] {
            keepCaches(), 
            mavenBundle("org.json","json","20250517")},                 
            super.config());
    }    
    
    @BeforeClass
    public static void setUpClass() {
        
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {        
    }

    /*
    * Installs all Merlot features associated with the S7 driver.
    */    
    @Test
    public void testS7Services_001() throws Exception {
        addFeaturesRepository("mvn:org.apache.karaf.decanter/apache-karaf-decanter/2.0.0/xml/features");    
        addFeaturesRepository("mvn:org.apache.plc4x.merlot.features/org.apache.plc4x.merlot.features/0.13.0-SNAPSHOT/xml/features");         
        installAndAssertFeature("org.apache.plc4x.merlot.features");                   
    }
    
    /*
    * List filtered bundle in Merlot server. 
    */
    @Test    
    public void testS7Services_002() throws Exception {
//        // assert on an available service
//        assertServiceAvailable(FeaturesService.class);
//
//        // installing a feature and verifying that it's correctly installed
//        installAndAssertFeature("scr");
//import static junit.framework.Assert.assertEquals;
//        // testing a command execution

        //session = sessionFactory.create(System.in, printStream, errStream); 

//        String cliRes = executeCommand("ls");
//        System.out.println(cliRes);
//        cliRes = executeCommand("bundle:diag 146");
//        System.out.println(cliRes);        
     
//        assertContains("junit", cliRes);
//
//        String features = executeCommand("feature:list -i");
//        System.out.print(features);
//        assertContains("scr", features);
//
//        // using a service and assert state or result
//        FeaturesService featuresService = getOsgiService(FeaturesService.class);
//        Feature scr = featuresService.getFeature("scr");
//        assertEquals("scr", scr.getName());
    }     
    
    /*
    * Check that all services are deployed.
    */
    @Test
    public void testS7Services_003() throws Exception {
        assertFeatureInstalled("org.apache.plc4x.merlot.features");        
        assertServiceAvailable(PlcDriver.class, "(org.apache.plc4x.driver.code=s7)", 1000);        
        assertServiceAvailable(DBRecordFactory.class, "(db.record.type=s7ai)", 1000);           
        assertServiceAvailable(PlcModel.class, "(db.record.plcmodel.category=s7)", 1000); 
        assertServiceAvailable(PlcModel.class, "(db.record.plcmodel.category=s7-light)", 1000);         
        assertServiceAvailable(PlcGeneralFunction.class);                
    }
    
    /*
    * Check drivers for this model 
    */
    @Test
    public void testS7Services_004() throws Exception {
        PlcGeneralFunction gf = null;
        gf = getOsgiService(PlcGeneralFunction.class);        
        assertNotEquals(gf, null);        
        var drivers =  gf.getPlcDrivers();        
        assertTrue(drivers.containsKey("simulated"));        
        assertTrue(drivers.containsKey("s7"));
        assertTrue(drivers.containsKey("s7-light"));                
    }
   
    /*
    * Check Memory Areas names.
    */
    @Test
    public void testS7Services_005() throws Exception {
        PlcGeneralFunction gf = null;
        gf = getOsgiService(PlcGeneralFunction.class);
        assertNotEquals(gf, null);        
        PlcModel plcModel = getOsgiService(PlcModel.class, "(db.record.plcmodel.category=s7)", 1000); 
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.COUNTERS.getShortName()));
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.TIMERS.getShortName()));            
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.DIRECT_PERIPHERAL_ACCESS.getShortName()));         
        
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.INPUTS.getShortName()));
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.OUTPUTS.getShortName()));
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.FLAGS_MARKERS.getShortName()));
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.DATA_BLOCKS.getShortName()));
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.INSTANCE_DATA_BLOCKS.getShortName()));
        assertTrue(plcModel.listMemoryAreas().contains(MemoryArea.LOCAL_DATA.getShortName())); 
    } 
    
    /*
    * Check Memory Areas Ids.
    */    
    @Test
    public void testS7Services_006() throws Exception {
        PlcGeneralFunction gf = null;
        gf = getOsgiService(PlcGeneralFunction.class);
        assertNotEquals(gf, null);        
        PlcModel plcModel = getOsgiService(PlcModel.class, "(db.record.plcmodel.category=s7)", 1000);         
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("C")), Long.valueOf(0x1C));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("T")), Long.valueOf(0x1D));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("D")), Long.valueOf(0x80));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("I")), Long.valueOf(0x81));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("Q")), Long.valueOf(0x82));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("M")), Long.valueOf(0x83));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("DB")), Long.valueOf(0x84));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("DBI")), Long.valueOf(0x85));
        assertEquals(Long.valueOf(plcModel.getMemoryAreaId("LD")), Long.valueOf(0x86));          
    } 
    
    /*
    * Check ByteBuf "slice" function from ByteBuf.
    */      
    @Test
    public void testS7Services_007() { 
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
     
    /*
    * Test all db record factorys
    * .
    */     
    @Test
     public void testS7Services_008() throws Exception {
        PlcModel plcModel = getOsgiService(PlcModel.class, "(db.record.plcmodel.category=s7)", 1000); 
        assertNotEquals(plcModel, null);        
        DBRecordFactory s7aiFactory = getOsgiService(DBRecordFactory.class, "(db.record.type=s7ai)", 1000);       
        assertNotEquals(s7aiFactory, null);
        
        DBRecord dbRecord = s7aiFactory.create("AI001");
        assertNotEquals(dbRecord, null); 
        PVStructure structure = dbRecord.getPVStructure();
        PVString pvId = structure.getStringField("id"); 
        PVString pvScan = structure.getStringField("scan_time"); 
        PVBoolean pvScanEnable = structure.getBooleanField("scan_enable"); 
        
        assertNotEquals(pvId, null);        
        assertNotEquals(pvScan, null);
        assertNotEquals(pvScanEnable, null);  
        
        //PlcItem test
        
        pvId.put("AG01:%DB100:100:BYTE");        
        plcModel.createMemoryArea(dbRecord);
                            
        pvId.put("AG01:%DB10:2048:BYTE"); 
        plcModel.createMemoryArea(dbRecord);

        assertEquals(2L, (long) plcModel.getMemoryAreaSegmentCount("DB"));        
        
        Optional<PlcItem> optMainPlcItem = plcModel.getMemoryAreaPlcItem("DB", 10);
        assertTrue(optMainPlcItem.isPresent());          
        PlcItem plcMainPlcItem = optMainPlcItem.get();
        
        ByteBuf byteBuf = plcMainPlcItem.getItemByteBuf(); 
        assertEquals(2112L, byteBuf.capacity());
        
        pvId.put("AG01:%DB10:2936:BYTE"); 
        plcModel.createMemoryArea(dbRecord); 
        assertEquals(3000L, byteBuf.capacity());        
       
        optMainPlcItem = plcModel.getMemoryAreaPlcItem("DB", 100);
        assertTrue(optMainPlcItem.isPresent()); 
        plcMainPlcItem = optMainPlcItem.get();
        byteBuf = plcMainPlcItem.getItemByteBuf(); 
        assertEquals(2048L, byteBuf.capacity());        
        
        pvId.put("AG01:%DB100:200:BYTE"); 
        plcModel.createMemoryArea(dbRecord);                
        assertEquals(2048L, byteBuf.capacity());  
        
        pvId.put("AG01:%DB100:3064:BYTE"); 
        plcModel.createMemoryArea(dbRecord);                
        assertEquals(3128L, byteBuf.capacity());         
        
        Set<Integer> dbs = plcModel.getMemoryAreaSegmentIds("DB");
        
        assertTrue(dbs.contains(10));
        assertTrue(dbs.contains(100));
        assertFalse(dbs.contains(66));
        
        //Internal ByteBuf test.
        optMainPlcItem = plcModel.getMemoryAreaPlcItem("DB", 10);        
        byteBuf = plcMainPlcItem.getItemByteBuf(); 
        
        DBRecord AI002 = s7aiFactory.create("AI002");
        assertNotEquals(AI002, null);  
        structure = AI002.getPVStructure();
        pvId = structure.getStringField("id"); 
        pvId.put("AG01:%DB10:640:BYTE");
        plcModel.createMemoryArea(AI002);
        AI002.atach(plcMainPlcItem);
                
        DBRecord AI003 = s7aiFactory.create("AI003");
        assertNotEquals(AI003, null);  
        structure = AI003.getPVStructure();
        pvId = structure.getStringField("id"); 
        pvId.put("AG01:%DB10:640:BYTE");
        AI003.atach(plcMainPlcItem);

        byteBuf = plcMainPlcItem.getItemByteBuf(); 
        ByteBuf byteBufAI002 = AI002.getInnerBuffer().get();
        ByteBuf byteBufAI003 = AI003.getInnerBuffer().get(); 
                
        byteBuf.setFloat(640, (float) 3.1416);        
        assertEquals(3.1416, (float) byteBuf.getFloat(640), 0.01);
        
        assertEquals(3.1416, (float) byteBufAI002.getFloat(0), 0.01);        
        assertEquals(3.1416, (float) byteBufAI003.getFloat(0), 0.01);         
                
        //DB100
        
        optMainPlcItem = plcModel.getMemoryAreaPlcItem("DB", 100); 
        plcMainPlcItem = optMainPlcItem.get();
        
        DBRecord AI004 = s7aiFactory.create("AI004");
        assertNotEquals(AI004, null);
        structure = AI004.getPVStructure();
        pvId = structure.getStringField("id"); 
        pvId.put("AG01:%DB100:0:BYTE");        
        AI004.atach(plcMainPlcItem);        
        
        byteBuf = plcMainPlcItem.getItemByteBuf();        
        ByteBuf byteBufAI004 = AI004.getInnerBuffer().get();        

        byteBuf.setFloat(100, (float) 3.1416);        
        assertEquals(3.1416, (float) byteBuf.getFloat(100), 0.01); 

        byteBuf.setByte(1, 0x01); 
        byteBuf.setByte(62, 0x1F); 
        byteBuf.setByte(63, 0x2F);  
        byteBuf.writerIndex(byteBuf.capacity());
        
        assertEquals(0x01, byteBufAI004.getByte(1), 0); 
        assertEquals(0x1F, byteBufAI004.getByte(62), 0);        
        assertEquals(0x2F, byteBufAI004.getByte(63), 0);
 
     }   
     
    /*
    * Test slice areas.
    * .
    */     
    @Test
     public void testS7Services_009() throws Exception {
        PlcGeneralFunction gf = null;
        gf = getOsgiService(PlcGeneralFunction.class);
        PlcModel plcModel = getOsgiService(PlcModel.class, "(db.record.plcmodel.category=s7)", 1000); 
        assertNotEquals(null, plcModel);        
        DBRecordFactory s7aiFactory = getOsgiService(DBRecordFactory.class, "(db.record.type=s7ai)", 1000);       
        assertNotEquals(null, s7aiFactory);
        
        DBRecord dbRecord = s7aiFactory.create("AI002");
        assertNotEquals(null, dbRecord); 
        PVStructure structure = dbRecord.getPVStructure();
        PVString pvId = structure.getStringField("id"); 
        PVString pvScan = structure.getStringField("scan_time"); 
        PVBoolean pvScanEnable = structure.getBooleanField("scan_enable"); 
        
        assertNotEquals(null, pvId);        
        assertNotEquals(null, pvScan);
        assertNotEquals(null, pvScanEnable);
        
        String devUuid = UUID.randomUUID().toString();

        Optional<PlcDevice> optPlcDevice = gf.createDevice(devUuid,
                                            "simulated", 
                                            "AG01",
                                            "simulated://127.0.0.1",
                                            "+C1=AS01.", 
                                            "La descripcion",
                                            "true");     
        if (optPlcDevice.isPresent()) {
            System.out.println("Ejecuta ls . " + optPlcDevice.get().getDeviceName() + " : " +  optPlcDevice.get().getDeviceKey());
            String cliRes = executeCommand("ls");
            System.out.println(cliRes);
            cliRes = executeCommand("bundle:diag 146");
            System.out.println(cliRes);                 
            PlcDevice plcDevice = getOsgiService(PlcDevice.class, 10000);
            cliRes = executeCommand("ls PlcDevice");  
            System.out.println("> " + cliRes);             
//            pvId.put("AG01:%DB20:10:BYTE");
//            pvScan.put("1000");
//            pvScanEnable.put(false);
//            plcModel.createScanGroup(dbRecord);
//            
//            cliRes = executeCommand("ls PlcGroup");
//            System.out.println(cliRes);   
        } else {
            System.out.println("XXXXXXXXXXXXXXXXXXXXXXX");
        }
        
//        assertServiceAvailable(PlcDevice.class, "(org.apache.plc4x.driver.code=s7)", 1000);           
//        pvId.put("AG01:%DB10:640:BYTE");
//        pvScanEnable.put(false);
//        pvScan.put("100");
//        
//        plcModel.createScanGroup(dbRecord);
//        
//        PlcGroup plcGroup =  getOsgiService(PlcGroup.class);
//        assertNotEquals(null, plcModel);
        
     }

    /*
    * Test scan groups.
    * .
    */     
    @Test
     public void testS7Services_010() throws Exception { 
         
     }    
     
    @Test
     public void testS7Services_012() throws Exception { 
        assumeTrue(S7TestSuite.LIVE_PLC);        
        PlcGeneralFunction gf = null;
        gf = getOsgiService(PlcGeneralFunction.class);
        assertNotEquals(gf, null);
        var drivers =  gf.getPlcDrivers();
        drivers.forEach((k,v) ->{
            System.out.println(k + " : " + v);
        });
        
        UUID devUuid = UUID.randomUUID();
        
        Optional<PlcDevice> optPlcDevice = gf.createDevice(devUuid.toString(),
                                            "s7", 
                                            "AS01",
                                            "s7://10.10.1.191",
                                            "+C1=AS01.", 
                                            "La descripcion",
                                            "true");     
        if (optPlcDevice.isPresent()) {
            System.out.println("Creado el Dispositivo...");
//            assertTrue(optPlcDevice.isPresent());
            PlcDevice plcDevice = optPlcDevice.get();
           
            plcDevice.init();
            plcDevice.enable();

//            assumeTrue(plcDevice.getPlcConnection().isConnected());             
            
            UUID grpUuid = UUID.randomUUID();
            
            Optional<PlcGroup> optPlcGroup =  gf.createGroup(grpUuid.toString(),
                                optPlcDevice.get().getUid().toString(),
                                "GRUPO001",
                                "Descripcion del grupo",
                                "100",
                                "true");   
            
            if (optPlcGroup.isPresent()) {
                var plcGroups = gf.getPlcGroups();
//                assertTrue(plcGroups.co<ntainsValue("GRUPO001"));
                
                for (int i= 1; i < 4; i++) {
                    Optional<PlcItem> optPlcItem = gf.
                            createItem(UUID.randomUUID().toString(), 
                            optPlcGroup.get().getGroupUid().toString(),
                            optPlcDevice.get().getUid().toString(),
                            "ITEM_" + i,
                            "Item description _" + i,
                            "%DB4:" + i +":DWORD",
                            "true");
                    if (optPlcItem.isPresent()){
                        optPlcItem.get().enable();
                        System.out.println(optPlcItem.get().getItemUid().toString()+ " : " + optPlcItem.get().getItemName());
                    }                                                            
                }                                                   
            }
                
            boolean logic = false;
            
            String cliRes = executeCommand("plc4x:device-list -d " + devUuid.toString());
            System.out.println(cliRes);
            
            cliRes = executeCommand("plc4x:device-list -g " + grpUuid.toString());
            System.out.println(cliRes);            
            
           for (int i=0;i<100000000; i++){
               System.out.print("");
               
           }
            try {
                plcDevice.destroy();                
                plcDevice.disable();                                 

            } catch(Exception ex) {
                ex.printStackTrace();
            }
            
            logic = !plcDevice.isEnable();           
             
        }     
     };      
    
}
