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

import com.google.protobuf.ByteString;
import java.util.function.BiFunction;
import org.apache.iotdb.pipe.api.type.Type;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarByte;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarDouble;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarFloat;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarInt;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarString;

public enum MerlotIoTDBMapping {
    INT32(Type.INT32, (val, meta)
            -> ScalarInt.newBuilder()
                    .setSecondsintoyear(meta.seconds)
                    .setNano(meta.nano)
                    .setVal((Integer) val)
                    .setSeverity(meta.severity)
                    .setStatus(meta.status)
                    .build()),
    
    INT64(Type.INT64, (val, meta) -> {
        long numericValue = ((Number) val).longValue();

        return org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarDouble.newBuilder()
                .setSecondsintoyear(meta.seconds)
                .setNano(meta.nano)
                .setVal((double) numericValue)
                .setSeverity(meta.severity)
                .setStatus(meta.status)
                .build();
    }),
    
    FLOAT(Type.FLOAT, (val, meta)
            -> ScalarFloat.newBuilder()
                    .setSecondsintoyear(meta.seconds)
                    .setNano(meta.nano)
                    .setVal((Float) val)
                    .setSeverity(meta.severity)
                    .setStatus(meta.status)
                    .build()),
    DOUBLE(Type.DOUBLE, (val, meta)
            -> ScalarDouble.newBuilder()
                    .setSecondsintoyear(meta.seconds)
                    .setNano(meta.nano)
                    .setVal((Double) val)
                    .setSeverity(meta.severity)
                    .setStatus(meta.status)
                    .build()),
    TEXT(Type.TEXT, (val, meta)
            -> ScalarString.newBuilder()
                    .setSecondsintoyear(meta.seconds)
                    .setNano(meta.nano)
                    .setVal(val.toString())
                    .setSeverity(meta.severity)
                    .setStatus(meta.status)
                    .build()),
    BOOLEAN(Type.BOOLEAN, (val, meta)
            -> ScalarByte.newBuilder()
                    .setSecondsintoyear(meta.seconds)
                    .setNano(meta.nano)
                    .setVal(ByteString.copyFrom(new byte[]{(byte) ((boolean) val ? 1 : 0)}))
                    .setSeverity(meta.severity)
                    .setStatus(meta.status)
                    .build());

   
    private final Type iotdbType;
    private final BiFunction<Object, EpicsMetadata, Object> converter;

    MerlotIoTDBMapping(Type iotdbType, BiFunction<Object, EpicsMetadata, Object> converter) {
        this.iotdbType = iotdbType;
        this.converter = converter;
    }

    public Object convert(Object value, EpicsMetadata meta) {
        return this.converter.apply(value, meta);
    }

    public static MerlotIoTDBMapping fromIotdb(Type type) {
        for (MerlotIoTDBMapping c : values()) {
            if (c.iotdbType == type) {
                return c;
            }
        }
        throw new UnsupportedOperationException("IoTDB type not supported: " + type);
    }

    /**
     * Container for additional fields defined in EPICSEvent.proto
     */
    public static class EpicsMetadata {

        public final int seconds;
        public final int nano;
        public final int severity;
        public final int status;

        public EpicsMetadata(int seconds, int nano, int severity, int status) {
            this.seconds = seconds;
            this.nano = nano;
            this.severity = severity;
            this.status = status;
        }
    }
}
