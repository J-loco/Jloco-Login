package org.jloco.locos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class PasswordCipherTest {

    private static final String KEY = "abcdefghijklmnopqrstuvwxyzabcdef";

    @Test
    void decodesWhatTheDofusClientSends() {
        String password = "Secret 123!é";
        assertThat(PasswordCipher.decode(PasswordCipher.encode(password, KEY), KEY))
                .contains(password);
    }

    @Test
    void roundTripsWithRandomKeys() {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 100; i++) {
            String key = LoginKey.generate(random);
            assertThat(PasswordCipher.decode(PasswordCipher.encode("pass-" + i + "~ok", key), key))
                    .contains("pass-" + i + "~ok");
        }
    }

    @Test
    void rejectsMalformedPackets() {
        assertThat(PasswordCipher.decode("#1", KEY)).isEmpty();
        assertThat(PasswordCipher.decode("#1abc", KEY)).as("odd length").isEmpty();
        assertThat(PasswordCipher.decode("#1a!", KEY))
                .as("character outside the alphabet")
                .isEmpty();
        assertThat(PasswordCipher.decode("#1" + "ab".repeat(33), KEY))
                .as("longer than the key")
                .isEmpty();
    }

    @Test
    void loginKeysAreThirtyTwoLowercaseLetters() {
        assertThat(LoginKey.generate(new SecureRandom())).matches("[a-z]{32}");
    }
}
