package org.starloco.locos.account;

import org.starloco.locos.database.Jdbc;

/** administration_ban_ip, written by the game server's BANIP command. */
public final class BanRepository {

    private final Jdbc jdbc;

    public BanRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isBanned(String ip) {
        return jdbc.exists("SELECT 1 FROM administration_ban_ip WHERE ip = ?", ip);
    }
}
