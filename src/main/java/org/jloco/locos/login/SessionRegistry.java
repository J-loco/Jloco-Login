package org.jloco.locos.login;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Open login connections, and which one currently owns each account. */
public final class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final Map<Long, LoginSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, LoginSession> byAccount = new ConcurrentHashMap<>();

    void add(LoginSession session) {
        sessions.put(session.id(), session);
    }

    void remove(LoginSession session) {
        sessions.remove(session.id());
        byAccount.values().remove(session);
    }

    /**
     * Makes the session the owner of the account after a successful authentication; a previous connection
     * on the same account is closed.
     */
    public void claim(String accountName, LoginSession session) {
        LoginSession previous = byAccount.put(accountName.toLowerCase(Locale.ROOT), session);
        if (previous != null && previous != session) {
            log.info(
                    "[{}] Account {} logged in again from session {}: closing the old one",
                    previous.id(),
                    accountName,
                    session.id());
            previous.close();
        }
    }

    public Optional<LoginSession> find(long id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public Collection<LoginSession> all() {
        return sessions.values();
    }

    public void broadcast(String packet) {
        sessions.values().forEach(session -> session.send(packet));
    }

    /** Closes the sessions of a banned IP; returns how many. */
    public int closeIp(String ip) {
        int closed = 0;
        for (LoginSession session : sessions.values()) {
            if (session.ip().equals(ip)) {
                session.close();
                closed++;
            }
        }
        return closed;
    }
}
