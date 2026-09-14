package org.starloco.locos.it;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs the login server as a separate JVM (black box): a working directory with a generated
 * login.config.properties, random ports, and the test database. Used by the integration tests so they
 * exercise exactly what is deployed.
 */
public final class LoginServerProcess implements AutoCloseable {

    /** Game-server exchange key (base64, at least 256 bits) also used to sign character-switch tokens. */
    public static final String EXCHANGE_KEY = "Q2hhbmdlTWVZbjJramliZGRGQVd0blBKMkFGbEw4V1htb2hKTUN2aWdRZ2dhRXlwYTU=";

    public static final String VERSION = "1.39.8e";

    private final Process process;
    private final Path workingDirectory;
    private final int loginPort;
    private final int exchangePort;

    private LoginServerProcess(Process process, Path workingDirectory, int loginPort, int exchangePort) {
        this.process = process;
        this.workingDirectory = workingDirectory;
        this.loginPort = loginPort;
        this.exchangePort = exchangePort;
    }

    public static LoginServerProcess start(TestDatabase database, String passwordScheme) throws IOException {
        return start(database, passwordScheme, Map.of());
    }

    public static LoginServerProcess start(TestDatabase database, String passwordScheme, Map<String, String> env)
            throws IOException {
        int loginPort = freePort();
        int exchangePort = freePort();
        Path directory = Files.createTempDirectory("starloco-login-it");
        Files.writeString(
                directory.resolve("login.config.properties"),
                String.join(
                        "\n",
                        "system.server.exchange.ip 127.0.0.1",
                        "system.server.exchange.port " + exchangePort,
                        "system.server.exchange.key " + EXCHANGE_KEY,
                        "system.server.login.port " + loginPort,
                        "system.server.login.version " + VERSION,
                        "database.login.host " + database.host(),
                        "database.login.port " + database.port(),
                        "database.login.user " + database.user(),
                        "database.login.pass " + database.password(),
                        "database.login.name " + database.name(),
                        "system.server.login.password.scheme " + passwordScheme,
                        ""),
                StandardCharsets.UTF_8);

        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = new ArrayList<>(List.of(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                System.getProperty("starloco.it.main", MAIN_CLASS)));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("stdout.log").toFile());
        // Packet traces on, so the tests also check that secrets never reach the logs.
        builder.environment().put("LOGIN_LOG_LEVEL", "DEBUG");
        builder.environment().putAll(env);
        Process process = builder.start();
        // stdin stays open (an interactive console must not see EOF during the test).

        LoginServerProcess server = new LoginServerProcess(process, directory, loginPort, exchangePort);
        server.awaitListening(Duration.ofSeconds(60));
        return server;
    }

    static final String MAIN_CLASS = "org.starloco.locos.Main";

    public int loginPort() {
        return loginPort;
    }

    public int exchangePort() {
        return exchangePort;
    }

    public Path workingDirectory() {
        return workingDirectory;
    }

    /** Everything the server printed so far, for assertion messages. */
    public String output() {
        try {
            Path log = workingDirectory.resolve("stdout.log");
            return Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Waits for the startup log line (probing the ports would count against the connection limits). */
    private void awaitListening(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The login server exited:\n" + output());
            }
            if (output().contains("Login server ready")) {
                return;
            }
            sleep(100);
        }
        throw new IllegalStateException("The login server did not start in " + timeout + ":\n" + output());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
