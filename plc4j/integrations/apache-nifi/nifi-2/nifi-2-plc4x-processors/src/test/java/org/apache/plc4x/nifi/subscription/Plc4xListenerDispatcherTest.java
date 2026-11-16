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
package org.apache.plc4x.nifi.subscription;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.nifi.logging.ComponentLog;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionFactory;
import org.apache.plc4x.java.api.messages.PlcSubscriptionEvent;
import org.apache.plc4x.java.api.messages.PlcSubscriptionRequest;
import org.apache.plc4x.java.api.messages.PlcSubscriptionResponse;
import org.apache.plc4x.java.api.metadata.PlcConnectionMetadata;
import org.apache.plc4x.java.api.model.PlcSubscriptionHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A driver can keep producing events after the device is gone - an OPC UA CYCLIC subscription
 * re-reports the last known values - so the dispatcher must not rely on silence alone to notice a
 * lost connection, or it never reconnects.
 */
public class Plc4xListenerDispatcherTest {

    private static final long TIMEOUT_MS = 5_000L;

    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicReference<Consumer<PlcSubscriptionEvent>> driverConsumer = new AtomicReference<>();
    private final BlockingQueue<PlcSubscriptionEvent> events = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService driver = Executors.newSingleThreadScheduledExecutor();
    private PlcConnection connection;
    private Plc4xListenerDispatcher dispatcher;

    @BeforeEach
    public void setUp() throws Exception {
        connection = mock(PlcConnection.class);
        when(connection.isConnected()).thenAnswer(invocation -> connected.get());
        PlcConnectionMetadata metadata = mock(PlcConnectionMetadata.class);
        when(metadata.isSubscribeSupported()).thenReturn(true);
        when(connection.getMetadata()).thenReturn(metadata);

        PlcSubscriptionHandle handle = mock(PlcSubscriptionHandle.class);
        when(handle.register(any())).thenAnswer(invocation -> {
            driverConsumer.set(invocation.getArgument(0));
            return null;
        });
        PlcSubscriptionResponse response = mock(PlcSubscriptionResponse.class);
        when(response.getSubscriptionHandles()).thenReturn(List.of(handle));
        PlcSubscriptionRequest request = mock(PlcSubscriptionRequest.class);
        when(request.execute()).thenAnswer(invocation -> CompletableFuture.completedFuture(response));
        PlcSubscriptionRequest.Builder builder = mock(PlcSubscriptionRequest.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(request);
        when(connection.subscriptionRequestBuilder()).thenReturn(builder);

        PlcConnectionFactory factory = mock(PlcConnectionFactory.class);
        when(factory.getConnection(anyString())).thenReturn(connection);

        dispatcher = new Plc4xListenerDispatcher(TIMEOUT_MS, Plc4xSubscriptionType.CYCLIC, 100L,
            mock(ComponentLog.class), events, factory);
        dispatcher.open("opcua:tcp://127.0.0.1:4840", Map.of("value", "ns=2;i=1"));
    }

    @AfterEach
    public void tearDown() {
        driver.shutdownNow();
        dispatcher.close();
    }

    @Test
    public void forwardsEventsWhileConnected() throws InterruptedException {
        Thread reader = start();
        driverConsumer.get().accept(mock(PlcSubscriptionEvent.class));

        assertTrue(events.poll(2, TimeUnit.SECONDS) != null, "event not forwarded");
        assertTrue(dispatcher.isRunning());
        assertTrue(reader.isAlive());
    }

    @Test
    public void closesWhenTheConnectionIsLostWhileEventsKeepComing() throws Exception {
        Thread reader = start();
        // The driver keeps reporting every 100ms - well inside the timeout - before and after the loss.
        driver.scheduleAtFixedRate(() -> driverConsumer.get().accept(mock(PlcSubscriptionEvent.class)),
            0, 100, TimeUnit.MILLISECONDS);
        assertTrue(events.poll(2, TimeUnit.SECONDS) != null, "event not forwarded");

        connected.set(false);

        reader.join(2_000);
        assertFalse(reader.isAlive(), "dispatcher kept running on a lost connection");
        assertFalse(dispatcher.isRunning());
        verify(connection).close();
    }

    private Thread start() {
        assertTrue(dispatcher.isRunning());
        Thread reader = new Thread(dispatcher, "dispatcher-under-test");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }
}
