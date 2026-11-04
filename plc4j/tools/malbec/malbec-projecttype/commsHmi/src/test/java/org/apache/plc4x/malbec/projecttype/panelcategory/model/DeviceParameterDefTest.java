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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeviceParameterDefTest {

    private static DeviceParameterDef positiveInt(boolean required) {
        return new DeviceParameterDef("pdu-size", "1024", required,
                value -> value.matches("\\d+") && Integer.parseInt(value) > 0,
                "debe ser un numero entero mayor a 0.");
    }

    @Test
    void validResultCarriesNoMessage() {
        ValidationResult result = ValidationResult.valid();

        assertTrue(result.isValid());
        assertEquals("", result.getMessage());
    }

    @Test
    void invalidResultKeepsItsMessage() {
        ValidationResult result = ValidationResult.invalid("algo no cuadra");

        assertFalse(result.isValid());
        assertEquals("algo no cuadra", result.getMessage());
    }

    @Test
    void accessorsReturnWhatWasBuilt() {
        DeviceParameterDef def = positiveInt(true);

        assertEquals("pdu-size", def.getKey());
        assertEquals("1024", def.getDefaultValue());
        assertTrue(def.isRequired());
    }

    @Test
    void optionalParameterAcceptsEmptyValue() {
        assertTrue(positiveInt(false).validate("").isValid());
        assertTrue(positiveInt(false).validate(null).isValid());
        assertTrue(positiveInt(false).validate("   ").isValid());
    }

    @Test
    void requiredParameterRejectsEmptyValueNamingTheKey() {
        ValidationResult result = positiveInt(true).validate(null);

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("pdu-size"),
                "el aviso debe nombrar el parametro: " + result.getMessage());
    }

    @Test
    void valueIsTrimmedBeforeValidation() {
        assertTrue(positiveInt(false).validate("  512  ").isValid());
    }

    @Test
    void validatorRejectionAppendsTheReason() {
        ValidationResult result = positiveInt(false).validate("cero");

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("pdu-size"), result.getMessage());
        assertTrue(result.getMessage().contains("mayor a 0"), result.getMessage());
    }

    @Test
    void nullValidatorAcceptsAnythingNotEmpty() {
        DeviceParameterDef def = new DeviceParameterDef("cotp.local-address", "", false, null, "");

        assertTrue(def.validate("10.0.0.1").isValid());
        assertTrue(def.validate("   ").isValid());
    }

    @Test
    void zeroIsRejectedByPositiveIntegerRule() {
        assertFalse(positiveInt(false).validate("0").isValid());
        assertTrue(positiveInt(false).validate("1").isValid());
    }
}