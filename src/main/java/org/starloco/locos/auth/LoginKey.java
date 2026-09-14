package org.starloco.locos.auth;

import java.security.SecureRandom;

/** The per-connection key sent to the client in "HC": 32 lowercase letters. */
public final class LoginKey {

    public static final int LENGTH = 32;

    private LoginKey() {}

    public static String generate(SecureRandom random) {
        char[] key = new char[LENGTH];
        for (int i = 0; i < key.length; i++) {
            key[i] = (char) ('a' + random.nextInt(26));
        }
        return new String(key);
    }
}
