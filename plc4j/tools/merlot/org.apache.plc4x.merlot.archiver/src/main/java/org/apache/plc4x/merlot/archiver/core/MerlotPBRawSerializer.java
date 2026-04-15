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
import org.apache.plc4x.merlot.api.PB.EPICSEvent;
import org.epics.vtype.VType;
import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarDouble;
import org.epics.vtype.AlarmProvider;
import org.epics.vtype.Time;
import org.epics.vtype.TimeProvider;
import org.epics.vtype.VByte;
import org.epics.vtype.VDouble;
import org.epics.vtype.VFloat;
import org.epics.vtype.VInt;
import org.epics.vtype.VString;
import org.slf4j.LoggerFactory;

/**
 * Utility class for generating PBRAW-format files compatible with the EPICS
 * Archiver Appliance.
 */
public final class MerlotPBRawSerializer {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotPBRawSerializer.class);

    private MerlotPBRawSerializer() {
    }

    public static void serializeIoTDBToPBRaw(List<VType> events, String pvName, OutputStream out) throws IOException {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("The list of events is empty or null");
        }

        VType primerElemento = events.get(0);
        EPICSEvent.PayloadType type;
        int year;

        if (primerElemento instanceof VDouble) {
            type = EPICSEvent.PayloadType.SCALAR_DOUBLE;
            year = ((VDouble) primerElemento).getTime().getTimestamp().atOffset(ZoneOffset.UTC).getYear();
        } else if (primerElemento instanceof VInt) {
            type = EPICSEvent.PayloadType.SCALAR_INT;
            year = ((VInt) primerElemento).getTime().getTimestamp().atOffset(ZoneOffset.UTC).getYear();
        } else if (primerElemento instanceof VFloat) {
            type = EPICSEvent.PayloadType.SCALAR_FLOAT;
            year = ((VFloat) primerElemento).getTime().getTimestamp().atOffset(ZoneOffset.UTC).getYear();
        } else if (primerElemento instanceof VString) {
            type = EPICSEvent.PayloadType.SCALAR_STRING;
            year = ((VString) primerElemento).getTime().getTimestamp().atOffset(ZoneOffset.UTC).getYear();
        } else if (primerElemento instanceof VByte) {
            type = EPICSEvent.PayloadType.SCALAR_BYTE;
            year = ((VByte) primerElemento).getTime().getTimestamp().atOffset(ZoneOffset.UTC).getYear();
        } else {
            throw new UnsupportedOperationException("Class not supported for mapping: " + primerElemento.getClass().getName());
        }

        // Response to Phoebus
        EPICSEvent.PayloadInfo info = EPICSEvent.PayloadInfo.newBuilder()
                .setPvname(pvName)
                .setType(type)
                .setYear(year)
                .setElementCount(1)
                .build();
        out.write(info.toByteArray());
        out.write('\n');

        for (int i = 0; i < events.size(); i++) {
            byte[] data = serializeIoTDBToBytes(events.get(i));

            //Samples converted to PB
            if (data != null) {

                out.write(data);
                out.write('\n');
            } else {
                LOGGER.warn("The event {} could not be serialized (null)", i);
            }
        }

        out.flush();
    }

    /**
     * Serializa una lista de eventos VType a un archivo en formato .pbraw.
     *
     * @param events Lista de eventos provenientes de la red de control.
     * @param pvName Nombre de la Variable de Proceso (PV).
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
                .setType(EPICSEvent.PayloadType.SCALAR_DOUBLE)
                .setYear(((VDouble) events.get(0))
                        .getTime()
                        .getTimestamp()
                        .atOffset(ZoneOffset.UTC).getYear())
                .setElementCount(1)
                .build();

//        info.writeDelimitedTo(out);
        out.write(info.toByteArray());
        out.write('\n');

//        
//            // Tiempo actual
//            Instant now = Instant.now();
//            ZonedDateTime zdt = now.atZone(ZoneId.systemDefault());
//
//            int year = zdt.getYear();
//
//            int secondsIntoYear
//                    = (zdt.getDayOfYear() - 1) * 86400
//                    + zdt.getHour() * 3600
//                    + zdt.getMinute() * 60
//                    + zdt.getSecond();        
        // 2. Serializar cada evento VType usando la factoría MerlotPayloadMapping 
        for (VType vType : events) {
            Object pbEvent = MerlotPayloadMapping.createEvent(vType);

            if (pbEvent != null) {
//                writeEventToStream(out, pbEvent);
                out.write(((ScalarDouble) pbEvent).toByteArray());

                out.write('\n');
            }
        }
//        
//            ScalarDouble event = ScalarDouble.newBuilder()
//                    .setSecondsintoyear(secondsIntoYear)
//                    .setNano(zdt.getNano())
//                    .setVal(10.0)
//                    .setSeverity(0)
//                    .setStatus(0)
//                    .build();
//
//            out.write(event.toByteArray());        

        out.flush();
    }

    /**
     * Serializa una lista de eventos Type a un archivo en formato .pbraw.
     *
     * @param events Lista de eventos provenientes de IoTDB Database.
     * @param pvName Nombre de la Variable de Proceso (PV).
     * @param fileName Nombre del archivo de salida.
     * @throws IOException Si ocurre un error durante la escritura.
     */
    /**
     * Escribe el objeto de Protocol Buffers en el stream usando formato
     * delimitado.
     */
    private static void writeEventToStream(OutputStream out, Object pbEvent) throws IOException {
        if (pbEvent instanceof com.google.protobuf.MessageLite) {
            ((com.google.protobuf.MessageLite) pbEvent).writeDelimitedTo(out);
        } else {
            // Manejo manual de varint si no es un mensaje directo de Protobuf
            byte[] bytes = serializeToBytes(pbEvent);
            if (bytes != null) {
//                writeVarint32(out, bytes.length);
                out.write(bytes);
                out.write(0x0A);
            }
        }
    }

    /**
     * Utilidad para escribir el prefijo de tamaño (Varint32) requerido por el
     * protocolo.
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
        } else if (pbEvent instanceof EPICSEvent.ScalarFloat) {
            return ((EPICSEvent.ScalarFloat) pbEvent).toByteArray();
        } else if (pbEvent instanceof EPICSEvent.ScalarByte) {
            return ((EPICSEvent.ScalarByte) pbEvent).toByteArray();
        }
        // Añadir otros tipos según sea necesario
        return null;
    }

    private static byte[] serializeIoTDBToBytes(VType event) {
        if (event == null) {
            return null;
        }

        Time eventTime = ((TimeProvider) event).getTime();
        OffsetDateTime time = eventTime.getTimestamp().atOffset(ZoneOffset.UTC);
        int secondsIntoYear = (int) (time.toEpochSecond()
                - time.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).toEpochSecond());
        int nanos = eventTime.getTimestamp().getNano();
        int severity = ((AlarmProvider) event).getAlarm().getSeverity().ordinal();

        if (event instanceof VDouble) {
            return EPICSEvent.ScalarDouble.newBuilder()
                    .setSecondsintoyear(secondsIntoYear)
                    .setNano(nanos)
                    .setVal(((VDouble) event).getValue())
                    .setSeverity(severity)
                    .build().toByteArray();

        } else if (event instanceof VInt) {
            return EPICSEvent.ScalarInt.newBuilder()
                    .setSecondsintoyear(secondsIntoYear)
                    .setNano(nanos)
                    .setVal(((VInt) event).getValue())
                    .setSeverity(severity)
                    .build().toByteArray();

        } else if (event instanceof VFloat) {
            return EPICSEvent.ScalarFloat.newBuilder()
                    .setSecondsintoyear(secondsIntoYear)
                    .setNano(nanos)
                    .setVal(((VFloat) event).getValue())
                    .setSeverity(severity)
                    .build().toByteArray();

        } else if (event instanceof VString) {
            return EPICSEvent.ScalarString.newBuilder()
                    .setSecondsintoyear(secondsIntoYear)
                    .setNano(nanos)
                    .setVal(((VString) event).getValue())
                    .setSeverity(severity)
                    .build().toByteArray();

        } else if (event instanceof VByte) {
            return EPICSEvent.ScalarByte.newBuilder()
                    .setSecondsintoyear(secondsIntoYear)
                    .setNano(nanos)
                    .setVal(ByteString.copyFrom(new byte[]{((VByte) event).getValue()}))
                    .setSeverity(severity)
                    .build().toByteArray();
        }
        //If the type is not supported
        return null;
    }
}
