package org.starloco.locos.exchange;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One game server connection: challenge-response authentication, then the messages of {@link ExchangeProtocol}. */
final class ExchangeChannelHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(ExchangeChannelHandler.class);

    private final GameServerRegistry registry;
    private final Consumer<String> ipBanned;
    private final String nonce;
    private final Duration authenticationTimeout;
    private WorldServer server;

    ExchangeChannelHandler(
            GameServerRegistry registry,
            Consumer<String> ipBanned,
            SecureRandom random,
            Duration authenticationTimeout) {
        this.registry = registry;
        this.ipBanned = ipBanned;
        this.nonce = ExchangeProtocol.newNonce(random);
        this.authenticationTimeout = authenticationTimeout;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Exchange connection from {}", remote(ctx));
        ctx.writeAndFlush(ExchangeProtocol.challenge(nonce));
        ctx.executor()
                .schedule(
                        () -> {
                            if (server == null && ctx.channel().isActive()) {
                                log.warn("Exchange connection from {} did not authenticate in time", remote(ctx));
                                ctx.close();
                            }
                        },
                        authenticationTimeout.toMillis(),
                        TimeUnit.MILLISECONDS);
        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String message) {
        if (message.isEmpty()) {
            return;
        }
        if (server == null) {
            authenticate(ctx, message);
            return;
        }
        log.debug("Exchange < server {}: {}", server.id(), message);
        try {
            handle(ctx, message);
        } catch (RuntimeException e) {
            log.warn("Invalid exchange message from server {}: {}", server.id(), message, e);
        }
    }

    private void authenticate(ChannelHandlerContext ctx, String message) {
        List<String> parts =
                message.startsWith("SK") ? List.of(message.substring(2).split(";", -1)) : List.of();
        WorldServer candidate = null;
        if (parts.size() == 3) {
            try {
                candidate = registry.find(Integer.parseInt(parts.get(0)))
                        .filter(found ->
                                !found.key().isEmpty() && ExchangeProtocol.verify(found.key(), nonce, parts.get(1)))
                        .orElse(null);
                if (candidate != null) {
                    candidate.freePlaces(Integer.parseInt(parts.get(2)));
                }
            } catch (NumberFormatException e) {
                candidate = null;
            }
        }
        if (candidate == null) {
            log.warn(
                    "Exchange authentication refused for {} ({})",
                    remote(ctx),
                    parts.isEmpty() ? message : "server " + parts.get(0));
            ctx.writeAndFlush("SKR").addListener(ChannelFutureListener.CLOSE);
            return;
        }

        Channel previous = candidate.link();
        if (previous != null && !previous.equals(ctx.channel()) && previous.isActive()) {
            log.warn(
                    "Game server {} connected again from {}: closing its previous connection",
                    candidate.id(),
                    remote(ctx));
            previous.close();
        }
        server = candidate;
        server.link(ctx.channel());
        log.info("Game server {} authenticated from {}", server.id(), remote(ctx));
        ctx.writeAndFlush("SKK");
    }

    private void handle(ChannelHandlerContext ctx, String message) {
        switch (message.charAt(0)) {
            case 'F' -> server.freePlaces(Integer.parseInt(message.substring(1)));
            case 'S' -> handleServer(ctx, message);
            case 'D' -> {
                if (message.startsWith("DM")) {
                    registry.linked().stream().filter(other -> other != server).forEach(other -> other.send(message));
                }
            }
            default -> log.warn("Unknown exchange message from server {}: {}", server.id(), message);
        }
    }

    private void handleServer(ChannelHandlerContext ctx, String message) {
        String payload = message.length() > 2 ? message.substring(2) : "";
        switch (message.length() > 1 ? message.charAt(1) : ' ') {
            case 'H' -> {
                String[] address = payload.split(";", -1);
                server.address(address[0], Integer.parseInt(address[1]));
                ctx.writeAndFlush("SHK");
            }
            case 'S' -> registry.changeState(server, Integer.parseInt(payload));
            case 'B' -> ipBanned.accept(payload);
            default -> log.warn("Unknown exchange message from server {}: {}", server.id(), message);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (server != null && ctx.channel().equals(server.link())) {
            log.warn("Game server {} disconnected", server.id());
            server.link(null);
            registry.changeState(server, WorldServer.OFFLINE);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Exchange connection {} failed: {}", remote(ctx), cause.toString());
        ctx.close();
    }

    private static String remote(ChannelHandlerContext ctx) {
        return ctx.channel().remoteAddress() instanceof InetSocketAddress address
                ? address.getAddress().getHostAddress() + ":" + address.getPort()
                : String.valueOf(ctx.channel().remoteAddress());
    }
}
