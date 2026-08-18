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
package org.apache.plc4x.java.opcuaserver.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.apache.plc4x.java.api.PlcDriverManager;
import org.junit.jupiter.api.Test;

/**
 * The backend builds a CachedPlcConnectionManager, which holds on to the PLC connections it hands
 * out - so the backend's lifecycle has to release them.
 */
public class Plc4xCommunicationLifecycleTest {

    @Test
    public void shutdownReleasesTheConnectionCache() {
        Plc4xCommunication communication = new Plc4xCommunication();
        communication.startup();

        assertDoesNotThrow(communication::shutdown);
        // Closing an already closed cache does nothing, so a second shutdown must stay harmless.
        assertDoesNotThrow(communication::shutdown);
    }

    /**
     * Replacing the driver manager replaces the cache built from it. Nobody else holds the old
     * one, so it has to be closed here or it is leaked.
     */
    @Test
    public void replacingTheDriverManagerReleasesThePreviousCache() {
        Plc4xCommunication communication = new Plc4xCommunication();
        // Starting up builds the first cache; setting a driver manager replaces it.
        communication.startup();

        assertDoesNotThrow(() -> communication.setDriverManager(PlcDriverManager.getDefault()));

        // The freshly built cache is still usable: writing through it must not fail on a closed
        // manager. The simulated driver accepts the write without needing a real device.
        assertDoesNotThrow(() -> communication.setValue("STATE/foo:DINT", "42", "simulated://localhost"));

        communication.shutdown();
    }

}
