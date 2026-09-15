package org.jloco.locos.login.step;

import org.jloco.locos.login.ClientVersion;
import org.jloco.locos.login.LoginServices;
import org.jloco.locos.login.LoginSession;
import org.jloco.locos.login.LoginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** First packet: "1.39.8e" (optionally followed by "|lang"). */
public final class VersionStep {

    private static final Logger log = LoggerFactory.getLogger(VersionStep.class);

    private final ClientVersion minimum;

    public VersionStep(LoginServices services) {
        this.minimum = services.config().login().minimumClientVersion();
    }

    public void handle(LoginSession session, String packet) {
        if (packet.startsWith("<policy-file-request")) {
            return; // Flash asks for the policy file, which was already sent on connection.
        }
        String version = packet.split("\\|", 2)[0];
        boolean accepted =
                ClientVersion.parse(version).map(v -> v.isAtLeast(minimum)).orElse(false);
        if (!accepted) {
            log.info("[{}] Client version \"{}\" refused (minimum {})", session.id(), version, minimum);
            session.sendAndClose("AlEv" + minimum);
            return;
        }
        session.state(new LoginState.WaitingAccount());
    }
}
