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

import org.apache.plc4x.java.spi.transports.api.RingBuffer;
import org.apache.plc4x.java.spi.transports.api.TransportInstance;
import org.apache.plc4x.java.spi.transports.api.config.TransportConfiguration;
import org.apache.plc4x.java.spi.transports.api.exceptions.TransportException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Adapts an accepted server-side {@link Socket} to the SPI3 {@link TransportInstance} contract
 * so the regular PLC4X message codec can be reused on the server side.
 *
 * <p>SPI3 has no server transport (it is purely client/outbound), but a {@code MessageCodecBase}
 * only needs the byte-level peek/read/write surface. Inbound bytes are pulled from the socket by
 * {@link #fill()} into a {@link RingBuffer}; the codec then drains complete messages from that
 * buffer via {@link #getNumBytesAvailable()} / {@link #peekReadableBytes(int)} / {@link #read(int)}.
 * The maximum PLC4X proxy message is bounded by a {@code uint16} length, so a buffer slightly
 * larger than 64&nbsp;KiB always holds a full frame.</p>
 */
public class SocketTransportInstance implements TransportInstance<TransportConfiguration> {

    private static final int BUFFER_CAPACITY = 256 * 1024;
    private static final int READ_CHUNK = 8 * 1024;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final RingBuffer ringBuffer = new RingBuffer(BUFFER_CAPACITY);
    private final byte[] readChunk = new byte[READ_CHUNK];

    public SocketTransportInstance(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /**
     * Blocks until at least one byte arrives from the socket and appends it to the ring buffer.
     *
     * @return {@code false} on end-of-stream (peer closed), {@code true} otherwise.
     */
    public boolean fill() throws IOException {
        int read = in.read(readChunk);
        if (read == -1) {
            return false;
        }
        if (read > 0) {
            ringBuffer.write(readChunk, 0, read);
        }
        return true;
    }

    @Override
    public TransportConfiguration getConfiguration() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed() && socket.isConnected();
    }

    @Override
    public int getNumBytesAvailable() {
        return ringBuffer.availableForReading();
    }

    @Override
    public byte[] peekReadableBytes(int numBytes) {
        return ringBuffer.peek(numBytes);
    }

    @Override
    public byte[] read(int numBytes) {
        return ringBuffer.read(numBytes);
    }

    @Override
    public void write(byte[] bytes) throws TransportException {
        try {
            synchronized (out) {
                out.write(bytes);
                out.flush();
            }
        } catch (IOException e) {
            throw new TransportException("Failed to write to socket", e);
        }
    }

    @Override
    public void close() throws TransportException {
        try {
            socket.close();
        } catch (IOException e) {
            throw new TransportException("Failed to close socket", e);
        }
    }

}
