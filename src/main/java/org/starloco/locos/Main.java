package org.starloco.locos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starloco.locos.config.ConfigException;
import org.starloco.locos.config.ConfigLoader;
import org.starloco.locos.config.LoginConfig;
import org.starloco.locos.console.AdminConsole;

/**
 * Entry point. The configuration file is login.config.properties in the working directory, or the path in
 * STARLOCO_LOGIN_CONFIG.
 *
 * <pre>
 * java -jar login.jar                         run
 * java -jar login.jar --write-sample-config   write a commented template and exit
 * </pre>
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {}

    public static void main(String[] args) throws InterruptedException {
        Path configFile = Path.of(System.getenv().getOrDefault("STARLOCO_LOGIN_CONFIG", "login.config.properties"));

        if (Arrays.asList(args).contains("--write-sample-config")) {
            writeSampleConfig(configFile);
            return;
        }

        LoginConfig config;
        try {
            config = ConfigLoader.load(configFile, System.getenv());
        } catch (ConfigException e) {
            log.error(e.getMessage());
            System.exit(2);
            return;
        }

        LoginApplication application = new LoginApplication(config);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime()
                .addShutdownHook(Thread.ofPlatform().name("shutdown").unstarted(() -> {
                    application.close();
                    stopped.countDown();
                }));

        try {
            application.start();
        } catch (RuntimeException e) {
            log.error("The login server could not start: {}", e.getMessage(), e);
            System.exit(1);
        }

        AdminConsole.start(application);
        stopped.await();
    }

    private static void writeSampleConfig(Path file) {
        if (Files.exists(file)) {
            log.error("{} already exists: not overwritten", file.toAbsolutePath());
            System.exit(2);
        }
        try {
            Files.writeString(file, ConfigLoader.sample(), StandardCharsets.UTF_8);
            log.info("Sample configuration written to {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.error("Cannot write {}: {}", file.toAbsolutePath(), e.getMessage());
            System.exit(2);
        }
    }
}
