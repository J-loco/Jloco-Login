package org.jloco.locos.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.embedded.EmbeddedChannel;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The exchange handler without sockets: the channel carries decoded lines in and strings out. */
class ExchangeChannelHandlerTest {

    private final GameServerRegistry registry = new GameServerRegistry();
    private final List<String> hostLists = new CopyOnWriteArrayList<>();
    private final List<String> bannedIps = new CopyOnWriteArrayList<>();

    @BeforeEach
    void servers() {
        registry.load(List.of(
                new WorldServerRepository.Row(601, "key-601", false),
                new WorldServerRepository.Row(602, "key-602", false),
                new WorldServerRepository.Row(603, null, false)));
        registry.onHostListChange(hostLists::add);
    }

    private EmbeddedChannel connect() {
        return new EmbeddedChannel(
                new ExchangeChannelHandler(registry, bannedIps::add, new SecureRandom(), Duration.ofSeconds(10)));
    }

    private static String nonce(EmbeddedChannel channel) {
        String challenge = channel.readOutbound();
        assertThat(challenge).matches("SK\\?2;[0-9a-f]{64}");
        return challenge.substring("SK?2;".length());
    }

    private EmbeddedChannel authenticated(int id, String key) {
        EmbeddedChannel channel = connect();
        channel.writeInbound("SK" + id + ";" + ExchangeProtocol.sign(key, nonce(channel)) + ";50");
        assertThat((String) channel.readOutbound()).isEqualTo("SKK");
        return channel;
    }

    @Test
    void acceptsAGameServerThatSignsTheChallenge() {
        EmbeddedChannel channel = authenticated(601, "key-601");
        WorldServer server = registry.find(601).orElseThrow();
        assertThat(server.isLinked()).isTrue();
        assertThat(server.freePlaces()).isEqualTo(50);

        channel.writeInbound("SH203.0.113.10;5555");
        assertThat((String) channel.readOutbound()).isEqualTo("SHK");
        channel.writeInbound("SS1");
        assertThat(server.host()).isEqualTo("203.0.113.10");
        assertThat(server.port()).isEqualTo(5555);
        assertThat(hostLists).containsExactly("AH601;1;110;1|602;0;110;1|603;0;110;1");
    }

    @Test
    void refusesAWrongSignatureAndRegistersNothing() {
        EmbeddedChannel channel = connect();
        nonce(channel);
        channel.writeInbound("SK601;" + "0".repeat(64) + ";50");
        assertThat((String) channel.readOutbound()).isEqualTo("SKR");
        channel.runPendingTasks();
        assertThat(channel.isOpen()).isFalse();
        assertThat(registry.find(601).orElseThrow().isLinked()).isFalse();
        assertThat(hostLists).isEmpty();
    }

    @Test
    void aSignatureForAnotherNonceIsRefused() {
        EmbeddedChannel first = connect();
        String firstNonce = nonce(first);
        EmbeddedChannel replay = connect();
        nonce(replay);
        replay.writeInbound("SK601;" + ExchangeProtocol.sign("key-601", firstNonce) + ";50");
        assertThat((String) replay.readOutbound()).isEqualTo("SKR");
    }

    @Test
    void refusesEverythingBeforeAuthenticationAndServersWithoutKey() {
        EmbeddedChannel early = connect();
        nonce(early);
        early.writeInbound("SS1");
        assertThat((String) early.readOutbound()).isEqualTo("SKR");

        EmbeddedChannel keyless = connect();
        nonce(keyless);
        keyless.writeInbound("SK603;" + "0".repeat(64) + ";50");
        assertThat((String) keyless.readOutbound()).isEqualTo("SKR");

        EmbeddedChannel unknown = connect();
        unknown.writeInbound("SK999;" + ExchangeProtocol.sign("key-601", nonce(unknown)) + ";50");
        assertThat((String) unknown.readOutbound()).isEqualTo("SKR");
    }

    @Test
    void closingTheLinkPutsTheServerOffline() {
        EmbeddedChannel channel = authenticated(601, "key-601");
        channel.writeInbound("SS1");
        channel.close();
        WorldServer server = registry.find(601).orElseThrow();
        assertThat(server.isLinked()).isFalse();
        assertThat(server.state()).isZero();
        assertThat(hostLists).last().isEqualTo("AH601;0;110;1|602;0;110;1|603;0;110;1");
    }

    @Test
    void relaysMessagesToTheOtherServersAndReportsBans() {
        EmbeddedChannel first = authenticated(601, "key-601");
        EmbeddedChannel second = authenticated(602, "key-602");

        first.writeInbound("DMPlayer;Server;Hello");
        assertThat((String) second.readOutbound()).isEqualTo("DMPlayer;Server;Hello");
        assertThat(first.<Object>readOutbound()).as("not echoed to the sender").isNull();

        second.writeInbound("SB198.51.100.4");
        assertThat(bannedIps).containsExactly("198.51.100.4");
    }

    @Test
    void aMalformedMessageDoesNotDropTheLink() {
        EmbeddedChannel channel = authenticated(601, "key-601");
        channel.writeInbound("Fnot-a-number");
        channel.writeInbound("S");
        channel.writeInbound("F12");
        assertThat(channel.isOpen()).isTrue();
        assertThat(registry.find(601).orElseThrow().freePlaces()).isEqualTo(12);
    }

    @Test
    void theHmacIsTheOneJLocoGameComputes() {
        // HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog"), RFC 4231-style public vector.
        assertThat(ExchangeProtocol.sign("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
        assertThat(ExchangeProtocol.verify(
                        "key",
                        "The quick brown fox jumps over the lazy dog",
                        "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"))
                .isTrue();
        assertThat(ExchangeProtocol.verify("key", "The quick brown fox jumps over the lazy dog", "f7bc"))
                .isFalse();
    }
}
