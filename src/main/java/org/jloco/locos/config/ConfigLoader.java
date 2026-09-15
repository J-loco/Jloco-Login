package org.jloco.locos.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.jloco.locos.auth.PasswordScheme;
import org.jloco.locos.login.ClientVersion;

/**
 * Reads login.config.properties ("key value" lines, the format written by the previous versions) and
 * applies environment overrides: every key can be set with JLOCO_LOGIN_ followed by the key in upper
 * case with dots replaced by underscores, e.g. JLOCO_LOGIN_DATABASE_LOGIN_PASS.
 */
public final class ConfigLoader {

    public static final String EXCHANGE_HOST = "system.server.exchange.ip";
    public static final String EXCHANGE_PORT = "system.server.exchange.port";
    public static final String EXCHANGE_KEY = "system.server.exchange.key";
    public static final String LOGIN_PORT = "system.server.login.port";
    public static final String CLIENT_VERSION = "system.server.login.version";
    public static final String PASSWORD_SCHEME = "system.server.login.password.scheme";
    public static final String CONNECTIONS_PER_MINUTE = "system.server.login.connections.per.minute";
    public static final String IDLE_TIMEOUT_SECONDS = "system.server.login.idle.timeout.seconds";
    public static final String DB_HOST = "database.login.host";
    public static final String DB_PORT = "database.login.port";
    public static final String DB_USER = "database.login.user";
    public static final String DB_PASS = "database.login.pass";
    public static final String DB_NAME = "database.login.name";

    private static final String ENV_PREFIX = "JLOCO_LOGIN_";

    private ConfigLoader() {}

    /** Loads the file (which may be absent when every required key comes from the environment). */
    public static LoginConfig load(Path file, Map<String, String> environment) {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new ConfigException("Cannot read " + file.toAbsolutePath() + ": " + e.getMessage());
            }
        }
        return parse(new Values(properties, environment, file));
    }

    private static LoginConfig parse(Values values) {
        LoginConfig.Exchange exchange = new LoginConfig.Exchange(
                values.required(EXCHANGE_HOST), values.port(EXCHANGE_PORT), values.required(EXCHANGE_KEY));
        LoginConfig.Login login = new LoginConfig.Login(
                values.port(LOGIN_PORT),
                values.clientVersion(),
                values.positiveInt(CONNECTIONS_PER_MINUTE, 30),
                Duration.ofSeconds(values.positiveInt(IDLE_TIMEOUT_SECONDS, 300)));
        LoginConfig.Database database = new LoginConfig.Database(
                values.required(DB_HOST),
                values.port(DB_PORT),
                values.required(DB_USER),
                values.optional(DB_PASS, ""),
                values.required(DB_NAME));
        PasswordScheme scheme = values.passwordScheme();

        if (exchange.key() != null && !isStrongKey(exchange.key())) {
            values.problems.add(EXCHANGE_KEY
                    + " must be base64 of at least 32 bytes (it signs the character-switch tokens shared with the"
                    + " game server)");
        }
        if (!values.problems.isEmpty()) {
            throw new ConfigException("Invalid configuration (" + values.source.toAbsolutePath() + "):\n  - "
                    + String.join("\n  - ", values.problems)
                    + "\nRun with --write-sample-config to create a template.");
        }
        return new LoginConfig(exchange, login, database, scheme);
    }

    private static boolean isStrongKey(String key) {
        try {
            return Base64.getDecoder().decode(key).length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String environmentName(String key) {
        return ENV_PREFIX + key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    /** A commented template with every key, for --write-sample-config. */
    public static String sample() {
        return String.join(
                "\n",
                "# JLoco login server. Every key can also be set in the environment, e.g.",
                "# " + environmentName(DB_PASS) + "=secret",
                "",
                "# Exchange channel for the game servers (keep it private).",
                EXCHANGE_HOST + " 127.0.0.1",
                EXCHANGE_PORT + " 666",
                "# Base64, at least 32 bytes; the same value as in game.config.properties.",
                EXCHANGE_KEY + " ChangeMeYn2kjibddFAWtnPJ2AFlL8WXmohJMCvigQggaEypa5E=",
                "",
                "# Dofus client socket.",
                LOGIN_PORT + " 450",
                "# Oldest accepted client version.",
                CLIENT_VERSION + " 1.39.8e",
                "# legacy (default) or pbkdf2: must match PASSWORD_HASH_SCHEME of JLoco-Web.",
                PASSWORD_SCHEME + " legacy",
                "# New connections accepted per client IP per minute.",
                CONNECTIONS_PER_MINUTE + " 30",
                IDLE_TIMEOUT_SECONDS + " 300",
                "",
                DB_HOST + " 127.0.0.1",
                DB_PORT + " 3306",
                DB_USER + " root",
                DB_PASS + " ",
                DB_NAME + " jloco_login",
                "");
    }

    private static final class Values {
        private final Properties properties;
        private final Map<String, String> environment;
        private final Path source;
        private final List<String> problems = new ArrayList<>();

        Values(Properties properties, Map<String, String> environment, Path source) {
            this.properties = properties;
            this.environment = environment;
            this.source = source;
        }

        private String raw(String key) {
            String value = environment.get(environmentName(key));
            if (value == null) {
                value = properties.getProperty(key);
            }
            return value == null ? null : value.trim();
        }

        String optional(String key, String fallback) {
            String value = raw(key);
            return value == null ? fallback : value;
        }

        String required(String key) {
            String value = raw(key);
            if (value == null || value.isEmpty()) {
                problems.add(key + " is missing");
                return null;
            }
            return value;
        }

        int port(String key) {
            String value = required(key);
            if (value == null) {
                return 0;
            }
            try {
                int port = Integer.parseInt(value);
                if (port >= 1 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
                // reported below
            }
            problems.add(key + " must be a port number, got \"" + value + "\"");
            return 0;
        }

        int positiveInt(String key, int fallback) {
            String value = raw(key);
            if (value == null || value.isEmpty()) {
                return fallback;
            }
            try {
                int number = Integer.parseInt(value);
                if (number > 0) {
                    return number;
                }
            } catch (NumberFormatException ignored) {
                // reported below
            }
            problems.add(key + " must be a positive number, got \"" + value + "\"");
            return fallback;
        }

        ClientVersion clientVersion() {
            String value = required(CLIENT_VERSION);
            if (value == null) {
                return null;
            }
            return ClientVersion.parse(value).orElseGet(() -> {
                problems.add(CLIENT_VERSION + " must look like 1.39.8e, got \"" + value + "\"");
                return null;
            });
        }

        PasswordScheme passwordScheme() {
            String value = optional(PASSWORD_SCHEME, "legacy");
            return PasswordScheme.parse(value).orElseGet(() -> {
                problems.add(PASSWORD_SCHEME + " must be legacy or pbkdf2, got \"" + value + "\"");
                return PasswordScheme.LEGACY;
            });
        }
    }
}
