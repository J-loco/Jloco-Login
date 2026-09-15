package org.jloco.locos.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A minimal Dofus 1.39 login client: NUL-terminated UTF-8 frames. */
public final class DofusTestClient implements AutoCloseable {

    private static final String CHAIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    /** The server allows a few connections per second per IP: tests pace their connections. */
    private static final long CONNECT_SPACING_MILLIS = 200;

    private static long lastConnect;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final List<String> received = new ArrayList<>();
    private String key;

    private DofusTestClient(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(5000);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    public static synchronized DofusTestClient connect(int port) throws IOException {
        long wait = lastConnect + CONNECT_SPACING_MILLIS - System.currentTimeMillis();
        if (wait > 0) {
            LoginServerProcess.sleep(wait);
        }
        lastConnect = System.currentTimeMillis();
        return new DofusTestClient(new Socket(InetAddress.getLoopbackAddress(), port));
    }

    /** Reads the policy file and the HC key sent on connection. */
    public DofusTestClient handshake() throws IOException {
        String policy = next();
        if (!policy.startsWith("<?xml")) {
            throw new AssertionError("Expected the cross-domain policy, got: " + policy);
        }
        String hello = next();
        if (!hello.startsWith("HC")) {
            throw new AssertionError("Expected HC<key>, got: " + hello);
        }
        key = hello.substring(2);
        return this;
    }

    public String key() {
        return key;
    }

    public void send(String packet) throws IOException {
        out.write((packet + "\n\0").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Version, account name and encoded password, as the client sends them. */
    public void login(String account, String password) throws IOException {
        send(LoginServerProcess.VERSION);
        send(account);
        send(encryptPassword(password, key));
    }

    /** The next frame; fails after the socket timeout. */
    public String next() throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        while (true) {
            int b;
            try {
                b = in.read();
            } catch (SocketTimeoutException e) {
                throw new AssertionError("No frame received (so far: " + received + ", partial: "
                        + frame.toString(StandardCharsets.UTF_8) + ")");
            }
            if (b == -1) {
                throw new AssertionError("Connection closed by the server (received: " + received + ")");
            }
            if (b == 0) {
                String packet = frame.toString(StandardCharsets.UTF_8);
                received.add(packet);
                return packet;
            }
            frame.write(b);
        }
    }

    /** Skips frames until one matches, e.g. the server-list broadcast "AH" arriving at any time. */
    public String nextMatching(Predicate<String> predicate) throws IOException {
        while (true) {
            String packet = next();
            if (predicate.test(packet)) {
                return packet;
            }
        }
    }

    public String nextStartingWith(String prefix) throws IOException {
        return nextMatching(packet -> packet.startsWith(prefix));
    }

    /** Whether the server closed the connection (reads until EOF, ignoring frames). */
    public boolean isClosedByServer() throws IOException {
        try {
            while (true) {
                int b = in.read();
                if (b == -1) {
                    return true;
                }
            }
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    public List<String> received() {
        return received;
    }

    /** The Dofus client's "#1" password encoding. */
    public static String encryptPassword(String password, String key) {
        StringBuilder encoded = new StringBuilder("#1");
        for (int i = 0; i < password.length(); i++) {
            int code = password.charAt(i);
            int keyCode = key.charAt(i);
            encoded.append(CHAIN.charAt((code / 16 + keyCode) % 64)).append(CHAIN.charAt((code % 16 + keyCode) % 64));
        }
        return encoded.toString();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
