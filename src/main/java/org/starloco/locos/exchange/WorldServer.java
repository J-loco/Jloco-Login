package org.starloco.locos.exchange;

import io.netty.channel.Channel;

/**
 * A game server from world_servers and its live state. The state is written by the exchange event loop and
 * read by login sessions, hence the volatile fields.
 */
public final class WorldServer {

    public static final int OFFLINE = 0;
    public static final int ONLINE = 1;

    private final int id;
    private final String key;
    private final boolean subscriberOnly;

    private volatile int state = OFFLINE;
    private volatile int freePlaces;
    private volatile String host = "";
    private volatile int port;
    private volatile Channel link;

    public WorldServer(int id, String key, boolean subscriberOnly) {
        this.id = id;
        this.key = key == null ? "" : key;
        this.subscriberOnly = subscriberOnly;
    }

    public int id() {
        return id;
    }

    String key() {
        return key;
    }

    public boolean subscriberOnly() {
        return subscriberOnly;
    }

    public int state() {
        return state;
    }

    void state(int state) {
        this.state = state;
    }

    public int freePlaces() {
        return freePlaces;
    }

    void freePlaces(int freePlaces) {
        this.freePlaces = freePlaces;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    void address(String host, int port) {
        this.host = host;
        this.port = port;
    }

    Channel link() {
        return link;
    }

    void link(Channel link) {
        this.link = link;
    }

    public boolean isLinked() {
        Channel channel = link;
        return channel != null && channel.isActive();
    }

    /** Sends an exchange message if the game server is connected. */
    public void send(String message) {
        Channel channel = link;
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
        }
    }
}
