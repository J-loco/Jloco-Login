package org.starloco.locos.account;

import java.util.List;
import org.starloco.locos.database.Jdbc;

/** world_players (read-only, except the "logged" flag reset when a stale session is kicked). */
public final class PlayerRepository {

    private final Jdbc jdbc;

    public PlayerRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public List<Player> findByAccount(int accountId) {
        return jdbc.list(
                "SELECT id, server, groupe, logged FROM world_players WHERE account = ? ORDER BY id",
                row -> new Player(
                        row.getInt("id"), row.getInt("server"), row.getInt("groupe"), row.getInt("logged") == 1),
                accountId);
    }

    /** The game server id where one of the account's characters is flagged as connected, 0 when none. */
    public int loggedServer(int accountId) {
        return findByAccount(accountId).stream()
                .filter(Player::logged)
                .mapToInt(Player::server)
                .reduce((first, second) -> second)
                .orElse(0);
    }

    public void resetLogged(int accountId) {
        jdbc.update("UPDATE world_players SET logged = 0 WHERE account = ?", accountId);
    }
}
