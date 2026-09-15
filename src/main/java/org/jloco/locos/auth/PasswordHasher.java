package org.jloco.locos.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashes of world_accounts.pass, shared with JLoco-Web (src/Security/PasswordHasher.php):
 *
 * <ul>
 *   <li>legacy: hex SHA-512 of the hex MD5 of the password;
 *   <li>pbkdf2: {@code pbkdf2_sha512$iterations$base64(salt)$base64(64-byte key)}.
 * </ul>
 *
 * Both formats verify; new hashes, and the rehash after a successful login, use the configured scheme.
 */
public final class PasswordHasher {

    /** Must match JLoco-Web PasswordHasher::PBKDF2_ITERATIONS. */
    static final int PBKDF2_ITERATIONS = 210_000;

    private static final String PBKDF2_PREFIX = "pbkdf2_sha512$";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 512;
    private static final HexFormat HEX = HexFormat.of();

    private final PasswordScheme scheme;
    private final SecureRandom random;

    public PasswordHasher(PasswordScheme scheme, SecureRandom random) {
        this.scheme = scheme;
        this.random = random;
    }

    public PasswordScheme scheme() {
        return scheme;
    }

    /** Whether the plain password matches a stored hash of either format. */
    public boolean matches(String password, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (storedHash.startsWith(PBKDF2_PREFIX)) {
            List<String> parts = List.of(storedHash.split("\\$", -1));
            if (parts.size() != 4) {
                return false;
            }
            try {
                int iterations = Integer.parseInt(parts.get(1));
                byte[] salt = Base64.getDecoder().decode(parts.get(2));
                byte[] expected = Base64.getDecoder().decode(parts.get(3));
                if (iterations < 1 || expected.length == 0) {
                    return false;
                }
                return MessageDigest.isEqual(expected, pbkdf2Key(password, salt, iterations, expected.length * 8));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.US_ASCII), legacy(password).getBytes(StandardCharsets.US_ASCII));
    }

    /** Whether a verified hash should be replaced by one in the configured scheme. */
    public boolean needsRehash(String storedHash) {
        return scheme == PasswordScheme.PBKDF2
                && (storedHash == null || !storedHash.startsWith(PBKDF2_PREFIX + PBKDF2_ITERATIONS + "$"));
    }

    /** A new hash of the password in the configured scheme. */
    public String hash(String password) {
        if (scheme == PasswordScheme.LEGACY) {
            return legacy(password);
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return pbkdf2(password, salt, PBKDF2_ITERATIONS);
    }

    static String pbkdf2(String password, byte[] salt, int iterations) {
        Base64.Encoder encoder = Base64.getEncoder();
        return PBKDF2_PREFIX + iterations + "$" + encoder.encodeToString(salt) + "$"
                + encoder.encodeToString(pbkdf2Key(password, salt, iterations, KEY_BITS));
    }

    /** Legacy format: hex SHA-512 of the hex MD5 of the UTF-8 password. */
    static String legacy(String password) {
        return digest("SHA-512", digest("MD5", password));
    }

    private static byte[] pbkdf2Key(String password, byte[] salt, int iterations, int bits) {
        try {
            // SunJCE encodes the password characters as UTF-8, like PHP's hash_pbkdf2 on a UTF-8 string.
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
                        .generateSecret(spec)
                        .getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA512 is not available", e);
        }
    }

    private static String digest(String algorithm, String text) {
        try {
            return HEX.formatHex(MessageDigest.getInstance(algorithm).digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        }
    }
}
