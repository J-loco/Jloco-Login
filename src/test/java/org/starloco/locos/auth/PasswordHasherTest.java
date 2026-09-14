package org.starloco.locos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

/**
 * The vectors are shared with StarLoco-Web (tests/Unit/Security/PasswordHasherTest.php): a hash written by
 * either the website or the login server must verify on the other.
 */
class PasswordHasherTest {

    /** SHA-512 of MD5("Secret123"). */
    static final String LEGACY_VECTOR =
            "feb8908e6856152712b5206ec3d0e2b0ef9bbd4c9d518fdfde5851ba5673125f45615b7ced2a6f177e37cbe9820c9f250192d48ccb4d01f5b3ea77a86fc09b5c";

    /** PBKDF2-HMAC-SHA512("Secret123", salt = bytes 0..15, 210000 iterations, 64 bytes). */
    static final String PBKDF2_VECTOR =
            "pbkdf2_sha512$210000$AAECAwQFBgcICQoLDA0ODw==$iZtt5xyR1Etmq2tlOF9CV/QVTjl/9i+yhjOPuGSDI6QIyQ1QkpLGIX79y9cibM8TrXmsPxPWGmYpatx0UdBWfQ==";

    private final PasswordHasher legacy = new PasswordHasher(PasswordScheme.LEGACY, new SecureRandom());
    private final PasswordHasher pbkdf2 = new PasswordHasher(PasswordScheme.PBKDF2, new SecureRandom());

    @Test
    void legacyFormatMatchesTheWebsite() {
        assertThat(PasswordHasher.legacy("Secret123")).isEqualTo(LEGACY_VECTOR);
    }

    @Test
    void pbkdf2FormatMatchesTheWebsite() {
        byte[] salt = new byte[16];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) i;
        }
        assertThat(PasswordHasher.pbkdf2("Secret123", salt, 210_000)).isEqualTo(PBKDF2_VECTOR);
    }

    @Test
    void verifiesBothFormatsWhateverTheScheme() {
        for (PasswordHasher hasher : new PasswordHasher[] {legacy, pbkdf2}) {
            assertThat(hasher.matches("Secret123", LEGACY_VECTOR)).isTrue();
            assertThat(hasher.matches("Secret123", PBKDF2_VECTOR)).isTrue();
            assertThat(hasher.matches("secret123", LEGACY_VECTOR)).isFalse();
            assertThat(hasher.matches("secret123", PBKDF2_VECTOR)).isFalse();
        }
    }

    @Test
    void rejectsMalformedHashes() {
        assertThat(legacy.matches("Secret123", null)).isFalse();
        assertThat(legacy.matches("Secret123", "")).isFalse();
        assertThat(legacy.matches("Secret123", "pbkdf2_sha512$0$AAAA$AAAA")).isFalse();
        assertThat(legacy.matches("Secret123", "pbkdf2_sha512$210000$not base64!$AAAA"))
                .isFalse();
        assertThat(legacy.matches("Secret123", "pbkdf2_sha512$210000$AAAA")).isFalse();
        assertThat(legacy.matches("Secret123", "pbkdf2_sha512$many$AAAA$AAAA")).isFalse();
    }

    @Test
    void newHashesFollowTheConfiguredScheme() {
        assertThat(legacy.hash("Secret123")).isEqualTo(LEGACY_VECTOR);

        String hash = pbkdf2.hash("Secret123");
        assertThat(hash).startsWith("pbkdf2_sha512$210000$");
        assertThat(pbkdf2.hash("Secret123")).as("the salt is random").isNotEqualTo(hash);
        assertThat(pbkdf2.matches("Secret123", hash)).isTrue();
    }

    @Test
    void rehashOnlyWhenMigratingToPbkdf2() {
        assertThat(legacy.needsRehash(LEGACY_VECTOR)).isFalse();
        assertThat(pbkdf2.needsRehash(LEGACY_VECTOR)).isTrue();
        assertThat(pbkdf2.needsRehash(PBKDF2_VECTOR)).isFalse();
        assertThat(pbkdf2.needsRehash("pbkdf2_sha512$1000$AAAA$AAAA"))
                .as("fewer iterations")
                .isTrue();
    }
}
