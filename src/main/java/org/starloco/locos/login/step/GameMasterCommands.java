package org.starloco.locos.login.step;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.starloco.locos.exchange.WorldServer;
import org.starloco.locos.login.ClientVersion;
import org.starloco.locos.login.LoginServices;
import org.starloco.locos.login.LoginSession;

/** "BA" console commands of game masters in the server list. Answers are "BAT" messages. */
final class GameMasterCommands {

    private static final ClientVersion MESSAGE_CHANNEL_VERSION = new ClientVersion(1, 35, 0, false);

    private final LoginServices services;

    GameMasterCommands(LoginServices services) {
        this.services = services;
    }

    void execute(LoginSession session, String command) {
        String[] args = command.trim().split(" +", -1);
        Set<String> authorizedIps = services.admin().authorizedIps();
        switch (args[0].toUpperCase(Locale.ROOT)) {
            case "AUTHORIZED" -> {
                if (args.length < 2) {
                    error(session, "Invalid syntax -> AUTHORIZED [IP]");
                    return;
                }
                authorizedIps.add(args[1]);
                success(
                        session,
                        "You've authorized the IP (" + args[1] + "). It can connect to all accounts of the game.");
            }
            case "UNAUTHORIZED" -> {
                if (args.length < 2) {
                    error(session, "Invalid syntax -> UNAUTHORIZED [IP]");
                    return;
                }
                authorizedIps.remove(args[1]);
                success(session, "You've unauthorized the IP (" + args[1] + ").");
            }
            case "LISTAUTHORIZED" -> {
                if (authorizedIps.isEmpty()) {
                    error(session, "The list of authorized IPs is empty.");
                    return;
                }
                message(session, "IPs authorized to connect to all accounts:");
                authorizedIps.forEach(ip -> message(session, "- " + ip));
            }
            case "EMPTYAUTHORIZED" -> {
                int count = authorizedIps.size();
                authorizedIps.clear();
                success(session, "You removed " + count + " authorized IP(s).");
            }
            case "SERVERSTATE" -> serverState(session, args);
            default -> {
                // Sends the command back to the client as a raw packet (client debugging).
                session.send(command);
                message(session, "You send to your client : " + command);
            }
        }
    }

    private void serverState(LoginSession session, String[] args) {
        Optional<WorldServer> server;
        int state;
        try {
            if (args.length < 3) {
                throw new NumberFormatException();
            }
            server = services.servers().find(Integer.parseInt(args[1]));
            state = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            error(session, "Invalid syntax -> SERVERSTATE [SERVER ID] [STATE]");
            return;
        }
        if (server.isEmpty()) {
            message(session, "Unknown server. Servers:");
            services.servers().all().forEach(known -> message(session, "- id:" + known.id()));
            return;
        }
        services.servers().forceState(server.get(), state);
        success(session, "You've set the server (" + server.get().id() + ") to the state (" + state + ").");
    }

    private void message(LoginSession session, String text) {
        session.send(bat(0, text));
    }

    private void error(LoginSession session, String text) {
        session.send(bat(1, text));
    }

    private void success(LoginSession session, String text) {
        session.send(bat(2, text));
    }

    private String bat(int flag, String text) {
        boolean channel = services.config().login().minimumClientVersion().isAtLeast(MESSAGE_CHANNEL_VERSION);
        return "BAT" + flag + (channel ? "|12||" : "") + text;
    }
}
