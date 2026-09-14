package org.starloco.locos.login.step;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.account.Account;
import org.starloco.locos.account.Player;
import org.starloco.locos.exchange.WorldServer;
import org.starloco.locos.login.LoginServices;
import org.starloco.locos.login.LoginSession;
import org.starloco.locos.login.LoginState;

/** Packets of an authenticated client: account information, server lists, server selection, GM commands. */
public final class MenuStep {

    private static final Logger log = LoggerFactory.getLogger(MenuStep.class);
    private static final Pattern PSEUDO = Pattern.compile("[A-Za-z0-9.@-]{1,30}");

    private final LoginServices services;
    private final AccountQueue queue;
    private final GameMasterCommands gameMasterCommands;

    public MenuStep(LoginServices services, AccountQueue queue) {
        this.services = services;
        this.queue = queue;
        this.gameMasterCommands = new GameMasterCommands(services);
    }

    public void handle(LoginSession session, LoginState.InMenu menu, String packet) {
        Account account = menu.account();
        String header = packet.length() >= 2 ? packet.substring(0, 2) : packet;
        String payload = packet.length() > 2 ? packet.substring(2) : "";
        switch (header) {
            case "Af" -> queue.enter(session, account, menu.maintenanceBypass());
            case "Ax" ->
                session.send("AxK"
                        + account.subscriptionRemaining(services.clock().instant())
                                .toMillis()
                        + characterCounts(account.id()).entrySet().stream()
                                .map(entry -> "|" + entry.getKey() + "," + entry.getValue())
                                .collect(Collectors.joining()));
            case "AX" -> selectServer(session, account, payload);
            case "AF" -> session.send("AF" + friendServers(payload));
            case "BA" -> {
                if (isGameMaster(account)) {
                    gameMasterCommands.execute(session, payload);
                }
            }
            case "Ap", "Ai" -> {
                // Client information packets, nothing to do.
            }
            default -> {
                log.info("[{}] Unexpected packet in the server list: {}", session.id(), header);
                session.close();
            }
        }
    }

    /** Number of characters per known game server, in server order (servers without characters are left out). */
    private Map<Integer, Long> characterCounts(int accountId) {
        Map<Integer, Long> counts = services.players().findByAccount(accountId).stream()
                .collect(Collectors.groupingBy(Player::server, Collectors.counting()));
        Map<Integer, Long> ordered = new LinkedHashMap<>();
        services.servers().all().stream()
                .filter(server -> counts.containsKey(server.id()))
                .forEach(server -> ordered.put(server.id(), counts.get(server.id())));
        return ordered;
    }

    /** "id,count;" for each server where the friend (found by nickname) has characters. */
    private String friendServers(String pseudo) {
        if (!PSEUDO.matcher(pseudo).matches()) {
            return "";
        }
        return services.accounts()
                .findByPseudo(pseudo)
                .map(friend -> characterCounts(friend.id()).entrySet().stream()
                        .map(entry -> entry.getKey() + "," + entry.getValue() + ";")
                        .collect(Collectors.joining()))
                .orElse("");
    }

    private boolean isGameMaster(Account account) {
        return services.players().findByAccount(account.id()).stream().anyMatch(Player::isGameMaster);
    }

    /*
     * AXEr not authorized, AXEd server unavailable, AXEf<ids|> server full (with the free servers).
     */
    private void selectServer(LoginSession session, Account account, String payload) {
        int serverId;
        try {
            serverId = Integer.parseInt(payload);
        } catch (NumberFormatException e) {
            session.sendAndClose("AXEr");
            return;
        }
        Optional<WorldServer> selected = services.servers().find(serverId);
        if (selected.isEmpty()) {
            session.send("AXEr");
            return;
        }
        WorldServer server = selected.get();
        if (server.state() != WorldServer.ONLINE || !server.isLinked()) {
            session.send("AXEd");
            return;
        }

        List<Player> players = services.players().findByAccount(account.id());
        boolean subscribed =
                !account.subscriptionRemaining(services.clock().instant()).isZero();
        if (players.stream().noneMatch(Player::isGameMaster)
                && !subscribed
                && (server.freePlaces() <= 0 || server.subscriberOnly())) {
            session.send("AXEf" + fullServers());
            return;
        }

        int loggedServer = services.players().loggedServer(account.id());
        Optional<WorldServer> stillIn = loggedServer > 0 ? services.servers().find(loggedServer) : Optional.empty();
        if (stillIn.isPresent() && stillIn.get().isLinked()) {
            log.info(
                    "[{}] Account {} still has a character on server {}: kicking it",
                    session.id(),
                    account.id(),
                    loggedServer);
            services.accounts().setLogged(account.id(), 0);
            services.players().resetLogged(account.id());
            stillIn.get().send("WK" + account.id());
            session.sendAndClose("AlEd");
            return;
        }

        server.send("WA" + account.id());
        // A client on the same machine as the login server (development) reaches the game server locally too.
        String host = "127.0.0.1".equals(session.localIp()) ? "127.0.0.1" : server.host();
        session.send("AYK" + host + ":" + server.port() + ";" + account.id());
        services.accounts().setLogged(account.id(), 0);
        session.handOff();
        log.info("[{}] Account {} sent to server {}", session.id(), account.id(), server.id());
    }

    private String fullServers() {
        return services.servers().linked().stream()
                .filter(server -> !server.subscriberOnly() && server.freePlaces() <= 0)
                .map(server -> server.id() + "|")
                .collect(Collectors.joining());
    }
}
