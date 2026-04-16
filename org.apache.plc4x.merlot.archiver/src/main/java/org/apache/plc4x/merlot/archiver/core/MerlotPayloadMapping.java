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
package org.apache.plc4x.merlot.archiver.core;


import org.epics.vtype.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;
import org.apache.plc4x.merlot.api.PB.EPICSEvent;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.PayloadType;
import org.epics.util.array.IteratorDouble;

/**
 * Mapeador de tipos entre la jerarquía VType de EPICS y los mensajes Protobuf 
 * de la arquitectura del Archiver Appliance.
 */
public enum MerlotPayloadMapping {
    // Escalares
    SCALAR_DOUBLE(PayloadType.SCALAR_DOUBLE, VDouble.class),
    SCALAR_FLOAT(PayloadType.SCALAR_FLOAT, VFloat.class),
    SCALAR_INT(PayloadType.SCALAR_INT, VInt.class),
    SCALAR_SHORT(PayloadType.SCALAR_SHORT, VShort.class),
    SCALAR_BYTE(PayloadType.SCALAR_BYTE, VByte.class),
    SCALAR_STRING(PayloadType.SCALAR_STRING, VString.class),
    SCALAR_ENUM(PayloadType.SCALAR_ENUM, VEnum.class),
    
    // Vectores (Waveforms)
    VECTOR_DOUBLE(PayloadType.WAVEFORM_DOUBLE, VDoubleArray.class),
    VECTOR_FLOAT(PayloadType.WAVEFORM_FLOAT, VFloatArray.class),
    VECTOR_INT(PayloadType.WAVEFORM_INT, VIntArray.class),
    VECTOR_SHORT(PayloadType.WAVEFORM_SHORT, VShortArray.class),
    VECTOR_BYTE(PayloadType.WAVEFORM_BYTE, VByteArray.class);

    private final PayloadType protoType;
    private final Class<? extends VType> vTypeClass;

    private static final EnumMap<MerlotPayloadMapping, Function<VType, Object>> CONSTRUCTORS = new EnumMap<>(MerlotPayloadMapping.class);

    static {
        // Registro de factorías para Escalares
        CONSTRUCTORS.put(SCALAR_DOUBLE, (v) -> buildScalarDouble((VDouble) v));
        CONSTRUCTORS.put(SCALAR_FLOAT,  (v) -> buildScalarFloat((VFloat) v));
        CONSTRUCTORS.put(SCALAR_INT,    (v) -> buildScalarInt((VInt) v));
        CONSTRUCTORS.put(SCALAR_SHORT,  (v) -> buildScalarShort((VShort) v));
        CONSTRUCTORS.put(SCALAR_BYTE,   (v) -> buildScalarByte((VByte) v));
        CONSTRUCTORS.put(SCALAR_STRING, (v) -> buildScalarString((VString) v));
        CONSTRUCTORS.put(SCALAR_ENUM,   (v) -> buildScalarEnum((VEnum) v));

        // Registro de factorías para Vectores
        CONSTRUCTORS.put(VECTOR_DOUBLE, (v) -> buildVectorDouble((VDoubleArray) v));
        CONSTRUCTORS.put(VECTOR_FLOAT,  (v) -> buildVectorFloat((VFloatArray) v));
        CONSTRUCTORS.put(VECTOR_INT,    (v) -> buildVectorInt((VIntArray) v));
        CONSTRUCTORS.put(VECTOR_SHORT,  (v) -> buildVectorShort((VShortArray) v));
        CONSTRUCTORS.put(VECTOR_BYTE,   (v) -> buildVectorByte((VByteArray) v));
    }

    MerlotPayloadMapping(PayloadType protoType, Class<? extends VType> vTypeClass) {
        this.protoType = protoType;
        this.vTypeClass = vTypeClass;
    }

    public PayloadType getPayloadType() {
        return protoType;
    }
    
