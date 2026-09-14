package org.starloco.locos.auth;

import java.util.Optional;

/**
 * The Dofus client's password encoding ("#1" followed by two characters per password character, mixed
 * with the HC key sent on connection). It is not encryption: anyone holding the key can decode it, so
 * neither the key nor the encoded password may be logged.
 */
public final class PasswordCipher {

    private static final String CHAIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
    private static final String PREFIX = "#1";

    private PasswordCipher() {}

    /** The plain password, or empty when the packet is not a valid encoding for this key. */
    public static Optional<String> decode(String encoded, String key) {
        String payload = encoded.startsWith(PREFIX) ? encoded.substring(PREFIX.length()) : encoded;
        if (payload.isEmpty() || payload.length() % 2 != 0 || payload.length() / 2 > key.length()) {
            return Optional.empty();
        }
        StringBuilder password = new StringBuilder(payload.length() / 2);
        for (int i = 0; i < payload.length(); i += 2) {
            int keyCode = key.charAt(i / 2);
            int high = CHAIN.indexOf(payload.charAt(i));
            int low = CHAIN.indexOf(payload.charAt(i + 1));
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            password.append((char) (Math.floorMod(high - keyCode, 64) * 16 + Math.floorMod(low - keyCode, 64)));
        }
        return Optional.of(password.toString());
    }

    /** The client side of the encoding (tests and tools). */
    public static String encode(String password, String key) {
        StringBuilder encoded = new StringBuilder(PREFIX);
        for (int i = 0; i < password.length(); i++) {
            int code = password.charAt(i);
            int keyCode = key.charAt(i);
            encoded.append(CHAIN.charAt((code / 16 + keyCode) % 64)).append(CHAIN.charAt((code % 16 + keyCode) % 64));
        }
        return encoded.toString();
    }
}
