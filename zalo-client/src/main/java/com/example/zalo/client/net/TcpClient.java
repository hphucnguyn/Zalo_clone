package com.example.zalo.client.net;

import com.example.zalo.common.protocol.Packet;
import com.example.zalo.client.config.ClientConfig;
import com.example.zalo.client.state.ConnectionState;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Client TLS connection with a dedicated receive loop. */
public final class TcpClient implements AutoCloseable {

    private final ClientConfig config;
    private final SSLContext sslContext;
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final ExecutorService receiveExecutor = Executors.newSingleThreadExecutor();
    private volatile Consumer<Packet> packetConsumer = ignored -> { };
    private volatile Consumer<Throwable> disconnectConsumer = ignored -> { };
    private volatile SSLSocket socket;
    private volatile PacketSender packetSender;

    public TcpClient(ClientConfig config, SSLContext sslContext) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext must not be null");
    }

    public synchronized void connect() throws IOException {
        if (state.get() != ConnectionState.DISCONNECTED) {
            throw new IllegalStateException("Client is already connected or connecting");
        }
        state.set(ConnectionState.CONNECTING);
        Socket tcpSocket = new Socket();
        try {
            tcpSocket.connect(new InetSocketAddress(config.serverHost(), config.serverPort()), config.connectTimeoutMillis());
            tcpSocket.setSoTimeout(config.connectTimeoutMillis());
            SSLSocket tlsSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(
                    tcpSocket, config.expectedHost(), config.serverPort(), true);
            tlsSocket.setEnabledProtocols(config.tlsProtocols());
            SSLParameters parameters = tlsSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tlsSocket.setSSLParameters(parameters);
            tlsSocket.startHandshake();
            ServerCertificateValidator.validate(tlsSocket, config.expectedHost());
            tlsSocket.setSoTimeout(0);

            socket = tlsSocket;
            packetSender = new PacketSender(new BufferedWriter(
                    new OutputStreamWriter(tlsSocket.getOutputStream(), StandardCharsets.UTF_8)));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(tlsSocket.getInputStream(), StandardCharsets.UTF_8));
            state.set(ConnectionState.TLS_READY);
            receiveExecutor.execute(new ReceiveLoop(reader, this::dispatchPacket, this::connectionClosed));
        } catch (IOException | RuntimeException exception) {
            state.set(ConnectionState.DISCONNECTED);
            try {
                tcpSocket.close();
            } catch (IOException ignored) {
                // The original connection error is more useful than a close error.
            }
            throw exception;
        }
    }

    public void send(Packet packet) throws IOException {
        PacketSender sender = packetSender;
        if (sender == null || state.get() == ConnectionState.DISCONNECTED) {
            throw new IOException("Not connected to the server");
        }
        sender.send(packet);
    }

    public ConnectionState state() {
        return state.get();
    }

    public void markAuthenticated() {
        if (!state.compareAndSet(ConnectionState.TLS_READY, ConnectionState.AUTHENTICATED)) {
            throw new IllegalStateException("TLS connection is not ready for authentication");
        }
    }

    public void setPacketConsumer(Consumer<Packet> consumer) {
        packetConsumer = Objects.requireNonNull(consumer, "consumer must not be null");
    }

    public void setDisconnectConsumer(Consumer<Throwable> consumer) {
        disconnectConsumer = Objects.requireNonNull(consumer, "consumer must not be null");
    }

    private void dispatchPacket(Packet packet) {
        packetConsumer.accept(packet);
    }

    private void connectionClosed(Throwable cause) {
        state.set(ConnectionState.DISCONNECTED);
        packetSender = null;
        SSLSocket currentSocket = socket;
        socket = null;
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
                // The receive loop already reported the connection failure.
            }
        }
        disconnectConsumer.accept(cause);
    }

    @Override
    public synchronized void close() {
        SSLSocket currentSocket = socket;
        socket = null;
        packetSender = null;
        state.set(ConnectionState.DISCONNECTED);
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
                // Closing an already failed connection is harmless.
            }
        }
        receiveExecutor.shutdownNow();
    }
}