    /**
     * Punto de entrada principal para crear un evento serializable a partir de un VType.
     */
    public static Object createEvent(VType vtype) {
        MerlotPayloadMapping mapping = fromVType(vtype);
        if (mapping != null && CONSTRUCTORS.containsKey(mapping)) {
            return CONSTRUCTORS.get(mapping).apply(vtype);
        }
        return null;
    }

    public static MerlotPayloadMapping fromVType(VType v) {
        for (MerlotPayloadMapping m : values()) {
            if (m.vTypeClass.isInstance(v)) return m;
        }
        return null;
    }

    // --- Implementación de Builders Escalares ---

    private static EPICSEvent.ScalarDouble buildScalarDouble(VDouble v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());
        return EPICSEvent.ScalarDouble.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).setVal(v.getValue())
                .setSeverity(v.getAlarm().getStatus().ordinal())
                .setStatus(v.getAlarm().getStatus().ordinal()).build();
    }

    private static EPICSEvent.ScalarFloat buildScalarFloat(VFloat v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());
        return EPICSEvent.ScalarFloat.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).setVal(v.getValue())
                .setSeverity(v.getAlarm().getStatus().ordinal()).build();
    }

    private static EPICSEvent.ScalarInt buildScalarInt(VInt v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());
        return EPICSEvent.ScalarInt.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).setVal(v.getValue())
                .setSeverity(v.getAlarm().getStatus().ordinal()).build();
    }
    
    private static EPICSEvent.ScalarShort buildScalarShort(VShort v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());        
        return EPICSEvent.ScalarShort.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).setVal(v.getValue())
                .setSeverity(v.getAlarm().getStatus().ordinal()).build();  
        }   
    
    private static EPICSEvent.ScalarString buildScalarString(VString v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());
        return EPICSEvent.ScalarString.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).setVal(v.getValue())
                .setSeverity(v.getAlarm().getStatus().ordinal()).build();
    }

    private static EPICSEvent.ScalarEnum buildScalarEnum(VEnum v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());
        return EPICSEvent.ScalarEnum.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).setVal(v.getIndex())
                .setSeverity(v.getAlarm().getStatus().ordinal()).build();
    }

    // --- Implementación de Builders de Vectores (Waveforms) ---

    private static EPICSEvent.VectorDouble buildVectorDouble(VDoubleArray v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());
        double[] values = v.getData().toArray(new double[0]);
        List<Double> iterableValues = Arrays.stream(values)
                                  .boxed()
                                  .toList();
        return EPICSEvent.VectorDouble.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).addAllVal(iterableValues)
                .setSeverity(v.getAlarm().getStatus().ordinal()).build();
    }

    private static EPICSEvent.VectorInt buildVectorInt(VIntArray v) {
        TimeInfo ti = getTimeInfo(v.getTime().getTimestamp());
        Integer[] values = v.getData().toArray(new Integer[0]);
        Iterable<Integer> iterableValues = Arrays.asList(values);        
        return EPICSEvent.VectorInt.newBuilder()
                .setSecondsintoyear(ti.sec).setNano(ti.nano).addAllVal(iterableValues)
                .setSeverity(v.getAlarm().getStatus().ordinal()).build();
    }

    // Métodos abreviados para otros tipos numéricos
    
    private static EPICSEvent.ScalarByte buildScalarByte(VByte v) { return null;}
    
    private static Object buildVectorFloat(VFloatArray v) { return null; }
    
    private static Object buildVectorShort(VShortArray v) { return null; }
    
    private static Object buildVectorByte(VByteArray v) { return null; }

    // --- Utilidad de Tiempo ---

    private static class TimeInfo { int sec; int nano; }

    private static TimeInfo getTimeInfo(Instant ts) {
        ZonedDateTime zdt = ZonedDateTime.ofInstant(ts, ZoneId.of("UTC"));
        long yearStart = ZonedDateTime.of(zdt.getYear(), 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toEpochSecond();
        TimeInfo ti = new TimeInfo();
        ti.sec = (int) (ts.getEpochSecond() - yearStart);
        ti.nano = ts.getNano();
        return ti;
    }
}
