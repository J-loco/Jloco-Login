package org.starloco.locos.login.step;

import java.util.regex.Pattern;
import org.starloco.locos.account.Account;
import org.starloco.locos.login.LoginServices;
import org.starloco.locos.login.LoginSession;
import org.starloco.locos.login.LoginState;

/** Nickname chosen after "AlEr" (accounts created on the website have none). */
public final class NicknameStep {

    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9.@-]{1,30}");

    private final LoginServices services;
    private final AccountQueue queue;

    public NicknameStep(LoginServices services, AccountQueue queue) {
        this.services = services;
        this.queue = queue;
    }

    public void handle(LoginSession session, Account account, String nickname) {
        if (account.hasPseudo()) {
            session.close();
            return;
        }
        if (nickname.equalsIgnoreCase(account.name())) {
            session.send("AlEr"); // the nickname must differ from the login name
            return;
        }
        if (!NICKNAME.matcher(nickname).matches() || services.accounts().pseudoExists(nickname)) {
            session.send("AlEs"); // invalid or already taken
            return;
        }
        if (!services.accounts().choosePseudo(account.id(), nickname)) {
            session.close();
            return;
        }
        services.accounts().setLogged(account.id(), 0);
        Account named = account.withPseudo(nickname);
        session.state(new LoginState.InMenu(named, false));
        queue.enter(session, named, false);
    }
}
