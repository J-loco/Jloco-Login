package org.starloco.locos.account;

import java.time.Duration;
import java.time.Instant;

/**
 * A row of world_accounts, as the login server needs it.
 *
 * @param logged 0 disconnected, 1 in the login server, 2 in a game server
 * @param subscribedUntil epoch milliseconds, 0 when never subscribed
 * @param bannedUntil epoch milliseconds of a temporary ban, 0 for a permanent one
 */
public record Account(
        int id,
        String name,
        String passwordHash,
        String pseudo,
        String question,
        int logged,
        long subscribedUntil,
        boolean banned,
        long bannedUntil) {

    public Account {
        // NULL until the nickname is chosen (accounts created on the website).
        pseudo = pseudo == null ? "" : pseudo;
        question = question == null ? "" : question;
    }

    public boolean hasPseudo() {
        return !pseudo.isEmpty();
    }

    public Duration subscriptionRemaining(Instant now) {
        long remaining = subscribedUntil - now.toEpochMilli();
        return remaining <= 0 ? Duration.ZERO : Duration.ofMillis(remaining);
    }

    public Account withPseudo(String newPseudo) {
        return new Account(id, name, passwordHash, newPseudo, question, logged, subscribedUntil, banned, bannedUntil);
    }
}
