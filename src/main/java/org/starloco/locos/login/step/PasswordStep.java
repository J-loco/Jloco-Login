package org.starloco.locos.login.step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.account.Account;
import org.starloco.locos.auth.PasswordCipher;
import org.starloco.locos.login.LoginServices;
import org.starloco.locos.login.LoginSession;
import org.starloco.locos.login.LoginState;

/** The "#1" encoded password; upgrades the stored hash to the configured scheme after a successful check. */
public final class PasswordStep {

    private static final Logger log = LoggerFactory.getLogger(PasswordStep.class);

    private final LoginServices services;

    public PasswordStep(LoginServices services) {
        this.services = services;
    }

    public void handle(LoginSession session, Account account, String packet) {
        boolean authorizedIp = services.admin().authorizedIps().contains(session.ip());
        if (authorizedIp) {
            log.warn(
                    "[{}] Account {} opened without password check: IP {} is authorized",
                    session.id(),
                    account.id(),
                    session.ip());
        } else {
            String password = PasswordCipher.decode(packet, session.key()).orElse(null);
            if (password == null || !services.hasher().matches(password, account.passwordHash())) {
                log.info("[{}] Wrong password for account {}", session.id(), account.id());
                session.sendAndClose("AlEf");
                return;
            }
            if (services.hasher().needsRehash(account.passwordHash())) {
                services.accounts()
                        .updatePassword(account.id(), services.hasher().hash(password));
                log.info(
                        "[{}] Password hash of account {} upgraded to {}",
                        session.id(),
                        account.id(),
                        services.hasher().scheme());
            }
        }
        services.sessions().claim(account.name(), session);
        session.state(new LoginState.InMenu(account, authorizedIp));
        log.info("[{}] Account {} authenticated", session.id(), account.id());
    }
}
