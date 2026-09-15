package org.jloco.locos.exchange;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Login ↔ game exchange protocol, version 2. Messages are UTF-8 lines ending with "\n".
 *
 * <pre>
 * login → game   SK?2;&lt;nonce&gt;                          challenge (64 hex characters)
 * game  → login  SK&lt;id&gt;;&lt;hmac&gt;;&lt;free places&gt;        hmac = hex HMAC-SHA256(world_servers.key, nonce)
 * login → game   SKK | SKR                             accepted | refused (then closed)
 * game  → login  SH&lt;ip&gt;;&lt;port&gt;  → SHK                 public address of the game socket
 * game  → login  SS&lt;state&gt;                            0 offline, 1 online, 2 saving
 * login → game   F?     game → login  F&lt;free places&gt;
 * login → game   WA&lt;account id&gt;                       an account is on its way
 * login → game   WK&lt;account id&gt;                       kick the account
 * game  → login  SB&lt;ip&gt;                               an IP was banned: close its login sessions
 * game  → login  DM&lt;message&gt;                          relayed to the other game servers
 * </pre>
 *
 * JLoco-Game implements the other side in org.jloco.locos.exchange.
 */
public final class ExchangeProtocol {

    public static final int VERSION = 2;
    public static final int MAX_LINE_BYTES = 16 * 1024;

    private static final HexFormat HEX = HexFormat.of();

    private ExchangeProtocol() {}

    public static String newNonce(SecureRandom random) {
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        return HEX.formatHex(nonce);
    }

    public static String challenge(String nonce) {
        return "SK?" + VERSION + ";" + nonce;
    }

    public static String sign(String serverKey, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(serverKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(nonce.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** Constant-time comparison of the game server's answer with the expected signature. */
    public static boolean verify(String serverKey, String nonce, String signature) {
        return MessageDigest.isEqual(
                sign(serverKey, nonce).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }
}
