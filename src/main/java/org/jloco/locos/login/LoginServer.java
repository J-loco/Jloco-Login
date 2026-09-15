package org.jloco.locos.login;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jloco.locos.config.LoginConfig;
import org.jloco.locos.net.ConnectionRateLimiter;
import org.jloco.locos.net.DofusCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Accepts Dofus clients on the public login port. */
public final class LoginServer {

    private static final Logger log = LoggerFactory.getLogger(LoginServer.class);

    private final LoginConfig.Login config;
    private final PacketDispatcher dispatcher;
    private final SessionRegistry sessions;
    private final ConnectionRateLimiter rateLimiter;
    private final Executor workers;
    private final SecureRandom random;
    private Channel serverChannel;

    public LoginServer(
            LoginConfig.Login config,
            PacketDispatcher dispatcher,
            SessionRegistry sessions,
            ConnectionRateLimiter rateLimiter,
            Executor workers,
            SecureRandom random) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.workers = workers;
        this.random = random;
    }

    public void start(EventLoopGroup bossGroup, EventLoopGroup workerGroup) throws InterruptedException {
        serverChannel = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 256)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(
                                        "idle",
                                        new IdleStateHandler(
                                                0, 0, config.idleTimeout().toSeconds(), TimeUnit.SECONDS));
                        DofusCodec.install(channel.pipeline());
                        channel.pipeline()
                                .addLast(
                                        "login",
                                        new LoginChannelHandler(dispatcher, sessions, rateLimiter, workers, random));
                    }
                })
                .bind(new InetSocketAddress(config.port()))
                .sync()
                .channel();
        log.info("Login server started on port {}", port());
    }

    public int port() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        sessions.all().forEach(LoginSession::close);
    }
}
