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

import org.apache.plc4x.merlot.api.PB.EPICSEvent;
import org.epics.vtype.VType;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Clase utilitaria para la generación de archivos en formato PBRAW compatibles
 * con el EPICS Archiver Appliance.
 */
public final class MerlotPBRawSerializer {

    // Constructor privado para evitar instanciación de clase utilitaria
    private MerlotPBRawSerializer() {}

    /**
     * Serializa una lista de eventos VType a un archivo en formato .pbraw. 
     *
     * @param events  Lista de eventos provenientes de la red de control.
     * @param pvName  Nombre de la Variable de Proceso (PV).
     * @param fileName Nombre del archivo de salida.
     * @throws IOException Si ocurre un error durante la escritura.
     */
    public static void serializeToPBRaw(List<VType> events, String pvName, OutputStream out) throws IOException {
            
        if (events == null || events.isEmpty()) {
            return;
        }

        // 1. Escribir el PayloadInfo (Metadatos obligatorios para pbrawclient) 
        // Se asume el tipo basado en el primer elemento de la lista
        MerlotPayloadMapping mapping = MerlotPayloadMapping.fromVType(events.get(0));
        if (mapping == null) {
            throw new IOException("Tipo de VType no soportado para serialización.");
        }

        EPICSEvent.PayloadInfo info = EPICSEvent.PayloadInfo.newBuilder()
                .setPvname(pvName)
                .setType(MerlotTypeMapping.fromVType(events.get(0)))
                .setElementCount(1)
                .build();

        info.writeDelimitedTo(out);

        // 2. Serializar cada evento VType usando la factoría MerlotPayloadMapping 
        for (VType vType : events) {
            Object pbEvent = MerlotPayloadMapping.createEvent(vType);

            if (pbEvent != null) {
                writeEventToStream(out, pbEvent);
            }
        }
    }

    /**
     * Escribe el objeto de Protocol Buffers en el stream usando formato delimitado. 
     */
    private static void writeEventToStream(OutputStream out, Object pbEvent) throws IOException {
        if (pbEvent instanceof com.google.protobuf.MessageLite) {
            ((com.google.protobuf.MessageLite) pbEvent).writeDelimitedTo(out);
        } else {
            // Manejo manual de varint si no es un mensaje directo de Protobuf
            byte[] bytes = serializeToBytes(pbEvent);
            if (bytes != null) {
                writeVarint32(out, bytes.length);
                out.write(bytes);
            }
        }
    }

    /**
     * Utilidad para escribir el prefijo de tamaño (Varint32) requerido por el protocolo. 
     */
    private static void writeVarint32(OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            } else {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    /**
     * Convierte el objeto del evento en su representación de bytes. 
     */
    private static byte[] serializeToBytes(Object pbEvent) {
        if (pbEvent instanceof EPICSEvent.ScalarDouble) {
            return ((EPICSEvent.ScalarDouble) pbEvent).toByteArray();
        } else if (pbEvent instanceof EPICSEvent.ScalarInt) {
            return ((EPICSEvent.ScalarInt) pbEvent).toByteArray();
        }
        // Añadir otros tipos según sea necesario
        return null;
    }
}
