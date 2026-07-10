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

package org.apache.plc4x.java.tools.plc4xserver;

import static java.lang.Runtime.getRuntime;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.plc4x.Plc4xMessageCodec;
import org.apache.plc4x.java.plc4x.readwrite.Constants;
import org.apache.plc4x.java.spi.drivers.exceptions.MessageCodecException;
import org.apache.plc4x.java.tools.plc4xserver.protocol.Plc4xServerAdapter;
import org.apache.plc4x.java.tools.plc4xserver.protocol.SocketTransportInstance;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP/TLS server that relays {@code plc4x} proxy requests to PLCs reachable from this machine.
 *
 * <p>Security model:</p>
 * <ul>
 *   <li><b>Authentication is mandatory.</b> Every connection must present a username and
 *   password before any operation. If none are configured the server uses the default user
 *   {@code toddy} with a freshly generated secure password that is printed to the console once
 *   at startup. Explicitly configured credentials are never logged.</li>
 *   <li><b>TLS is the default transport.</b> Without a configured keystore the server generates
 *   an ephemeral self-signed certificate and prints its SHA-256 fingerprint so clients can pin
 *   it. Plaintext TCP is available as an explicit opt-in for trusted networks/testing.</li>
 * </ul>
 */
public class Plc4xServer {

    public static final String SERVER_PORT_PROPERTY = "plc4x.server.port";
    public static final String SERVER_PORT_ENVIRONMENT_VARIABLE = "PLC4X_SERVER_PORT";
    public static final String SERVER_USERNAME_PROPERTY = "plc4x.server.username";
    public static final String SERVER_USERNAME_ENVIRONMENT_VARIABLE = "PLC4X_SERVER_USERNAME";
    public static final String SERVER_PASSWORD_PROPERTY = "plc4x.server.password";
    public static final String SERVER_PASSWORD_ENVIRONMENT_VARIABLE = "PLC4X_SERVER_PASSWORD";
    public static final String SERVER_PLAINTEXT_PROPERTY = "plc4x.server.plaintext";
    public static final String SERVER_PLAINTEXT_ENVIRONMENT_VARIABLE = "PLC4X_SERVER_PLAINTEXT";
    public static final String SERVER_KEYSTORE_PROPERTY = "plc4x.server.keystore";
    public static final String SERVER_KEYSTORE_PASSWORD_PROPERTY = "plc4x.server.keystore-password";

    public static final String DEFAULT_USERNAME = "toddy";
    public static int DEFAULT_PORT = Constants.PLC4XTCPDEFAULTPORT;

    private static final Logger LOG = LoggerFactory.getLogger(Plc4xServer.class);

    private final PlcConnectionManager connectionManager = CachedPlcConnectionManager.getBuilder()
        .withConnectionManager(new DefaultPlcDriverManager())
        .build();

    private Integer port;
    private String username;
    private String password;
    private boolean plaintext = false;
    private String keystorePath;
    private String keystorePassword;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService connectionExecutor;
    private volatile boolean running = false;

    public static void main(String[] args) throws Exception {
        final Plc4xServer server = new Plc4xServer();

        Future<Void> serverFuture = server.start(
            Arrays_findFirst(args) // port number given as first command line argument
                .or(() -> Optional.ofNullable(System.getProperty(SERVER_PORT_PROPERTY)))
                .or(() -> Optional.ofNullable(System.getenv(SERVER_PORT_ENVIRONMENT_VARIABLE)))
                .map(Integer::parseInt)
                .orElse(DEFAULT_PORT)
        );
        CompletableFuture<Void> serverRunning = new CompletableFuture<>();
        getRuntime().addShutdownHook(new Thread(() -> serverRunning.complete(null)));

        try {
            LOG.info("Server is configured to listen on TCP port {}", server.getPort());
            serverFuture.get();
            LOG.info("Server is ready.");
            serverRunning.get();
        } catch (InterruptedException e) {
            throw new PlcRuntimeException(e);
        } finally {
            LOG.info("Server is shutting down...");
            server.stop();
        }
    }

    private static Optional<String> Arrays_findFirst(String[] args) {
        return args.length > 0 ? Optional.of(args[0]) : Optional.empty();
    }

    public Integer getPort() {
        return port;
    }

    /**
     * The effective username clients must authenticate with.
     */
    public String getUsername() {
        return username;
    }

