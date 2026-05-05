/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.plc4x.merlot.archiver.test;

import com.google.protobuf.Extension.MessageType;
import java.io.ObjectInputFilter.Status;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.epics.gpclient.GPClient;
import static org.epics.gpclient.GPClient.channel;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PVReader;
import org.epics.pvaccess.ClientFactory;
import org.epics.pvaccess.client.Channel;
import org.epics.pvaccess.client.ChannelGet;
import org.epics.pvaccess.client.ChannelGetRequester;
import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.client.ChannelProviderRegistryFactory;
import org.epics.pvaccess.client.ChannelRequester;
import org.epics.pvdata.copy.CreateRequest;
import org.epics.pvdata.pv.PVDouble;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.Structure;
import org.epics.vtype.VNumber;
import org.epics.vtype.VType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author luis
 */
public class MerlotGpClientTest {

    //simboliza mi mapa de nombres que leo del archivo cfg
    private String humedad = "humidity";
    private String channelName = "demo.all";
    private static ChannelProvider provider;
    private static CountDownLatch latch;

    @BeforeAll
    public static void setUp() {
        // 1. Iniciar infraestructura de red [3]
        ClientFactory.start();

        latch = new CountDownLatch(1);

        // 2. Obtener el proveedor "pva" [6, 11]
        provider = ChannelProviderRegistryFactory.getChannelProviderRegistry().getProvider("pva");
    }

    @Test
    public void gpClientTest() throws Exception {
        GPClientInstance client = GPClient.defaultInstance();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        PVReader<VType> reader = client.read("pva://training:integral")
                .addReadListener((event, pvReader) -> {
                    VType value = pvReader.getValue();
                    if (value != null) {
                        System.out.println("Dato recibido en listener: " + value);
                        latch.countDown(); 
                    }
                })
                .start();

      
        if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            System.out.println("El tiempo de espera se agotó sin recibir datos.");
        }

        System.out.println("Valor final en el test: " + reader.getValue());
    }

  //  @Test
    public void testReadTemperatureLowLevel() throws Exception {

        // 3. Crear el canal con un Requester [7]
        provider.createChannel(this.channelName, new ChannelRequester() {
            @Override
            public void channelCreated(org.epics.pvdata.pv.Status status, Channel chnl) {
                if (status.isSuccess()) {
                    // 4. Definir el pvRequest para el campo 'temperature' [2]
                    PVStructure pvRequest = CreateRequest.create().createRequest(String.format("field(%s)", humedad));//"field(humidity)"
                    // 5. Crear la operación Get [2]
                    chnl.createChannelGet(new ChannelGetRequester() {
                        @Override
                        public void channelGetConnect(org.epics.pvdata.pv.Status status, ChannelGet cg, Structure strctr) {
                            if (status.isSuccess()) {
                                // 6. Solicitar los datos reales al servidor [8]
                                cg.get();
                            }
                        }

                        @Override
                        public void getDone(org.epics.pvdata.pv.Status status, ChannelGet cg, PVStructure pvs, org.epics.pvdata.misc.BitSet bitset) {
                            if (status.isSuccess()) {
                                // 7. EXTRAER EL VALOR [9, 10]
                                // Accedemos directamente al campo 'temperature' dentro de la estructura
                                PVDouble tempField = pvs.getSubField(PVDouble.class, humedad);
                                if (tempField != null) {
                                    System.out.println("Humidity leida: " + tempField.get());
                                }
                                latch.countDown(); // Liberar el test
                            } else {
                                System.err.println("Error en getDone: " + status.getMessage());
                            }
                        }

                        @Override
                        public String getRequesterName() {
                            return "TestRequester";
                        }

                        @Override
                        public void message(String message, org.epics.pvdata.pv.MessageType messageType) {
                            System.out.println(message);
                        }

                    }, pvRequest);

                }
            }

            @Override
            public void channelStateChange(Channel channel, Channel.ConnectionState connectionState) {
                System.out.println("Estado del canal cambió a: " + connectionState);
            }

            @Override
            public String getRequesterName() {
                return "TestChannelRequester";
            }

            @Override
            public void message(String message, org.epics.pvdata.pv.MessageType messageType) {
                System.out.println("[" + messageType + "] " + message);
            }
        }, ChannelProvider.PRIORITY_DEFAULT);

        // Esperar la respuesta asíncrona
        if (!latch.await(5, TimeUnit.SECONDS)) {
            System.out.println("Timeout: No se pudo leer la variable. Verifica la IP 192.168.31.251");
        }
    }
}
