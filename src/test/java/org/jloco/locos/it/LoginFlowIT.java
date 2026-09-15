package org.jloco.locos.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The Dofus client's view of the login server, black box over TCP. These tests describe the wire contract
 * the client depends on: they must keep passing across refactors.
 */
@Tag("integration")
class LoginFlowIT {

    /** Hash of "Secret123" in the legacy format (hex SHA-512 of hex MD5), shared with JLoco-Web. */
    static final String LEGACY_HASH =
            "feb8908e6856152712b5206ec3d0e2b0ef9bbd4c9d518fdfde5851ba5673125f45615b7ced2a6f177e37cbe9820c9f250192d48ccb4d01f5b3ea77a86fc09b5c";

    static final String PASSWORD = "Secret123";
    static final int SERVER_ID = 601;

    private static final AtomicInteger ACCOUNTS = new AtomicInteger();

    private static TestDatabase database;
    private static LoginServerProcess server;

    @BeforeAll
    static void startServer() throws Exception {
        database = TestDatabase.create();
        database.addServer(SERVER_ID, "game-key", false);
        server = LoginServerProcess.start(database, "pbkdf2");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    private static String newAccountName() {
        return "flow" + ACCOUNTS.incrementAndGet();
    }

    @Test
    void sendsThePolicyFileThenALoginKey() throws Exception {
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            assertThat(client.received().get(0)).contains("<cross-domain-policy>");
            assertThat(client.key()).matches("[a-z]{32}");
        }
    }

    @Test
    void rejectsAnOlderClientVersion() throws Exception {
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.send("1.29.1");
            assertThat(client.next()).isEqualTo("AlEv" + LoginServerProcess.VERSION);
            assertThat(client.isClosedByServer()).isTrue();
        }
    }

    @Test
    void rejectsAnUnknownAccount() throws Exception {
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login("nobody", PASSWORD);
            assertThat(client.next()).isEqualTo("AlEf");
            assertThat(client.isClosedByServer()).isTrue();
        }
    }

    @Test
    void rejectsAWrongPassword() throws Exception {
        String name = newAccountName();
        database.addAccount(name, LEGACY_HASH, "Pseudo" + name);
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login(name, "wrong-password");
            assertThat(client.next()).isEqualTo("AlEf");
            assertThat(client.isClosedByServer()).isTrue();
        }
    }

    @Test
    void sendsTheAccountInformationAfterASuccessfulLogin() throws Exception {
        String name = newAccountName();
        int guid = database.addAccount(name, LEGACY_HASH, "Pseudo" + name);
        database.addPlayer(guid, SERVER_ID, 0);

        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login(name, PASSWORD);
            client.send("Af");
            assertThat(client.nextStartingWith("Af")).isEqualTo("Af0|0|0|1|-1");
            assertThat(client.next()).isEqualTo("AdPseudo" + name);
            assertThat(client.next()).isEqualTo("Ac0");
            assertThat(client.next()).isEqualTo("AH" + SERVER_ID + ";0;110;1");
            assertThat(client.next()).isEqualTo("AlK0");
            assertThat(client.next()).isEqualTo("AQQuestion?");

            client.send("Ax");
            assertThat(client.nextStartingWith("Ax")).isEqualTo("AxK0|" + SERVER_ID + ",1");

            assertThat(database.query("SELECT logged FROM world_accounts WHERE guid = ?", guid))
                    .as("state 1: in the login server")
                    .isEqualTo("1");

            client.send("AX" + SERVER_ID);
            assertThat(client.nextStartingWith("AX"))
                    .as("the game server is offline")
                    .isEqualTo("AXEd");
        }
    }

    @Test
    void upgradesALegacyHashWhenTheSchemeIsPbkdf2() throws Exception {
        String name = newAccountName();
        int guid = database.addAccount(name, LEGACY_HASH, "Pseudo" + name);

        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login(name, PASSWORD);
            client.send("Af");
            client.nextStartingWith("Ad");
        }
        String hash = database.query("SELECT pass FROM world_accounts WHERE guid = ?", guid);
        assertThat(hash).startsWith("pbkdf2_sha512$210000$");

        database.update("UPDATE world_accounts SET logged = 0 WHERE guid = ?", guid);
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login(name, PASSWORD);
            client.send("Af");
            assertThat(client.nextStartingWith("Ad")).isEqualTo("AdPseudo" + name);
        }
        assertThat(database.query("SELECT pass FROM world_accounts WHERE guid = ?", guid))
                .as("an up-to-date hash is not rewritten")
                .isEqualTo(hash);
    }

    @Test
    void asksForANicknameWhenTheAccountHasNone() throws Exception {
        String name = newAccountName();
        int guid = database.addAccount(name, LEGACY_HASH, null);

        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login(name, PASSWORD);
            client.send("Af");
            assertThat(client.nextStartingWith("Al")).isEqualTo("AlEr");

            client.send("Nick" + name);
            assertThat(client.nextStartingWith("Ad")).isEqualTo("AdNick" + name);
        }
        assertThat(database.query("SELECT pseudo FROM world_accounts WHERE guid = ?", guid))
                .isEqualTo("Nick" + name);
    }

    @Test
    void refusesABannedAccount() throws Exception {
        String name = newAccountName();
        int guid = database.addAccount(name, LEGACY_HASH, "Pseudo" + name);
        database.update("UPDATE world_accounts SET banned = 1 WHERE guid = ?", guid);

        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login(name, PASSWORD);
            client.send("Af");
            assertThat(client.nextStartingWith("Al")).isEqualTo("AlEb");
            assertThat(client.isClosedByServer()).isTrue();
        }
    }

    @Test
    void neverLogsTheLoginKeyOrThePassword() throws Exception {
        String name = newAccountName();
        database.addAccount(name, LEGACY_HASH, "Pseudo" + name);
        String key;
        String encodedPassword;
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            key = client.key();
            encodedPassword = DofusTestClient.encryptPassword(PASSWORD, key);
            client.login(name, PASSWORD);
            client.send("Af");
            client.nextStartingWith("Ad");
        }
        String logs = server.output();
        assertThat(logs).as("packet traces are enabled").contains("> Ad");
        assertThat(logs).doesNotContain(key).doesNotContain(encodedPassword).doesNotContain(PASSWORD);
    }

    @Test
    void closesAConnectionThatSendsAnOversizedFrame() throws Exception {
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.send("A".repeat(5000));
            assertThat(client.isClosedByServer()).isTrue();
        }
    }
}