    /**
     * The effective password clients must authenticate with (generated if none was configured).
     */
    public String getPassword() {
        return password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPlaintext(boolean plaintext) {
        this.plaintext = plaintext;
    }

    public void setKeystore(String keystorePath, String keystorePassword) {
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
    }

    public Future<Void> start() {
        return start(0);
    }

    public Future<Void> start(int port) {
        if (running) {
            return CompletableFuture.completedFuture(null);
        }
        this.port = (port == 0) ? findRandomFreePort() : port;

        resolveCredentials();
        resolvePlaintext();

        try {
            serverSocket = plaintext ? new ServerSocket(this.port) : createTlsServerSocket(this.port);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                new PlcRuntimeException("Failed to start PLC4X server", e));
        }

        running = true;
        connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        acceptThread = new Thread(this::acceptLoop, "Plc4xServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        LOG.info("PLC4X server listening on port {} ({})", this.port, plaintext ? "plaintext TCP" : "TLS");
        return CompletableFuture.completedFuture(null);
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.debug("Error closing server socket", e);
            }
            serverSocket = null;
        }
        if (connectionExecutor != null) {
            connectionExecutor.shutdownNow();
            connectionExecutor = null;
        }
    }

    private void acceptLoop() {
        while (running) {
            final Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running) {
                    LOG.debug("Accept failed", e);
                }
                return;
            }
            connectionExecutor.submit(() -> handleConnection(socket));
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            SocketTransportInstance transport = new SocketTransportInstance(socket);
            // The codec needs a message handler and the adapter needs the codec to send replies,
            // so wire them through a one-slot holder to break the construction cycle.
            final Plc4xServerAdapter[] holder = new Plc4xServerAdapter[1];
            Plc4xMessageCodec codec = new Plc4xMessageCodec(transport, msg -> holder[0].handle(msg));
            holder[0] = new Plc4xServerAdapter(connectionManager, codec, username, password);

            while (running && transport.fill()) {
                codec.processIncomingData();
            }
        } catch (MessageCodecException e) {
            LOG.debug("Protocol error - dropping connection", e);
        } catch (IOException e) {
            LOG.debug("Connection I/O error", e);
        }
    }

    private SSLServerSocket createTlsServerSocket(int port) throws Exception {
        ServerTlsContext tlsContext;
        String keystore = keystorePath != null ? keystorePath : System.getProperty(SERVER_KEYSTORE_PROPERTY);
        if (keystore != null) {
            String pwd = keystorePassword != null
                ? keystorePassword : System.getProperty(SERVER_KEYSTORE_PASSWORD_PROPERTY);
            tlsContext = ServerTlsContext.fromKeystore(keystore, pwd, null);
        } else {
            tlsContext = ServerTlsContext.selfSigned();
            LOG.info("No keystore configured - generated an ephemeral self-signed certificate.");
            LOG.info("Server certificate SHA-256 fingerprint: {}", tlsContext.getCertificateFingerprint());
        }
        SSLServerSocketFactory factory = tlsContext.getSslContext().getServerSocketFactory();
        return (SSLServerSocket) factory.createServerSocket(port);
    }

    /**
     * Resolves the effective credentials from explicit config, system properties or environment,
     * falling back to the default user with a generated password. Generated passwords are printed
     * once; configured passwords are never logged.
     */
    private void resolveCredentials() {
        if (username == null) {
            username = Optional.ofNullable(System.getProperty(SERVER_USERNAME_PROPERTY))
                .or(() -> Optional.ofNullable(System.getenv(SERVER_USERNAME_ENVIRONMENT_VARIABLE)))
                .orElse(DEFAULT_USERNAME);
        }
        boolean generated = false;
        if (password == null) {
            String configured = Optional.ofNullable(System.getProperty(SERVER_PASSWORD_PROPERTY))
                .or(() -> Optional.ofNullable(System.getenv(SERVER_PASSWORD_ENVIRONMENT_VARIABLE)))
                .orElse(null);
            if (configured != null) {
                password = configured;
            } else {
                password = generateSecurePassword();
                generated = true;
            }
        }
        if (generated) {
            // Intentionally printed to stdout (not just the log) so it is visible on first start.
            System.out.println("============================================================");
            System.out.println(" No PLC4X server credentials configured - generated defaults:");
            System.out.println("   username: " + username);
            System.out.println("   password: " + password);
            System.out.println(" Provide plc4x.server.username/password to set your own.");
            System.out.println("============================================================");
        } else {
            LOG.info("Using configured credentials for user '{}'", username);
        }
    }

    private void resolvePlaintext() {
        if (!plaintext) {
            plaintext = Boolean.parseBoolean(System.getProperty(SERVER_PLAINTEXT_PROPERTY))
                || Boolean.parseBoolean(System.getenv(SERVER_PLAINTEXT_ENVIRONMENT_VARIABLE) == null
                    ? "false" : System.getenv(SERVER_PLAINTEXT_ENVIRONMENT_VARIABLE));
        }
    }

    private static String generateSecurePassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static int findRandomFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new PlcRuntimeException("Couldn't find any free port.", e);
        }
    }
}
