package org.starloco.locos.login.step;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.account.Account;
import org.starloco.locos.auth.CharacterSwitchToken;
import org.starloco.locos.login.LoginServices;
import org.starloco.locos.login.LoginSession;
import org.starloco.locos.login.LoginState;

/** Account name, or the character switch ("#S" then a token signed by the game server). */
public final class AccountStep {

    /** Same rule as StarLoco-Web registration. */
    public static final Pattern ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9.@-]{3,30}");

    private static final Logger log = LoggerFactory.getLogger(AccountStep.class);

    private final LoginServices services;

    public AccountStep(LoginServices services) {
        this.services = services;
    }

    public void handleName(LoginSession session, String packet) {
        if (packet.equals("#S")) {
            session.state(new LoginState.WaitingSwitchToken());
            return;
        }
        Optional<Account> account = ACCOUNT_NAME.matcher(packet).matches()
                ? services.accounts().findByName(packet.toLowerCase(Locale.ROOT))
                : Optional.empty();
        if (account.isEmpty()) {
            log.info("[{}] Unknown account name", session.id());
            session.sendAndClose("AlEf");
            return;
        }
        session.state(new LoginState.WaitingPassword(account.get()));
    }

    public void handleSwitchToken(LoginSession session, String packet) {
        Optional<Account> account = services.switchTokens()
                .verify(packet)
                .filter(claim -> claim.ip().equals(session.ip()))
                .map(CharacterSwitchToken.Claim::accountName)
                .flatMap(name -> services.accounts().findByName(name));
        if (account.isEmpty()) {
            log.info("[{}] Invalid character switch token", session.id());
            session.sendAndClose("AlEf");
            return;
        }
        services.sessions().claim(account.get().name(), session);
        session.state(new LoginState.InMenu(account.get(), false));
        log.info(
                "[{}] Account {} came back from a game server",
                session.id(),
                account.get().id());
    }
}
