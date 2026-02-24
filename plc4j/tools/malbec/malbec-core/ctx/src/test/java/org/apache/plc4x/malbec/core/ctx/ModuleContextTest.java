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
package org.apache.plc4x.malbec.core.ctx;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Collection;

public class ModuleContextTest {

    @Test
    public void testModuleContextFunctionality() {
        // Instantiate the class
        ModuleContext context = new ModuleContext();
        String testObject = "Test String";

        // Test inherited functionality (initial state)
        // verify nothing is there initially
        assertNull("Should be null initially", context.lookup(String.class));

        // Test put functionality
        context.put(testObject);
        
        // Check if it can be retrieved via inherited lookup method
        assertEquals("Should retrieve the object added", testObject, context.lookup(String.class));
        
        // Test inherited lookupAll
        Collection<? extends String> allStrings = context.lookupAll(String.class);
        assertEquals("Should have 1 element", 1, allStrings.size());
        assertTrue("Should contain the object", allStrings.contains(testObject));

        // Test remove functionality
        context.remove(testObject);
        assertNull("Should be null after remove", context.lookup(String.class));
        
        // Test adding multiple objects
        String obj1 = "One";
        String obj2 = "Two";
        context.put(obj1);
        context.put(obj2);
        
        allStrings = context.lookupAll(String.class);
        assertEquals("Should have 2 elements", 2, allStrings.size());
        assertTrue("Should contain obj1", allStrings.contains(obj1));
        assertTrue("Should contain obj2", allStrings.contains(obj2));
    }
}
