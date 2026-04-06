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
package org.apache.plc4x.merlot.archiver.test;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */

import java.time.Instant;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.PayloadType;
import org.apache.plc4x.merlot.archiver.core.MerlotTypeMapping;
import org.epics.vtype.Alarm;
import org.epics.vtype.AlarmSeverity;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VDouble;
import org.epics.vtype.VType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 *
 * @author cgarcia
 */
public class MerlotTypeMappingTest {
    
    public MerlotTypeMappingTest() {
    }
    
    @BeforeAll
    public static void setUpClass() {
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
    @DisplayName("Debe retornar el mapeo correcto para SCALAR_DOUBLE")
    void testScalarDoubleMapping() {
        MerlotTypeMapping mapping = MerlotTypeMapping.fromProto(PayloadType.SCALAR_DOUBLE);
        
        assertNotNull(mapping, "El mapeo no debería ser nulo");
        assertEquals(VDouble.class, mapping.getVTypeClass(), 
            "SCALAR_DOUBLE debe mapear a la interfaz VDouble");
    }

    @ParameterizedTest
    @EnumSource(MerlotTypeMapping.class)
    @DisplayName("Validar integridad de todos los mapeos definidos")
    void testAllMappings(MerlotTypeMapping mapping) {
        // Verifica que la clase VType asociada sea efectivamente una subinterfaz de VType
        assertTrue(VType.class.isAssignableFrom(mapping.getVTypeClass()),
            "La clase mapeada " + mapping.getVTypeClass().getName() + " debe ser un VType");
        
        // Verifica que el tipo de proto no sea nulo
        assertNotNull(mapping.getProtoType(), 
            "El tipo de Protobuf para " + mapping.name() + " no debe ser nulo");
    }

    @Test
    @DisplayName("Debe manejar tipos de payload desconocidos devolviendo null")
    void testUnknownPayloadType() {
        // Usamos un valor que no esté en nuestro Enum de mapeo (si existe en el .proto)
        // o simplemente verificamos el comportamiento con null
        assertNull(MerlotTypeMapping.fromProto(null), 
            "El mapeo de un tipo null debe resultar en null");
    }

    @Test
    @DisplayName("Verificar consistencia del mapa estático inverso")
    void testReverseMapConsistency() {
        for (MerlotTypeMapping mapping : MerlotTypeMapping.values()) {
            MerlotTypeMapping retrieved = MerlotTypeMapping.fromProto(mapping.getProtoType());
            assertEquals(mapping, retrieved, 
                "El mapeo recuperado por PayloadType debe coincidir con la instancia del Enum");
        }
    }
    
    @Test
    @DisplayName("Debe detectar el PayloadType correcto desde una instancia concreta")
    void testFromVTypeInstance() {
        // Creamos una instancia de prueba (VDouble)
        VDouble myValue = VDouble.of(10.5, Alarm.none(), Time.now(), Display.none());

        PayloadType detectedType = MerlotTypeMapping.fromVType(myValue);

        assertEquals(PayloadType.SCALAR_DOUBLE, detectedType, 
            "Una instancia de VDouble debe ser reconocida como SCALAR_DOUBLE");
    }    

    
}
