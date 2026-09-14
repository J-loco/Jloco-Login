package org.starloco.locos.account;

/** A character of an account: world_players id, game server id and GM group (0 for players). */
public record Player(int id, int server, int group, boolean logged) {

    public boolean isGameMaster() {
        return group > 0;
    }
}
