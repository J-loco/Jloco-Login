package org.starloco.locos.login.step;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.account.Account;
import org.starloco.locos.account.Player;
import org.starloco.locos.exchange.WorldServer;
import org.starloco.locos.login.LoginServices;
import org.starloco.locos.login.LoginSession;
import org.starloco.locos.login.LoginState;

/**
 * "Af" after authentication: checks bans, maintenance and an existing connection, then sends the account
 * information. Error packets: AlEb banned, AlEk&lt;days|hours|minutes&gt; temporarily banned, AlEm maintenance,
 * AlEd another connection was kicked, AlEr choose a nickname.
 */
public final class AccountQueue {

    private static final Logger log = LoggerFactory.getLogger(AccountQueue.class);

    private static final int DISCONNECTED = 0;
    private static final int IN_LOGIN = 1;
    private static final int IN_GAME = 2;

    private final LoginServices services;

    public AccountQueue(LoginServices services) {
        this.services = services;
    }

    public void enter(LoginSession session, Account authenticated, boolean maintenanceBypass) {
        Optional<Account> reloaded = services.accounts().findById(authenticated.id());
        if (reloaded.isEmpty()) {
            session.sendAndClose("AlEf");
            return;
        }
        Account account = reloaded.get();

        if (account.banned() || services.bans().isBanned(session.ip())) {
            banned(session, account);
            return;
        }
        if (!maintenanceBypass && services.admin().isUnderMaintenance(account.name())) {
            log.info("[{}] Account {} is under maintenance", session.id(), account.id());
            session.sendAndClose("AlEm");
            return;
        }

        switch (account.logged()) {
            case DISCONNECTED -> sendInformation(session, account, maintenanceBypass);
            case IN_LOGIN -> {
                int server = services.players().loggedServer(account.id());
                Optional<WorldServer> game = services.servers().find(server);
                if (server != 0 && game.isPresent()) {
                    log.info(
                            "[{}] Account {} still has a character on server {}: kicking it",
                            session.id(),
                            account.id(),
                            server);
                    kickFromGame(account, game.get());
                    session.sendAndClose("AlEd");
                    return;
                }
                sendInformation(session, account, maintenanceBypass);
            }
            case IN_GAME -> {
                log.warn("[{}] Account {} is flagged in game while logging in", session.id(), account.id());
                int server = services.players().loggedServer(account.id());
                services.accounts().setLogged(account.id(), DISCONNECTED);
                services.servers().find(server).ifPresent(game -> game.send("WK" + account.id()));
                session.sendAndClose("AlEd");
            }
            default -> {
                log.warn(
                        "[{}] Account {} has an unknown logged state {}", session.id(), account.id(), account.logged());
                session.sendAndClose("AlEf");
            }
        }
    }

    private void kickFromGame(Account account, WorldServer game) {
        services.accounts().setLogged(account.id(), DISCONNECTED);
        services.players().resetLogged(account.id());
        game.send("WK" + account.id());
    }

    private void banned(LoginSession session, Account account) {
        Instant now = services.clock().instant();
        if (account.banned() && account.bannedUntil() > 0) {
            Duration remaining = Duration.ofMillis(account.bannedUntil() - now.toEpochMilli());
            if (remaining.isNegative() || remaining.isZero()) {
                services.accounts().liftBan(account.id());
                log.info("[{}] Temporary ban of account {} expired", session.id(), account.id());
                if (!services.bans().isBanned(session.ip())) {
                    sendInformation(session, account, false);
                    return;
                }
            } else {
                session.sendAndClose("AlEk" + banCountdown(remaining));
                return;
            }
        }
        log.info("[{}] Account {} or IP {} is banned", session.id(), account.id(), session.ip());
        session.sendAndClose("AlEb");
    }

    /** "days|hours|minutes" left, minutes rounded up. */
    static String banCountdown(Duration remaining) {
        long minutes = (remaining.toMillis() + 59_999) / 60_000;
        return (minutes / (24 * 60)) + "|" + (minutes / 60 % 24) + "|" + (minutes % 60);
    }

    private void sendInformation(LoginSession session, Account account, boolean maintenanceBypass) {
        if (!account.hasPseudo()) {
            session.state(new LoginState.WaitingNickname(account));
            session.send("AlEr");
            return;
        }
        services.accounts().setLogged(account.id(), IN_LOGIN);
        session.state(new LoginState.InMenu(account, maintenanceBypass));

        List<Player> players = services.players().findByAccount(account.id());
        boolean gameMaster = players.stream().anyMatch(Player::isGameMaster);
        session.send("Af0|0|0|1|-1");
        session.send("Ad" + account.pseudo());
        session.send("Ac0");
        session.send(services.servers().hostList());
        session.send("AlK" + (gameMaster ? 1 : 0));
        session.send("AQ" + account.question());
        log.info("[{}] Account {} is in the server list", session.id(), account.id());
    }
}
