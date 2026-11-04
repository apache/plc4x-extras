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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigWithersTest {

    private static final String[] ORIGINAL = {
        "uuid-1", "nombre", "descripcion", "500", "etiqueta", "md5-1", "grupo-1",
        "segundo", "tercero", "cuarto", "quinto", "sexto", " septimo", "octavo", "noveno",
        "decimo", "undecimo", "duodecimo", "decimotercero", "catorce"};

    @Test
    void eachWitherChangesOnlyItsField() {
        for (Class<?> type : List.of(CommConfigData.GroupConfig.class,
                CommConfigData.ItemConfig.class, CommConfigData.PvConfig.class)) {
            for (RecordComponent campo : type.getRecordComponents()) {
                verifyWithOneField(type, campo);
            }
        }
    }

    private void verifyWithOneField(Class<?> type, RecordComponent campo) {
        String name = "with" + Character.toUpperCase(campo.getName().charAt(0))
                + campo.getName().substring(1);
        Object original = exampleInstance(type);
        Object copia = invoke(original, name, campo.getType(), differentValue(campo.getType()));

        assertNotEquals(original, copia,
                type.getSimpleName() + "." + name + " tendria que producir otra instancia");

        for (RecordComponent otro : type.getRecordComponents()) {
            boolean esElCampoEditado = otro.getName().equals(campo.getName());
            Object esperado = esElCampoEditado ? differentValue(otro.getType())
                    : leer(original, otro.getName());
            Object real = leer(copia, otro.getName());
            assertEquals(esperado, real,
                    type.getSimpleName() + "." + name + " no dejo "
                            + otro.getName() + " como estaba");
        }
    }

    @Test
    void withersReturnCopiesNotTheSameObject() {
        CommConfigData.PvConfig pv = pvOriginal();

        CommConfigData.PvConfig copia = pv.withDescriptorText("otro descriptor");

        assertEquals("otro descriptor", copia.getDescriptorText());
        assertEquals(pv.getUuid(), copia.getUuid());
        assertEquals(pv.getS88Path(), copia.getS88Path());
        assertTrue(pv != copia, "una copia tiene que ser un objeto distinto");
    }

    @Test
    void encadenarWithersAcumulaLosCambios() {
        CommConfigData.PvConfig pv = pvOriginal();

        CommConfigData.PvConfig copia = pv
                .withDescriptorText("nuevo descriptor")
                .withScanEnable(false)
                .withDisplayUnits("l/h")
                .withControlMinStep("2.5");

        assertEquals("nuevo descriptor", copia.getDescriptorText());
        assertFalseBoolean(copia.isScanEnable(), "scanEnable debe quedar desactivado");
        assertEquals("l/h", copia.getDisplayUnits());
        assertEquals("2.5", copia.getControlMinStep());
        assertEquals(pv.getUuid(), copia.getUuid(), "lo no tocado se conserva");
        assertEquals(pv.getAreaName(), copia.getAreaName());
        assertTrue(pv.isScanEnable(), "el original no se toca");
    }

    @Test
    void everyComponentHasItsWither() {
        for (Class<?> type : List.of(CommConfigData.GroupConfig.class,
                CommConfigData.ItemConfig.class, CommConfigData.PvConfig.class)) {
            for (RecordComponent campo : type.getRecordComponents()) {
                String name = "with" + Character.toUpperCase(campo.getName().charAt(0))
                        + campo.getName().substring(1);
                boolean existe = Arrays.stream(type.getDeclaredMethods())
                        .anyMatch(m -> m.getName().equals(name)
                                && m.getParameterCount() == 1);
                assertTrue(existe, type.getSimpleName() + " deberia tener " + name + "()");
            }
        }
    }

    private static void assertFalseBoolean(boolean valor, String mensaje) {
        assertEquals(false, valor, mensaje);
    }

    private static Object exampleInstance(Class<?> type) {
        RecordComponent[] componentes = type.getRecordComponents();
        Class<?>[] tipos = new Class<?>[componentes.length];
        Object[] valores = new Object[componentes.length];
        for (int i = 0; i < componentes.length; i++) {
            tipos[i] = componentes[i].getType();
            valores[i] = componentes[i].getType() == boolean.class
                    ? Boolean.FALSE : differentValues()[i];
        }
        try {
            return type.getDeclaredConstructor(tipos).newInstance(valores);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo construir " + type.getSimpleName(), e);
        }
    }

    private static String[] differentValues() {
        String[] v = new String[ORIGINAL.length];
        Arrays.fill(v, "x");
        v[0] = "uuid-original";
        return v;
    }

    private static Object differentValue(Class<?> type) {
        return type == boolean.class ? Boolean.TRUE : "valor-distinto";
    }

    private static Object leer(Object registro, String componente) {
        try {
            return registro.getClass().getMethod(componente).invoke(registro);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo leer " + componente, e);
        }
    }

    private static Object invoke(Object registro, String metodo, Class<?> type, Object valor) {
        try {
            Method m = registro.getClass().getDeclaredMethod(metodo, type);
            m.setAccessible(true);
            return m.invoke(registro, valor);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No existe " + metodo + "(" + type.getSimpleName()
                    + ") en " + registro.getClass().getName()
                    + "; metodos: " + namesOf(registro.getClass()), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo invocar " + metodo, e);
        }
    }

    private static String namesOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).sorted()
                .reduce((a, b) -> a + ", " + b).orElse("(ninguno)");
    }

    private static CommConfigData.PvConfig pvOriginal() {
        return new CommConfigData.PvConfig("uuid-1", "PV1", "INT", "Area 1", "0", "descriptor",
                "500", true, true, "0", "10", "Presion (bar)", "%.2f", "bar", "0", "10", "0.1",
                "md5-pv", "S88.1");
    }
}