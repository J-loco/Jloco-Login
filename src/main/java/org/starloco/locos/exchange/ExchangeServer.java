package org.starloco.locos.exchange;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.LineSeparator;
import io.netty.handler.codec.string.StringDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Listens for game servers on the private exchange port. */
public final class ExchangeServer {

    private static final Logger log = LoggerFactory.getLogger(ExchangeServer.class);

    private final InetSocketAddress address;
    private final GameServerRegistry registry;
    private final Consumer<String> ipBanned;
    private final SecureRandom random;
    private Channel serverChannel;

    public ExchangeServer(
            InetSocketAddress address, GameServerRegistry registry, Consumer<String> ipBanned, SecureRandom random) {
        this.address = address;
        this.registry = registry;
        this.ipBanned = ipBanned;
        this.random = random;
    }

    public void start(EventLoopGroup group) throws InterruptedException {
        serverChannel = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast("frames", new LineBasedFrameDecoder(ExchangeProtocol.MAX_LINE_BYTES))
                                .addLast("decoder", new StringDecoder(StandardCharsets.UTF_8))
                                .addLast("encoder", new LineEncoder(LineSeparator.UNIX, StandardCharsets.UTF_8))
                                .addLast(
                                        "exchange",
                                        new ExchangeChannelHandler(registry, ipBanned, random, Duration.ofSeconds(10)));
                    }
                })
                .bind(address)
                .sync()
                .channel();
        log.info("Exchange server started on {}:{}", address.getHostString(), port());
    }

    /** The bound port (useful when configured with 0). */
    public int port() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        for (WorldServer server : registry.linked()) {
            Channel link = server.link();
            if (link != null) {
                link.close().syncUninterruptibly();
            }
        }
    }
}
