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
import java.util.HashMap;
import java.util.Map;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.PayloadType;

public enum MerlotTypeMapping {
    SCALAR_FLOAT(PayloadType.SCALAR_FLOAT, VFloat.class),    
    SCALAR_DOUBLE(PayloadType.SCALAR_DOUBLE, VDouble.class),
    SCALAR_INT(PayloadType.SCALAR_INT, VInt.class),
    SCALAR_STRING(PayloadType.SCALAR_STRING, VString.class),
    SCALAR_ENUM(PayloadType.SCALAR_ENUM, VEnum.class),
    VECTOR_DOUBLE(PayloadType.WAVEFORM_DOUBLE, VDoubleArray.class),
    VECTOR_INT(PayloadType.WAVEFORM_INT, VIntArray.class);

    private final PayloadType protoType;
    private final Class<? extends VType> vTypeClass;

    // Mapa para búsqueda rápida inversa
    private static final Map<PayloadType, MerlotTypeMapping> BY_PROTO = new HashMap<>();

    static {
        for (MerlotTypeMapping mapping : values()) {
            BY_PROTO.put(mapping.protoType, mapping);
        }
    }

    MerlotTypeMapping(PayloadType protoType, Class<? extends VType> vTypeClass) {
        this.protoType = protoType;
        this.vTypeClass = vTypeClass;
    }

    public PayloadType getProtoType() { return protoType; }
    public Class<? extends VType> getVTypeClass() { return vTypeClass; }

    /**
     * Obtiene el mapeo a partir del tipo de Protobuf
     */
    public static MerlotTypeMapping fromProto(PayloadType type) {
        return BY_PROTO.get(type);
    }
    
    /**
     * Obtiene el PayloadType correspondiente a una instancia de VType.
     * @param vtype La instancia de datos de EPICS (VDouble, VInt, etc.)
     * @return El PayloadType de EPICSEvent o null si no hay coincidencia.
     */
    public static PayloadType fromVType(VType vtype) {
        if (vtype == null) return null;

        for (MerlotTypeMapping mapping : values()) {
            // Verificamos si la instancia de vtype implementa la clase del mapeo
            if (mapping.getVTypeClass().isInstance(vtype)) {
                return mapping.getProtoType();
            }
        }
        return null;
    }    
}
