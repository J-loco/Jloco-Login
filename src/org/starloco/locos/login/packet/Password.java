package org.starloco.locos.login.packet;

import org.starloco.locos.kernel.Config;
import org.starloco.locos.kernel.Main;
import org.starloco.locos.login.LoginClient;
import org.starloco.locos.login.LoginClient.Status;
import org.starloco.locos.object.Account;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Account password check at login, and the password hash formats shared with StarLoco-Web
 * (src/Security/PasswordHasher.php). world_accounts.pass holds either:
 *  - legacy: hex SHA-512 of the hex MD5 of the password;
 *  - pbkdf2: "pbkdf2_sha512$iterations$base64(salt)$base64(64-byte key)".
 * Both are accepted. With system.server.login.password.scheme = pbkdf2, a legacy hash is replaced
 * by a PBKDF2 one after a successful login.
 */
public class Password {

    public static final String SCHEME_LEGACY = "legacy";
    public static final String SCHEME_PBKDF2 = "pbkdf2";

    /** Must match StarLoco-Web PasswordHasher::PBKDF2_ITERATIONS. */
    static final int PBKDF2_ITERATIONS = 210000;
    private static final String PBKDF2_PREFIX = "pbkdf2_sha512";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 512;
    private static final SecureRandom RANDOM = new SecureRandom();

    static void verify(LoginClient client, String pass) {
        InetAddress inetAddress = ((InetSocketAddress) client.getIoSession().getRemoteAddress()).getAddress();
        String IP = inetAddress.getHostAddress();

        if (!Config.loginServer.authorizedIp.contains(IP)) {
            String password = decryptPassword(pass, client.getKey());
            Account account = client.getAccount();
            if (!matches(password, account.getPass())) {
                client.send("AlEf");
                client.kick();
                return;
            }
            if (needsRehash(account.getPass(), Config.passwordScheme)) {
                Main.database.getAccountData().updatePassword(account.getUUID(), hash(password, Config.passwordScheme));
            }
        } else {
            client.setMaintain();
        }

        client.setStatus(Status.SERVER);
    }

    static String decryptPassword(String pass, String key) {
        if (pass.startsWith("#1"))
            pass = pass.substring(2);
        String chain = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

        char PPass, PKey;
        int APass, AKey, ANB, ANB2, somme1, somme2;

        StringBuilder decrypted = new StringBuilder();

        for (int i = 0; i < pass.length(); i += 2) {
            PKey = key.charAt(i / 2);
            ANB = chain.indexOf(pass.charAt(i));
            ANB2 = chain.indexOf(pass.charAt(i + 1));

            somme1 = ANB + chain.length();
            somme2 = ANB2 + chain.length();

            APass = somme1 - (int) PKey;
            if (APass < 0)
                APass += 64;
            APass *= 16;

            AKey = somme2 - (int) PKey;
            if (AKey < 0)
                AKey += 64;

            PPass = (char) (APass + AKey);

            decrypted.append(PPass);
        }

        return decrypted.toString();
    }

    /** Whether the plain password matches a stored hash of either format. */
    public static boolean matches(String password, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (storedHash.startsWith(PBKDF2_PREFIX + "$")) {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 4) {
                return false;
            }
            try {
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                if (iterations < 1 || expected.length == 0) {
                    return false;
                }
                return MessageDigest.isEqual(expected, pbkdf2Key(password, salt, iterations, expected.length * 8));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.US_ASCII), encrypt(password).getBytes(StandardCharsets.US_ASCII));
    }

    /** Whether a verified hash should be replaced by one in the configured scheme. */
    public static boolean needsRehash(String storedHash, String scheme) {
        return SCHEME_PBKDF2.equals(scheme) && (storedHash == null || !storedHash.startsWith(PBKDF2_PREFIX + "$" + PBKDF2_ITERATIONS + "$"));
    }

    /** A new hash of the password in the given scheme. */
    public static String hash(String password, String scheme) {
        if (!SCHEME_PBKDF2.equals(scheme)) {
            return encrypt(password);
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return pbkdf2(password, salt, PBKDF2_ITERATIONS);
    }

    static String pbkdf2(String password, byte[] salt, int iterations) {
        Base64.Encoder encoder = Base64.getEncoder();
        return PBKDF2_PREFIX + "$" + iterations + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(pbkdf2Key(password, salt, iterations, KEY_BITS));
    }

    private static byte[] pbkdf2Key(String password, byte[] salt, int iterations, int bits) {
        try {
            // SunJCE encodes the password characters as UTF-8, like PHP's hash_pbkdf2 on a UTF-8 string.
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA512 is not available", e);
        }
    }

    private static String cryptPassword(String message, String type) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(type);
            md.update(message.getBytes(StandardCharsets.UTF_8));
            byte[] mb = md.digest();
            StringBuilder out = new StringBuilder();
            for (byte temp : mb) {
                StringBuilder s = new StringBuilder(Integer.toHexString(temp));
                while (s.length() < 2) {
                    s.insert(0, "0");
                }
                s = new StringBuilder(s.substring(s.length() - 2));
                out.append(s);
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(type + " is not available", e);
        }
    }

    /** Legacy format: hex SHA-512 of the hex MD5. */
    public static String encrypt(String password) {
        return cryptPassword(cryptPassword(password, "MD5"), "SHA-512");
    }
}
