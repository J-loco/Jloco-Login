package org.jloco.locos.exchange;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** The known game servers, ordered by id. Changes of the server list are broadcast to login clients. */
public final class GameServerRegistry {

    private final Map<Integer, WorldServer> servers = new ConcurrentSkipListMap<>();
    private volatile Consumer<String> hostListListener = hostList -> {};

    public void load(List<WorldServerRepository.Row> rows) {
        for (WorldServerRepository.Row row : rows) {
            servers.put(row.id(), new WorldServer(row.id(), row.key(), row.subscriberOnly()));
        }
    }

    /** Called with the new "AH" packet whenever a server's state changes. */
    public void onHostListChange(Consumer<String> listener) {
        this.hostListListener = listener;
    }

    public Optional<WorldServer> find(int id) {
        return Optional.ofNullable(servers.get(id));
    }

    public Collection<WorldServer> all() {
        return servers.values();
    }

    public List<WorldServer> linked() {
        return servers.values().stream().filter(WorldServer::isLinked).toList();
    }

    /** "AH" followed by id;state;completion;canLog for each server. */
    public String hostList() {
        return servers.values().stream()
                .map(server -> server.id() + ";" + server.state() + ";110;1")
                .collect(Collectors.joining("|", "AH", ""));
    }

    /** State set by a game master (the game server's next "SS" message overrides it). */
    public void forceState(WorldServer server, int state) {
        changeState(server, state);
    }

    void changeState(WorldServer server, int state) {
        server.state(state);
        hostListListener.accept(hostList());
    }
}
