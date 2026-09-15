package org.jloco.locos.exchange;

import java.util.List;
import org.jloco.locos.database.Jdbc;

/** world_servers: the game servers allowed to register, with their shared keys. */
public final class WorldServerRepository {

    public record Row(int id, String key, boolean subscriberOnly) {}

    private final Jdbc jdbc;

    public WorldServerRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> findAll() {
        return jdbc.list(
                "SELECT id, `key`, isSubscriberServer FROM world_servers ORDER BY id",
                row -> new Row(row.getInt("id"), row.getString("key"), row.getInt("isSubscriberServer") == 1));
    }
}
