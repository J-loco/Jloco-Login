package org.starloco.locos.it;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.mariadb.MariaDBContainer;

/** One MariaDB container for the whole test run, with a fresh starloco_login schema per test class. */
public final class TestDatabase {

    private static final MariaDBContainer CONTAINER = new MariaDBContainer("mariadb:11.3")
            .withDatabaseName("starloco_login")
            .withUsername("root")
            .withPassword("test");

    private static final AtomicInteger NAMES = new AtomicInteger();

    private final String name;

    private TestDatabase(String name) {
        this.name = name;
    }

    /** Starts the container on first use and creates an empty database with the login schema. */
    public static TestDatabase create() {
        synchronized (CONTAINER) {
            if (!CONTAINER.isRunning()) {
                CONTAINER.start();
            }
        }
        TestDatabase database = new TestDatabase("starloco_login_it" + NAMES.incrementAndGet());
        database.createSchema();
        return database;
    }

    public String host() {
        return CONTAINER.getHost();
    }

    public int port() {
        return CONTAINER.getMappedPort(3306);
    }

    public String name() {
        return name;
    }

    public String user() {
        return CONTAINER.getUsername();
    }

    public String password() {
        return CONTAINER.getPassword();
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:mariadb://" + host() + ":" + port() + "/" + name, user(), password());
    }

    private void createSchema() {
        String schema;
        try (InputStream in = TestDatabase.class.getResourceAsStream("/schema/login.sql")) {
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try (Connection connection = DriverManager.getConnection(
                        "jdbc:mariadb://" + host() + ":" + port() + "/", user(), password());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + name + "`");
            statement.execute("USE `" + name + "`");
            for (String sql : schema.split(";\\s*\\n", -1)) {
                if (!sql.isBlank() && sql.contains("CREATE TABLE")) {
                    statement.execute(sql.substring(sql.indexOf("CREATE TABLE")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create the test schema", e);
        }
    }

    public void addServer(int id, String key, boolean subscriberOnly) throws SQLException {
        update(
                "INSERT INTO world_servers (id, `key`, isSubscriberServer, name) VALUES (?, ?, ?, ?)",
                id,
                key,
                subscriberOnly ? 1 : 0,
                "Server " + id);
    }

    /** Inserts an account and returns its guid. A null pseudo means "nickname not chosen yet". */
    public int addAccount(String account, String passwordHash, String pseudo) throws SQLException {
        update(
                "INSERT INTO world_accounts (account, pass, pseudo, question) VALUES (?, ?, ?, ?)",
                account,
                passwordHash,
                pseudo,
                "Question?");
        return Integer.parseInt(query("SELECT guid FROM world_accounts WHERE account = ?", account));
    }

    public void addPlayer(int accountId, int server, int group) throws SQLException {
        update(
                "INSERT INTO world_players (name, account, groupe, sexe, class, color1, color2, color3, kamas,"
                        + " spellboost, capital, level, size, gfx, map, cell, spells, objets, storeObjets, server)"
                        + " VALUES (?, ?, ?, 0, 1, -1, -1, -1, 0, 0, 0, 1, 100, 10, 7411, 311, '', '', '', ?)",
                "Player" + NAMES.incrementAndGet(),
                accountId,
                group,
                server);
    }

    public void update(String sql, Object... params) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = prepare(connection, sql, params)) {
            statement.executeUpdate();
        }
    }

    /** The first column of the first row, or null. */
    public String query(String sql, Object... params) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = prepare(connection, sql, params);
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }
}
