package org.starloco.locos;

import com.zaxxer.hikari.HikariDataSource;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.account.AccountRepository;
import org.starloco.locos.account.BanRepository;
import org.starloco.locos.account.PlayerRepository;
import org.starloco.locos.auth.CharacterSwitchToken;
import org.starloco.locos.auth.PasswordHasher;
import org.starloco.locos.config.LoginConfig;
import org.starloco.locos.database.DataSources;
import org.starloco.locos.database.Jdbc;
import org.starloco.locos.exchange.ExchangeServer;
import org.starloco.locos.exchange.GameServerRegistry;
import org.starloco.locos.exchange.WorldServerRepository;
import org.starloco.locos.login.AdminState;
import org.starloco.locos.login.LoginServer;
import org.starloco.locos.login.LoginServices;
import org.starloco.locos.login.PacketDispatcher;
import org.starloco.locos.login.SessionRegistry;
import org.starloco.locos.net.ConnectionRateLimiter;

/** Builds the object graph and owns the lifecycle: database, exchange server, login server, timers. */
public final class LoginApplication implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LoginApplication.class);

    private final LoginConfig config;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();
    private final SessionRegistry sessions = new SessionRegistry();
    private final GameServerRegistry servers = new GameServerRegistry();
    private final AdminState admin = new AdminState();
    private final ExecutorService workers = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("login-worker-", 0).factory());
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("login-timer").daemon(true).factory());

    private final List<ScheduledFuture<?>> periodic = new CopyOnWriteArrayList<>();
    private HikariDataSource dataSource;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExchangeServer exchangeServer;
    private LoginServer loginServer;
    private final long startedAt = System.nanoTime();

    public LoginApplication(LoginConfig config) {
        this.config = config;
    }

    public void start() throws InterruptedException {
        log.info("Starting the login server on Java {} ({})", Runtime.version(), System.getProperty("java.vendor"));
        log.info("Database {}, password scheme {}", config.database(), config.passwordScheme());

        dataSource = DataSources.open(config.database());
        Jdbc jdbc = new Jdbc(dataSource);
        servers.load(new WorldServerRepository(jdbc).findAll());
        log.info("{} game server(s) known: {}", servers.all().size(), servers.hostList());

        LoginServices services = new LoginServices(
                config,
                new AccountRepository(jdbc),
                new PlayerRepository(jdbc),
                new BanRepository(jdbc),
                servers,
                sessions,
                admin,
                new PasswordHasher(config.passwordScheme(), random),
                new CharacterSwitchToken(config.exchange().key()),
                clock);
        servers.onHostListChange(sessions::broadcast);

        ConnectionRateLimiter rateLimiter =
                new ConnectionRateLimiter(config.login().connectionsPerMinute(), Duration.ofMinutes(1), clock);

        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        exchangeServer = new ExchangeServer(
                new InetSocketAddress(
                        config.exchange().host(), config.exchange().port()),
                servers,
                ip -> {
                    int closed = sessions.closeIp(ip);
                    log.info("IP {} banned by a game server: {} login connection(s) closed", ip, closed);
                },
                random);
        exchangeServer.start(workerGroup);

        loginServer =
                new LoginServer(config.login(), new PacketDispatcher(services), sessions, rateLimiter, workers, random);
        loginServer.start(bossGroup, workerGroup);

        periodic.add(timers.scheduleAtFixedRate(
                logged("free places poll", () -> servers.linked().forEach(server -> server.send("F?"))),
                30,
                30,
                TimeUnit.SECONDS));
        periodic.add(
                timers.scheduleAtFixedRate(logged("rate limiter purge", rateLimiter::purge), 1, 1, TimeUnit.MINUTES));
        log.info(
                "Login server ready in {} ms",
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }

    /** A periodic task must not die silently: an uncaught exception would cancel all its next runs. */
    private static Runnable logged(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Periodic task \"{}\" failed", name, e);
            }
        };
    }

    public SessionRegistry sessions() {
        return sessions;
    }

    public GameServerRegistry servers() {
        return servers;
    }

    public AdminState admin() {
        return admin;
    }

    public PasswordHasher passwordHasher() {
        return new PasswordHasher(config.passwordScheme(), random);
    }

    public Duration uptime() {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    @Override
    public void close() {
        log.info("Stopping the login server");
        periodic.forEach(task -> task.cancel(false));
        timers.shutdownNow();
        if (loginServer != null) {
            loginServer.stop();
        }
        if (exchangeServer != null) {
            exchangeServer.stop();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
        workers.close();
        if (dataSource != null) {
            dataSource.close();
        }
        log.info("Login server stopped");
    }
}
