package org.jloco.locos.auth;

import java.util.Locale;
import java.util.Optional;

/** Format of new password hashes (system.server.login.password.scheme). */
public enum PasswordScheme {
    /** hex SHA-512 of hex MD5 (the historical format). */
    LEGACY,
    /** pbkdf2_sha512$iterations$salt$key, shared with JLoco-Web. */
    PBKDF2;

    public static Optional<PasswordScheme> parse(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "legacy" -> Optional.of(LEGACY);
            case "pbkdf2" -> Optional.of(PBKDF2);
            default -> Optional.empty();
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
