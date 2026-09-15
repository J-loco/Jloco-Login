package org.jloco.locos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class CharacterSwitchTokenTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    private static final String OTHER_KEY = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    private final CharacterSwitchToken tokens = new CharacterSwitchToken(KEY);

    /** The token JLoco-Game builds in GameClient#switchCharacter. */
    private static String gameServerToken(String key, String issuer, Instant expiry) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject("player")
                .claim("ip", "203.0.113.7")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(key)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void acceptsATokenFromTheGameServer() {
        String token =
                gameServerToken(KEY, CharacterSwitchToken.ISSUER, Instant.now().plusSeconds(30));
        assertThat(tokens.verify(token)).contains(new CharacterSwitchToken.Claim("player", "203.0.113.7"));
    }

    @Test
    void rejectsForgedExpiredOrForeignTokens() {
        assertThat(tokens.verify(gameServerToken(
                        OTHER_KEY, CharacterSwitchToken.ISSUER, Instant.now().plusSeconds(30))))
                .as("other key")
                .isEmpty();
        assertThat(tokens.verify(gameServerToken(
                        KEY, CharacterSwitchToken.ISSUER, Instant.now().minusSeconds(60))))
                .as("expired")
                .isEmpty();
        assertThat(tokens.verify(
                        gameServerToken(KEY, "SomeoneElse", Instant.now().plusSeconds(30))))
                .as("issuer")
                .isEmpty();
        assertThat(tokens.verify("not-a-token")).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
    }
}
