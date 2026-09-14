package org.starloco.locos.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.starloco.locos.config.LoginConfig;

/** Connection pool of the login database. */
public final class DataSources {

    private DataSources() {}

    /** Opens the pool and checks the connection: fails fast with the driver's message. */
    public static HikariDataSource open(LoginConfig.Database database) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("login-db");
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.user());
        config.setPassword(database.password());
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
        config.setInitializationFailTimeout(Duration.ofSeconds(30).toMillis());
        return new HikariDataSource(config);
    }
}
