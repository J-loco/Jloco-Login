package org.starloco.locos.login;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.account.Account;

/**
 * One Dofus client connection. Its packets are handled sequentially on {@link #executor()}, so the state
 * needs no locking; it is volatile because the disconnect callback runs on the same executor but may be
 * read for logging from the event loop.
 */
public final class LoginSession {

    private static final Logger packets = LoggerFactory.getLogger("org.starloco.locos.login.packets");

    private final long id;
    private final Channel channel;
    private final String key;
    private final Executor executor;
    private volatile LoginState state = new LoginState.WaitingVersion();
    private volatile boolean handedOff;

    public LoginSession(long id, Channel channel, String key, Executor executor) {
        this.id = id;
        this.channel = channel;
        this.key = key;
        this.executor = executor;
    }

    public long id() {
        return id;
    }

    /** The key sent in "HC", used to decode the password. Never log it. */
    public String key() {
        return key;
    }

    public Executor executor() {
        return executor;
    }

    public LoginState state() {
        return state;
    }

    public void state(LoginState state) {
        this.state = state;
    }

    /** The account once the password (or switch token) was accepted. */
    public Optional<Account> account() {
        return state instanceof LoginState.InMenu menu ? Optional.of(menu.account()) : Optional.empty();
    }

    /** The player selected a game server: the login server no longer owns the account's "logged" flag. */
    public boolean handedOff() {
        return handedOff;
    }

    public void handOff() {
        this.handedOff = true;
    }

    public String ip() {
        return remoteIp(channel);
    }

    /** The address the client connected to (the login server's side of the socket). */
    public String localIp() {
        return channel.localAddress() instanceof InetSocketAddress address
                ? address.getAddress().getHostAddress()
                : "";
    }

    public boolean isOpen() {
        return channel.isActive();
    }

    public void send(String packet) {
        if (packets.isDebugEnabled()) {
            packets.debug("[{}] > {}", id, packet.startsWith("HC") ? "HC<key>" : packet);
        }
        channel.writeAndFlush(packet);
    }

    /** Closes the connection once everything already written has been sent. */
    public void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    public void sendAndClose(String packet) {
        send(packet);
        close();
    }

    static String remoteIp(Channel channel) {
        return channel.remoteAddress() instanceof InetSocketAddress address
                ? address.getAddress().getHostAddress()
                : String.valueOf(channel.remoteAddress());
    }
}
