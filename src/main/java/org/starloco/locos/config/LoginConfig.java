package org.starloco.locos.config;

import java.time.Duration;
import org.starloco.locos.auth.PasswordScheme;
import org.starloco.locos.login.ClientVersion;

/** Settings of the login server, read by {@link ConfigLoader}. */
public record LoginConfig(Exchange exchange, Login login, Database database, PasswordScheme passwordScheme) {

    /** The private channel game servers connect to. */
    public record Exchange(String host, int port, String key) {}

    /** The public socket of the Dofus client. */
    public record Login(int port, ClientVersion minimumClientVersion, int connectionsPerMinute, Duration idleTimeout) {}

    public record Database(String host, int port, String user, String password, String name) {

        public String jdbcUrl() {
            return "jdbc:mariadb://" + host + ":" + port + "/" + name;
        }

        @Override
        public String toString() {
            return "Database[" + user + "@" + host + ":" + port + "/" + name + "]";
        }
    }
}
