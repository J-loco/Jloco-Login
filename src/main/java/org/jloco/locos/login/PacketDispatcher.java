package org.jloco.locos.login;

import org.jloco.locos.database.Jdbc.DataAccessException;
import org.jloco.locos.login.step.AccountQueue;
import org.jloco.locos.login.step.AccountStep;
import org.jloco.locos.login.step.MenuStep;
import org.jloco.locos.login.step.NicknameStep;
import org.jloco.locos.login.step.PasswordStep;
import org.jloco.locos.login.step.VersionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes each client packet to the step of the session's current {@link LoginState}. */
public final class PacketDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PacketDispatcher.class);
    private static final Logger packets = LoggerFactory.getLogger("org.jloco.locos.login.packets");

    private final LoginServices services;
    private final VersionStep version;
    private final AccountStep account;
    private final PasswordStep password;
    private final NicknameStep nickname;
    private final MenuStep menu;

    public PacketDispatcher(LoginServices services) {
        this.services = services;
        AccountQueue queue = new AccountQueue(services);
        this.version = new VersionStep(services);
        this.account = new AccountStep(services);
        this.password = new PasswordStep(services);
        this.nickname = new NicknameStep(services, queue);
        this.menu = new MenuStep(services, queue);
    }

    public void handle(LoginSession session, String packet) {
        if (!session.isOpen()) {
            return;
        }
        LoginState state = session.state();
        if (packets.isDebugEnabled()) {
            packets.debug("[{}] < {}", session.id(), masked(state, packet));
        }
        try {
            switch (state) {
                case LoginState.WaitingVersion ignored -> version.handle(session, packet);
                case LoginState.WaitingAccount ignored -> account.handleName(session, packet);
                case LoginState.WaitingSwitchToken ignored -> account.handleSwitchToken(session, packet);
                case LoginState.WaitingPassword waiting -> password.handle(session, waiting.account(), packet);
                case LoginState.WaitingNickname waiting -> nickname.handle(session, waiting.account(), packet);
                case LoginState.InMenu inMenu -> menu.handle(session, inMenu, packet);
            }
        } catch (DataAccessException e) {
            log.error("[{}] Database error", session.id(), e);
            session.sendAndClose("AlEf");
        }
    }

    /** The connection closed: an authenticated account that did not go to a game server is no longer logged. */
    public void disconnected(LoginSession session) {
        if (session.handedOff()) {
            return;
        }
        session.account().ifPresent(account -> {
            try {
                services.accounts().setLogged(account.id(), 0);
            } catch (DataAccessException e) {
                log.error("[{}] Cannot reset the logged flag of account {}", session.id(), account.id(), e);
            }
        });
    }

    /** Passwords and switch tokens never reach the logs. */
    private static String masked(LoginState state, String packet) {
        return switch (state) {
            case LoginState.WaitingPassword ignored -> "<password>";
            case LoginState.WaitingSwitchToken ignored -> "<token>";
            default -> packet;
        };
    }
}
