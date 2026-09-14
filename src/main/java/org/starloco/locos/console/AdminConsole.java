package org.starloco.locos.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.LoginApplication;

/**
 * Commands typed on standard input. The reader thread ends at end-of-input, so a server without stdin
 * (Docker, systemd) simply has no console instead of spinning on it.
 */
public final class AdminConsole {

    private static final Logger log = LoggerFactory.getLogger(AdminConsole.class);

    private final LoginApplication application;

    AdminConsole(LoginApplication application) {
        this.application = application;
    }

    public static void start(LoginApplication application) {
        AdminConsole adminConsole = new AdminConsole(application);
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Thread.ofPlatform().name("console").daemon(true).start(() -> adminConsole.run(stdin));
    }

    void run(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    execute(line.strip());
                }
            }
            log.debug("Console input closed");
        } catch (IOException e) {
            log.warn("Console input failed: {}", e.getMessage());
        }
    }

    void execute(String line) {
        String[] args = line.split(" +", 3);
        switch (args[0].toUpperCase(Locale.ROOT)) {
            case "HELP" ->
                log.info("Commands: SERVERS, SESSIONS, UPTIME, AUTHORIZED <ip>, MAINTAIN <account>,"
                        + " PASSWORD <password>, SEND <session id> <packet>");
            case "SERVERS" ->
                application
                        .servers()
                        .all()
                        .forEach(server -> log.info(
                                "Server {}: state {}, {} free places, {}",
                                server.id(),
                                server.state(),
                                server.freePlaces(),
                                server.isLinked()
                                        ? "connected from " + server.host() + ":" + server.port()
                                        : "not connected"));
            case "SESSIONS" ->
                log.info(
                        "{} open login connection(s)",
                        application.sessions().all().size());
            case "UPTIME" -> {
                var uptime = application.uptime();
                log.info(
                        "Uptime: {}d {}h {}m {}s",
                        uptime.toDays(),
                        uptime.toHoursPart(),
                        uptime.toMinutesPart(),
                        uptime.toSecondsPart());
            }
            case "AUTHORIZED" -> {
                if (args.length < 2) {
                    log.info("Usage: AUTHORIZED <ip>");
                    return;
                }
                application.admin().authorizedIps().add(args[1]);
                log.warn("IP {} can now open any account without password", args[1]);
            }
            case "MAINTAIN" -> {
                if (args.length < 2) {
                    log.info("Usage: MAINTAIN <account>");
                    return;
                }
                boolean on = application.admin().toggleMaintenance(args[1]);
                log.info("Maintenance {} for account {}", on ? "enabled" : "disabled", args[1]);
            }
            case "PASSWORD" -> {
                if (args.length < 2) {
                    log.info("Usage: PASSWORD <password>");
                    return;
                }
                log.info(
                        "Hash: {}",
                        application
                                .passwordHasher()
                                .hash(line.substring(args[0].length()).strip()));
            }
            case "SEND" -> {
                if (args.length < 3) {
                    log.info("Usage: SEND <session id> <packet>");
                    return;
                }
                try {
                    application
                            .sessions()
                            .find(Long.parseLong(args[1]))
                            .ifPresentOrElse(
                                    session -> session.send(args[2]), () -> log.info("No session {}", args[1]));
                } catch (NumberFormatException e) {
                    log.info("Usage: SEND <session id> <packet>");
                }
            }
            default -> log.info("Unknown command {}, type HELP", args[0]);
        }
    }
}
