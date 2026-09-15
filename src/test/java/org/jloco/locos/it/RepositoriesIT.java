package org.jloco.locos.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.jloco.locos.account.Account;
import org.jloco.locos.account.AccountRepository;
import org.jloco.locos.account.BanRepository;
import org.jloco.locos.account.PlayerRepository;
import org.jloco.locos.config.LoginConfig;
import org.jloco.locos.database.DataSources;
import org.jloco.locos.database.Jdbc;
import org.jloco.locos.exchange.WorldServerRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Repositories against the real schema. */
@Tag("integration")
class RepositoriesIT {

    private static TestDatabase database;
    private static HikariDataSource dataSource;
    private static AccountRepository accounts;
    private static PlayerRepository players;
    private static BanRepository bans;
    private static WorldServerRepository servers;

    @BeforeAll
    static void open() {
        database = TestDatabase.create();
        dataSource = DataSources.open(new LoginConfig.Database(
                database.host(), database.port(), database.user(), database.password(), database.name()));
        Jdbc jdbc = new Jdbc(dataSource);
        accounts = new AccountRepository(jdbc);
        players = new PlayerRepository(jdbc);
        bans = new BanRepository(jdbc);
        servers = new WorldServerRepository(jdbc);
    }

    @AfterAll
    static void close() {
        dataSource.close();
    }

    @Test
    void findsAccountsByNameIgnoringCaseAndMapsNullNicknames() throws Exception {
        int id = database.addAccount("RepoCase", LoginFlowIT.LEGACY_HASH, null);

        Account account = accounts.findByName("repocase").orElseThrow();
        assertThat(account.id()).isEqualTo(id);
        assertThat(account.pseudo()).isEmpty();
        assertThat(account.hasPseudo()).isFalse();
        assertThat(account.question()).isEqualTo("Question?");
        assertThat(accounts.findByName("repo%")).as("no LIKE patterns").isEmpty();
        assertThat(accounts.findById(id)).contains(account);
    }

    @Test
    void aNicknameCanBeChosenOnlyOnce() throws Exception {
        int id = database.addAccount("repoNick", LoginFlowIT.LEGACY_HASH, null);

        assertThat(accounts.choosePseudo(id, "Chosen")).isTrue();
        assertThat(accounts.choosePseudo(id, "Other")).isFalse();
        assertThat(accounts.pseudoExists("Chosen")).isTrue();
        assertThat(accounts.findByPseudo("Chosen").orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void updatesOnlyTheColumnsTheLoginServerOwns() throws Exception {
        int id = database.addAccount("repoOwn", LoginFlowIT.LEGACY_HASH, "Own");
        database.update("UPDATE world_accounts SET banned = 1, bannedTime = 123, email = 'a@b.c' WHERE guid = ?", id);

        accounts.setLogged(id, 1);
        accounts.updatePassword(id, "pbkdf2_sha512$210000$x$y");
        accounts.liftBan(id);

        Account account = accounts.findById(id).orElseThrow();
        assertThat(account.logged()).isEqualTo(1);
        assertThat(account.passwordHash()).isEqualTo("pbkdf2_sha512$210000$x$y");
        assertThat(account.banned()).isFalse();
        assertThat(account.bannedUntil()).isZero();
        assertThat(database.query("SELECT email FROM world_accounts WHERE guid = ?", id))
                .isEqualTo("a@b.c");
    }

    @Test
    void ipBansMatchExactly() throws Exception {
        database.update("INSERT INTO administration_ban_ip (ip) VALUES ('203.0.113.9')");
        assertThat(bans.isBanned("203.0.113.9")).isTrue();
        assertThat(bans.isBanned("203.0.113.90")).isFalse();
        assertThat(bans.isBanned("ip")).isFalse();
    }

    @Test
    void readsCharactersAndTheirConnectedServer() throws Exception {
        int id = database.addAccount("repoPlayers", LoginFlowIT.LEGACY_HASH, "Players");
        database.addPlayer(id, 601, 0);
        database.addPlayer(id, 602, 3);
        assertThat(players.findByAccount(id)).hasSize(2).anyMatch(player -> player.isGameMaster());
        assertThat(players.loggedServer(id)).isZero();

        database.update("UPDATE world_players SET logged = 1 WHERE account = ? AND server = 602", id);
        assertThat(players.loggedServer(id)).isEqualTo(602);

        players.resetLogged(id);
        assertThat(players.loggedServer(id)).isZero();
    }

    @Test
    void loadsTheGameServers() throws Exception {
        database.addServer(700, "key-700", true);
        assertThat(servers.findAll()).contains(new WorldServerRepository.Row(700, "key-700", true));
    }
}
