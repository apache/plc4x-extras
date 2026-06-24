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
package org.apache.plc4x.java.tools.plc4xserver.protocol;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.plc4x.Plc4xMessageCodec;
import org.apache.plc4x.java.plc4x.readwrite.*;
import org.apache.plc4x.java.spi.drivers.exceptions.MessageCodecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Per-connection handler for the PLC4X proxy protocol. One instance lives for the lifetime of a
 * single accepted socket; the {@link SocketTransportInstance} read loop feeds the codec, which
 * invokes {@link #handle(Plc4xMessage)} for every decoded message.
 *
 * <p>Authentication is mandatory: a connection must complete a successful {@code AUTH_REQUEST}
 * before any {@code CONNECT}/{@code READ}/{@code WRITE} is honoured. Anything else is answered
 * with {@code ACCESS_DENIED}.</p>
 */
public class Plc4xServerAdapter implements Consumer<Plc4xMessage> {

    private final Logger logger = LoggerFactory.getLogger(Plc4xServerAdapter.class);

    private final PlcConnectionManager connectionManager;
    private final Plc4xMessageCodec codec;
    private final byte[] expectedUsername;
    private final byte[] expectedPassword;

    private final AtomicInteger connectionIdGenerator = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, String> connectionUrls = new ConcurrentHashMap<>();

    private volatile boolean authenticated = false;

    public Plc4xServerAdapter(PlcConnectionManager connectionManager, Plc4xMessageCodec codec,
                              String expectedUsername, String expectedPassword) {
        this.connectionManager = connectionManager;
        this.codec = codec;
        this.expectedUsername = expectedUsername.getBytes(StandardCharsets.UTF_8);
        this.expectedPassword = expectedPassword.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void accept(Plc4xMessage message) {
        handle(message);
    }

    public void handle(Plc4xMessage plc4xMessage) {
        switch (plc4xMessage.getRequestType()) {
            case AUTH_REQUEST:
                handleAuth((Plc4xAuthRequest) plc4xMessage);
                break;
            case CONNECT_REQUEST:
                if (requireAuth(plc4xMessage)) {
                    handleConnect((Plc4xConnectRequest) plc4xMessage);
                }
                break;
            case READ_REQUEST:
                if (requireAuth(plc4xMessage)) {
                    handleRead((Plc4xReadRequest) plc4xMessage);
                }
                break;
            case WRITE_REQUEST:
                if (requireAuth(plc4xMessage)) {
                    handleWrite((Plc4xWriteRequest) plc4xMessage);
                }
                break;
            default:
                logger.debug("Ignoring unsupported request type {}", plc4xMessage.getRequestType());
        }
    }

    private void handleAuth(Plc4xAuthRequest request) {
        boolean ok = constantTimeEquals(expectedUsername, request.getUsername().getBytes(StandardCharsets.UTF_8))
            & constantTimeEquals(expectedPassword, request.getPassword().getBytes(StandardCharsets.UTF_8));
        authenticated = ok;
        // Never log the supplied credentials, just the outcome.
        logger.info("Authentication {}", ok ? "succeeded" : "failed");
        send(new Plc4xAuthResponse(request.getRequestId(),
            ok ? Plc4xResponseCode.OK : Plc4xResponseCode.ACCESS_DENIED));
    }

    /**
     * Returns {@code true} if the connection is authenticated. Otherwise emits a type-appropriate
     * {@code ACCESS_DENIED} response and returns {@code false}.
     */
    private boolean requireAuth(Plc4xMessage message) {
        if (authenticated) {
            return true;
        }
        logger.warn("Rejecting {} - connection is not authenticated", message.getRequestType());
        switch (message.getRequestType()) {
            case CONNECT_REQUEST -> send(new Plc4xConnectResponse(
                message.getRequestId(), 0, Plc4xResponseCode.ACCESS_DENIED));
            case READ_REQUEST -> send(new Plc4xReadResponse(message.getRequestId(),
                ((Plc4xReadRequest) message).getConnectionId(), Plc4xResponseCode.ACCESS_DENIED,
                Collections.emptyList()));
            case WRITE_REQUEST -> send(new Plc4xWriteResponse(message.getRequestId(),
                ((Plc4xWriteRequest) message).getConnectionId(), Plc4xResponseCode.ACCESS_DENIED,
                Collections.emptyList()));
            default -> { /* nothing to answer */ }
        }
        return false;
    }

    private void handleConnect(Plc4xConnectRequest request) {
        try (final PlcConnection ignored = connectionManager.getConnection(request.getConnectionString())) {
            final int connectionId = connectionIdGenerator.getAndIncrement();
            connectionUrls.put(connectionId, request.getConnectionString());
            send(new Plc4xConnectResponse(request.getRequestId(), connectionId, Plc4xResponseCode.OK));
        } catch (Exception e) {
            send(new Plc4xConnectResponse(request.getRequestId(), 0, Plc4xResponseCode.INVALID_ADDRESS));
        }
    }

    private void handleRead(Plc4xReadRequest request) {
        String connectionUrl = connectionUrls.get(request.getConnectionId());
        try (final PlcConnection connection = connectionManager.getConnection(connectionUrl)) {
            final PlcReadRequest.Builder builder = connection.readRequestBuilder();
            for (Plc4xTagRequest requestTag : request.getTags()) {
                builder.addTagAddress(requestTag.getTag().getName(), requestTag.getTag().getTagQuery());
            }
            final PlcReadRequest rr = builder.build();

            // Execute synchronously (required when working with the connection cache).
            final PlcReadResponse apiReadResponse = rr.execute().get();

            List<Plc4xTagValueResponse> tags = new ArrayList<>(apiReadResponse.getTagNames().size());
            for (Plc4xTagRequest plc4xRequestTag : request.getTags()) {
                final PlcResponseCode responseCode =
                    apiReadResponse.getResponseCode(plc4xRequestTag.getTag().getName());
                Plc4xResponseCode resCode;
                Plc4xValueType valueType;
                PlcValue value;
                if (responseCode == PlcResponseCode.OK) {
                    resCode = Plc4xResponseCode.OK;
                    value = apiReadResponse.getPlcValue(plc4xRequestTag.getTag().getName());
                    final String valueTypeName = value.getClass().getSimpleName();
                    // Cut off the "Plc" prefix to get the name of the PlcValueType.
                    valueType = Plc4xValueType.valueOf(valueTypeName.substring(3));
                } else {
                    resCode = Plc4xResponseCode.INVALID_ADDRESS;
                    value = null;
                    valueType = Plc4xValueType.NULL;
                }
                tags.add(new Plc4xTagValueResponse(plc4xRequestTag.getTag(), resCode, valueType, value));
            }
            send(new Plc4xReadResponse(request.getRequestId(), request.getConnectionId(),
                Plc4xResponseCode.OK, tags));
        } catch (Exception e) {
            logger.error("Error executing read request", e);
            send(new Plc4xReadResponse(request.getRequestId(), request.getConnectionId(),
                Plc4xResponseCode.INVALID_ADDRESS, Collections.emptyList()));
        }
    }

    private void handleWrite(Plc4xWriteRequest request) {
        String connectionUrl = connectionUrls.get(request.getConnectionId());
        try (final PlcConnection connection = connectionManager.getConnection(connectionUrl)) {
            final PlcWriteRequest.Builder builder = connection.writeRequestBuilder();
            for (Plc4xTagValueRequest plc4xRequestTag : request.getTags()) {
                builder.addTagAddress(plc4xRequestTag.getTag().getName(),
                    plc4xRequestTag.getTag().getTagQuery(), plc4xRequestTag.getValue().getObject());
            }
            final PlcWriteRequest apiWriteRequest = builder.build();

            // Execute synchronously (required when working with the connection cache).
            final PlcWriteResponse apiWriteResponse = apiWriteRequest.execute().get();

            List<Plc4xTagResponse> plc4xTags = new ArrayList<>(apiWriteResponse.getTagNames().size());
            for (Plc4xTagValueRequest plc4xRequestTag : request.getTags()) {
                final PlcResponseCode apiResponseCode =
                    apiWriteResponse.getResponseCode(plc4xRequestTag.getTag().getName());
                Plc4xResponseCode resCode = apiResponseCode == PlcResponseCode.OK
                    ? Plc4xResponseCode.OK : Plc4xResponseCode.INVALID_ADDRESS;
                plc4xTags.add(new Plc4xTagResponse(plc4xRequestTag.getTag(), resCode));
            }
            send(new Plc4xWriteResponse(request.getRequestId(), request.getConnectionId(),
                Plc4xResponseCode.OK, plc4xTags));
        } catch (Exception e) {
            logger.error("Error executing write request", e);
            send(new Plc4xWriteResponse(request.getRequestId(), request.getConnectionId(),
                Plc4xResponseCode.INVALID_ADDRESS, Collections.emptyList()));
        }
    }

    private void send(Plc4xMessage message) {
        try {
            codec.send(message);
        } catch (MessageCodecException e) {
            logger.error("Failed to send response", e);
        }
    }

    /**
     * Length-aware constant-time comparison to avoid leaking credential length/content via timing.
     */
    private static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        return MessageDigest.isEqual(expected, actual);
    }

}
