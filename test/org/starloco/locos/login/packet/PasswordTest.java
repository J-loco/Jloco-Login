package org.starloco.locos.login.packet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The vectors are shared with StarLoco-Web (tests/Unit/Security/PasswordHasherTest.php):
 * a hash written by either the website or the login server must verify on the other.
 */
public class PasswordTest {

    /** SHA-512 of MD5("Secret123"). */
    private static final String LEGACY_VECTOR = "feb8908e6856152712b5206ec3d0e2b0ef9bbd4c9d518fdfde5851ba5673125f45615b7ced2a6f177e37cbe9820c9f250192d48ccb4d01f5b3ea77a86fc09b5c";

    /** PBKDF2-HMAC-SHA512("Secret123", salt = bytes 0..15, 210000 iterations, 64 bytes). */
    private static final String PBKDF2_VECTOR = "pbkdf2_sha512$210000$AAECAwQFBgcICQoLDA0ODw==$iZtt5xyR1Etmq2tlOF9CV/QVTjl/9i+yhjOPuGSDI6QIyQ1QkpLGIX79y9cibM8TrXmsPxPWGmYpatx0UdBWfQ==";

    private static final String CHAIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    @Test
    public void legacyFormatMatchesTheWebsite() {
        assertEquals(LEGACY_VECTOR, Password.encrypt("Secret123"));
    }

    @Test
    public void pbkdf2FormatMatchesTheWebsite() {
        byte[] salt = new byte[16];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) i;
        }
        assertEquals(PBKDF2_VECTOR, Password.pbkdf2("Secret123", salt, 210000));
    }

    @Test
    public void verifiesBothFormats() {
        assertTrue(Password.matches("Secret123", LEGACY_VECTOR));
        assertTrue(Password.matches("Secret123", PBKDF2_VECTOR));
        assertFalse(Password.matches("secret123", LEGACY_VECTOR));
        assertFalse(Password.matches("secret123", PBKDF2_VECTOR));
    }

    @Test
    public void rejectsMalformedHashes() {
        assertFalse(Password.matches("Secret123", null));
        assertFalse(Password.matches("Secret123", ""));
        assertFalse(Password.matches("Secret123", "pbkdf2_sha512$0$AAAA$AAAA"));
        assertFalse(Password.matches("Secret123", "pbkdf2_sha512$210000$not base64!$AAAA"));
        assertFalse(Password.matches("Secret123", "pbkdf2_sha512$210000$AAAA"));
    }

    @Test
    public void hashesFollowTheConfiguredScheme() {
        assertEquals(LEGACY_VECTOR, Password.hash("Secret123", Password.SCHEME_LEGACY));

        String hash = Password.hash("Secret123", Password.SCHEME_PBKDF2);
        assertTrue(hash.startsWith("pbkdf2_sha512$210000$"));
        assertNotEquals("the salt is random", hash, Password.hash("Secret123", Password.SCHEME_PBKDF2));
        assertTrue(Password.matches("Secret123", hash));
    }

    @Test
    public void rehashOnlyWhenMigratingToPbkdf2() {
        assertFalse(Password.needsRehash(LEGACY_VECTOR, Password.SCHEME_LEGACY));
        assertTrue(Password.needsRehash(LEGACY_VECTOR, Password.SCHEME_PBKDF2));
        assertFalse(Password.needsRehash(PBKDF2_VECTOR, Password.SCHEME_PBKDF2));
        assertTrue(Password.needsRehash("pbkdf2_sha512$1000$AAAA$AAAA", Password.SCHEME_PBKDF2));
    }

    @Test
    public void decryptsWhatTheDofusClientSends() {
        String key = "abcdefghijklmnopqrstuvwxyzabcdef";
        String password = "Secret 123!é";
        assertEquals(password, Password.decryptPassword(clientEncrypt(password, key), key));
    }

    /** The Dofus client's password encoding, as implemented by the login test client. */
    private static String clientEncrypt(String password, String key) {
        StringBuilder out = new StringBuilder("#1");
        for (int i = 0; i < password.length(); i++) {
            int code = password.charAt(i);
            int keyCode = key.charAt(i);
            out.append(CHAIN.charAt((code / 16 + keyCode) % 64)).append(CHAIN.charAt((code % 16 + keyCode) % 64));
        }
        return out.toString();
    }
}
