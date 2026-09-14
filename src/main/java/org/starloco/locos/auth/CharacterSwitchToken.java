package org.starloco.locos.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * The token a game server gives a player who switches character (1.39.8 "#S"): an HS256 JWS signed with
 * the exchange key, issued by "StarLocoGameServer", whose subject is the account name and "ip" claim the
 * player's address. StarLoco-Game creates it in GameClient#switchCharacter.
 */
public final class CharacterSwitchToken {

    static final String ISSUER = "StarLocoGameServer";

    private final JwtParser parser;

    public CharacterSwitchToken(String base64Key) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Key));
        this.parser = Jwts.parser()
                .requireIssuer(ISSUER)
                .clockSkewSeconds(Duration.ofSeconds(5).toSeconds())
                .verifyWith(key)
                .build();
    }

    public record Claim(String accountName, String ip) {}

    /** The verified claims, or empty for an invalid, expired or foreign token. */
    public Optional<Claim> verify(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String ip = claims.get("ip", String.class);
            if (claims.getSubject() == null || ip == null) {
                return Optional.empty();
            }
            return Optional.of(new Claim(claims.getSubject(), ip));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
