package org.jloco.locos.it;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Plays the game server side of the exchange channel (protocol v2: "\n"-terminated lines, HMAC handshake). */
public final class FakeGameServer implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader in;
    private final OutputStream out;

    private FakeGameServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(5000);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = socket.getOutputStream();
    }

    public static FakeGameServer connect(int exchangePort) throws IOException {
        return new FakeGameServer(new Socket(InetAddress.getLoopbackAddress(), exchangePort));
    }

    /** Writes the messages in a single TCP write, each terminated by "\n". */
    public void send(String... messages) throws IOException {
        StringBuilder data = new StringBuilder();
        for (String message : messages) {
            data.append(message).append('\n');
        }
        out.write(data.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** The next message, or an assertion error when the connection is closed or silent. */
    public String next() throws IOException {
        try {
            String line = in.readLine();
            if (line == null) {
                throw new AssertionError("Exchange connection closed by the login server");
            }
            return line;
        } catch (SocketTimeoutException e) {
            throw new AssertionError("No exchange message received");
        }
    }

    public String nextStartingWith(String prefix) throws IOException {
        while (true) {
            String message = next();
            if (message.startsWith(prefix)) {
                return message;
            }
        }
    }

    /** Answers the challenge with the HMAC of the nonce under the server key. */
    public void authenticate(int serverId, String key) throws IOException {
        String challenge = next();
        if (!challenge.startsWith("SK?2;")) {
            throw new AssertionError("Expected the v2 challenge, got " + challenge);
        }
        send("SK" + serverId + ";" + hmac(key, challenge.substring("SK?2;".length())) + ";100");
    }

    /** Authentication, host and state "online", the way JLoco-Game registers. */
    public void register(int serverId, String key, int port) throws IOException {
        authenticate(serverId, key);
        expect("SKK");
        send("SH127.0.0.1;" + port);
        expect("SHK");
        send("SS1");
    }

    public void expect(String message) throws IOException {
        String received = next();
        if (!received.equals(message)) {
            throw new AssertionError("Expected " + message + " but received " + received);
        }
    }

    /** Whether the login server closed the connection within the socket timeout. */
    public boolean isClosedByServer() throws IOException {
        try {
            while (in.readLine() != null) {
                // ignore remaining messages
            }
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    static String hmac(String key, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(nonce.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
