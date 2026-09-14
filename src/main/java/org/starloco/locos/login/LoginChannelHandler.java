package org.starloco.locos.login;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.timeout.IdleStateEvent;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.auth.LoginKey;
import org.starloco.locos.net.ConnectionRateLimiter;
import org.starloco.locos.net.SerialExecutor;

/**
 * Netty side of a Dofus connection: opens the session, then hands every packet to the session's serial
 * executor (virtual threads), where {@link PacketDispatcher} runs with blocking database access.
 */
final class LoginChannelHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(LoginChannelHandler.class);
    private static final AtomicLong IDS = new AtomicLong();

    static final String POLICY = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<cross-domain-policy>"
            + "<site-control permitted-cross-domain-policies=\"all\"/>\n"
            + "<allow-access-from domain=\"*\" to-ports=\"*\" secure=\"false\"/>\n"
            + "<allow-http-request-headers-from domain=\"*\" headers=\"*\" secure=\"false\"/>"
            + "</cross-domain-policy>";

    private final PacketDispatcher dispatcher;
    private final SessionRegistry sessions;
    private final ConnectionRateLimiter rateLimiter;
    private final Executor workers;
    private final SecureRandom random;
    private LoginSession session;

    LoginChannelHandler(
            PacketDispatcher dispatcher,
            SessionRegistry sessions,
            ConnectionRateLimiter rateLimiter,
            Executor workers,
            SecureRandom random) {
        this.dispatcher = dispatcher;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.workers = workers;
        this.random = random;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String ip = LoginSession.remoteIp(ctx.channel());
        if (!rateLimiter.tryAcquire(ip)) {
            log.warn("Too many connections from {}: refused", ip);
            ctx.close();
            return;
        }
        session = new LoginSession(
                IDS.incrementAndGet(), ctx.channel(), LoginKey.generate(random), new SerialExecutor(workers));
        sessions.add(session);
        log.info("[{}] Connection from {}", session.id(), ip);
        session.send(POLICY);
        session.send("HC" + session.key());
        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String frame) {
        if (session == null) {
            return;
        }
        for (String packet : frame.split("\n", -1)) {
            String trimmed = packet.strip();
            if (!trimmed.isEmpty()) {
                session.executor().execute(() -> dispatcher.handle(session, trimmed));
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        if (event instanceof IdleStateEvent && session != null) {
            log.info("[{}] Idle connection closed", session.id());
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(event);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (session != null) {
            LoginSession closed = session;
            sessions.remove(closed);
            closed.executor().execute(() -> dispatcher.disconnected(closed));
            log.info("[{}] Connection closed", closed.id());
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        long id = session == null ? 0 : session.id();
        if (cause instanceof TooLongFrameException) {
            log.warn("[{}] Frame too long: connection closed", id);
        } else {
            log.debug("[{}] Connection error: {}", id, cause.toString());
        }
        ctx.close();
    }
}
