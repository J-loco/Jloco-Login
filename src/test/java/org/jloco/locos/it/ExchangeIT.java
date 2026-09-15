package org.jloco.locos.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Game servers on the exchange channel (protocol v2), and players sent to them. */
@Tag("integration")
class ExchangeIT {

    private static final int SERVER_ID = 602;
    private static final String SERVER_KEY = "game-key-602";
    private static final int OTHER_SERVER_ID = 603;
    private static final String OTHER_SERVER_KEY = "game-key-603";
    private static final int GAME_PORT = 5556;

    private static TestDatabase database;
    private static LoginServerProcess server;

    @BeforeAll
    static void startServer() throws Exception {
        database = TestDatabase.create();
        database.addServer(SERVER_ID, SERVER_KEY, false);
        database.addServer(OTHER_SERVER_ID, OTHER_SERVER_KEY, false);
        server = LoginServerProcess.start(database, "legacy");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void aRegisteredGameServerReceivesThePlayersSentToIt() throws Exception {
        int guid = database.addAccount("exchange1", LoginFlowIT.LEGACY_HASH, "Exchanger");

        try (FakeGameServer game = FakeGameServer.connect(server.exchangePort());
                DofusTestClient client =
                        DofusTestClient.connect(server.loginPort()).handshake()) {
            game.register(SERVER_ID, SERVER_KEY, GAME_PORT);

            client.login("exchange1", LoginFlowIT.PASSWORD);
            client.send("Af");
            assertThat(client.nextMatching(packet -> packet.startsWith("AH") && packet.contains(SERVER_ID + ";1;")))
                    .contains(SERVER_ID + ";1;110;1");

            client.send("AX" + SERVER_ID);
            assertThat(client.nextStartingWith("AY")).isEqualTo("AYK127.0.0.1:" + GAME_PORT + ";" + guid);
            assertThat(game.nextStartingWith("WA")).isEqualTo("WA" + guid);
        }
    }

    @Test
    void aGameServerWithAWrongKeyIsRefusedAndStaysOffline() throws Exception {
        try (FakeGameServer impostor = FakeGameServer.connect(server.exchangePort())) {
            impostor.authenticate(OTHER_SERVER_ID, "not-the-key");
            assertThat(impostor.next()).isEqualTo("SKR");
            assertThat(impostor.isClosedByServer()).isTrue();
        }
        try (FakeGameServer impostor = FakeGameServer.connect(server.exchangePort())) {
            impostor.next(); // challenge
            impostor.send("SS1");
            assertThat(impostor.next())
                    .as("nothing is accepted before authentication")
                    .isEqualTo("SKR");
        }
        int guid = database.addAccount("exchange2", LoginFlowIT.LEGACY_HASH, "Exchanger2");
        try (DofusTestClient client =
                DofusTestClient.connect(server.loginPort()).handshake()) {
            client.login("exchange2", LoginFlowIT.PASSWORD);
            client.send("Af");
            assertThat(client.nextStartingWith("AH")).contains(OTHER_SERVER_ID + ";0;110;1");
            client.send("AX" + OTHER_SERVER_ID);
            assertThat(client.nextStartingWith("AX")).isEqualTo("AXEd");
        }
        assertThat(guid).isPositive();
    }

    @Test
    void messagesWrittenTogetherAreAllHandled() throws Exception {
        try (FakeGameServer game = FakeGameServer.connect(server.exchangePort())) {
            game.authenticate(OTHER_SERVER_ID, OTHER_SERVER_KEY);
            game.expect("SKK");
            // Host, state and free places in one TCP write: protocol v1 misread this as one message.
            game.send("SH127.0.0.1;" + GAME_PORT, "SS1", "F42");
            game.expect("SHK");

            int guid = database.addAccount("exchange3", LoginFlowIT.LEGACY_HASH, "Exchanger3");
            try (DofusTestClient client =
                    DofusTestClient.connect(server.loginPort()).handshake()) {
                client.login("exchange3", LoginFlowIT.PASSWORD);
                client.send("Af");
                assertThat(client.nextStartingWith("AH")).contains(OTHER_SERVER_ID + ";1;110;1");
                client.send("AX" + OTHER_SERVER_ID);
                assertThat(client.nextStartingWith("AY")).isEqualTo("AYK127.0.0.1:" + GAME_PORT + ";" + guid);
            }
        }
    }

    @Test
    void anIpBannedByAGameServerLosesItsLoginConnections() throws Exception {
        try (FakeGameServer game = FakeGameServer.connect(server.exchangePort());
                DofusTestClient client =
                        DofusTestClient.connect(server.loginPort()).handshake()) {
            game.register(SERVER_ID, SERVER_KEY, GAME_PORT);
            game.send("SB127.0.0.1");
            assertThat(client.isClosedByServer()).isTrue();
        }
    }
}
