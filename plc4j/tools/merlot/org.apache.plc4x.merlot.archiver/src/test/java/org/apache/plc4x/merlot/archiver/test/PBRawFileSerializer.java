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

/**
 *
 * @author cgarcia
 */
import org.epics.vtype.VDouble;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.plc4x.merlot.api.PB.EPICSEvent;
import org.epics.vtype.Alarm;
import org.epics.vtype.AlarmSeverity;
import org.epics.vtype.Display;
import org.epics.vtype.Time;

public class PBRawFileSerializer {

    // Caracter de escape y delimitadores según el protocolo del Archiver
    private static final int ESCAPE = 0x1B;
    private static final int NEWLINE = 0x0A;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        PBRawFileSerializer serializer = new PBRawFileSerializer();
        List<VDouble> randomEvents = new ArrayList<>();
        Random rand = new Random();
        
        // Generar 10 valores aleatorios empezando desde ahora
        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            double randomValue = 20.0 + (30.0 - 20.0) * rand.nextDouble();
            // Incrementamos el tiempo en 1 segundo por cada muestra
            Instant timestamp = now.plusSeconds(i);
            Time ts = Time.of(timestamp);

            randomEvents.add(VDouble.of(randomValue,
                    Alarm.none(),
                    ts,
                    Display.none()));
        }
        
        try {
            String fileName = "random_data.pbraw";
            serializer.serializeToPBRaw(randomEvents, "MY:RANDOM:PV", fileName);
            System.out.println("Archivo '" + fileName + "' generado con 10 valores aleatorios.");
        } catch (IOException e) {
            System.err.println("Error al generar el archivo: " + e.getMessage());
        }
    }       
    
    
    public void serializeToPBRaw(List<VDouble> events, String pvName, String fileName) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            
            // 1. Escribir el PayloadInfo (Metadatos iniciales)
            // El cliente necesita esto para saber que los datos son ScalarDouble
            EPICSEvent.PayloadInfo info = EPICSEvent.PayloadInfo.newBuilder()
                    .setPvname(pvName)
                    .setType(EPICSEvent.PayloadType.SCALAR_DOUBLE)
                    .setYear(2026)                    
                    .setElementCount(1)
                    .build();
            
            info.writeDelimitedTo(fos);

            // 2. Serializar cada VDouble
            for (VDouble vDouble : events) {
                EPICSEvent.ScalarDouble pbEvent = buildProtosEvent(vDouble);
                
                // El cliente PBRAW espera los datos escapados si se transmiten por stream
                // Para un archivo local simple, writeDelimitedTo suele bastar, 
                // pero implementamos el guardado binario puro aquí:
                byte[] eventBytes = pbEvent.toByteArray();
                
                // Escribir tamaño del mensaje (como varint o delimitado)
                writeVarint32(fos, eventBytes.length);
                fos.write(eventBytes);
            }
        }
    }

    private EPICSEvent.ScalarDouble buildProtosEvent(VDouble v) {
        Instant ts = v.getTime().getTimestamp();
        int year = ZonedDateTime.ofInstant(ts, ZoneId.of("UTC")).getYear();
        long yearStart = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toEpochSecond();

        return EPICSEvent.ScalarDouble.newBuilder()
                .setSecondsintoyear((int) (ts.getEpochSecond() - yearStart))
                .setNano(ts.getNano())
                .setVal(v.getValue())
                .setSeverity(v.getAlarm().getStatus().ordinal())
                .build();
    }

    // Utilidad para escribir el prefijo de tamaño que espera el deserializador de Google
    private void writeVarint32(OutputStream out, int value) throws IOException {
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
            
    
    
}
