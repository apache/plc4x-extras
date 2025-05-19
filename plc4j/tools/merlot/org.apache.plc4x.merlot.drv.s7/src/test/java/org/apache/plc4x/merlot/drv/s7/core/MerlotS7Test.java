/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */
package org.apache.plc4x.merlot.drv.s7.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.karaf.shell.api.console.Session;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;


/**
 *
 * @author cgarcia
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MerlotS7Test extends TestBase{
    
    

    private ExecutorService executor = Executors.newCachedThreadPool();

    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private PrintStream printStream = new PrintStream(byteArrayOutputStream);
    private PrintStream errStream = new PrintStream(byteArrayOutputStream);    
    
    
    final static String UNPACK_DIR_NAME = "merlot";
    
    
//    //@BeforeAll
//    public static void setUpClass() {
//    }
//    
//    //@AfterAll
//    public static void tearDownClass() {
//    }
//    
//    //@BeforeEach
//    public void setUp() {
//    }
//    
//    //@AfterEach
//    public void tearDown() {
//    }

    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
     @Test
     public void hello() {
         System.out.println("Hola mundo");   
        }

    
    @Test    
    public void listBundleCommand() throws Exception {
//        // assert on an available service
//        assertServiceAvailable(FeaturesService.class);
//
//        // installing a feature and verifying that it's correctly installed
//        installAndAssertFeature("scr");
//import static junit.framework.Assert.assertEquals;
//        // testing a command execution

        //session = sessionFactory.create(System.in, printStream, errStream); 

        String bundles = executeCommand("bundle:list -t 0");
        System.out.println(bundles);
     
//        assertContains("junit", bundles);
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
    
    
}
