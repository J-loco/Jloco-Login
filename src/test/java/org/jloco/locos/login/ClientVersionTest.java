package org.jloco.locos.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientVersionTest {

    private static ClientVersion version(String text) {
        return ClientVersion.parse(text).orElseThrow();
    }

    @Test
    void parsesTheElectronSuffix() {
        assertThat(version("1.39.8e")).isEqualTo(new ClientVersion(1, 39, 8, true));
        assertThat(version("1.29.1")).isEqualTo(new ClientVersion(1, 29, 1, false));
        assertThat(version("1.39.8e")).hasToString("1.39.8e");
    }

    @Test
    void comparesNumerically() {
        assertThat(version("1.39.10").isAtLeast(version("1.39.8e")))
                .as("not a string comparison")
                .isTrue();
        assertThat(version("1.39.8").isAtLeast(version("1.39.8e")))
                .as("the suffix does not matter")
                .isTrue();
        assertThat(version("1.29.1").isAtLeast(version("1.39.8e"))).isFalse();
        assertThat(version("2.0.0").isAtLeast(version("1.39.8e"))).isTrue();
    }

    @Test
    void rejectsGarbage() {
        assertThat(ClientVersion.parse("")).isEmpty();
        assertThat(ClientVersion.parse("1.39")).isEmpty();
        assertThat(ClientVersion.parse("<policy-file-request/>")).isEmpty();
        assertThat(ClientVersion.parse("1.39.8x")).isEmpty();
    }
}
