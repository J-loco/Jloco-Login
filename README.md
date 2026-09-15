# JLoco - Login

Login server of JLoco, a Dofus 1.39.8 emulator: account authentication, server list and server
selection for the client, and the private exchange channel the game servers register on.

Java 21, Netty 4.2, HikariCP + MariaDB Connector/J, SLF4J/Logback, no application framework.

## Requirements

- JDK 21 (Gradle downloads one if none is installed)
- MariaDB 10+ with the `jloco_login` database (`login.sql`)
- Docker, only for the integration tests and the image

## Usage

```bash
./gradlew installDist                         # build/install/login
build/install/login/bin/login --write-sample-config   # writes login.config.properties, then edit it
build/install/login/bin/login                 # run (login.bat on Windows, or start.bat)
```

Configuration: `login.config.properties` in the working directory (or the path in `JLOCO_LOGIN_CONFIG`).
Every key can be overridden by an environment variable: `JLOCO_LOGIN_` + the key in upper case with dots
replaced by underscores, e.g. `JLOCO_LOGIN_DATABASE_LOGIN_PASS`. Logs go to the console and `logs/login.log`
(`LOGIN_LOG_LEVEL=DEBUG` adds packet traces; passwords, tokens and login keys are never logged).

Console commands (standard input): `HELP`, `SERVERS`, `SESSIONS`, `UPTIME`, `AUTHORIZED <ip>`,
`MAINTAIN <account>`, `PASSWORD <password>`, `SEND <session id> <packet>`.

With Docker, the full stack runs from JLoco-Game (`docker compose up`), which builds this image.

## Development

```bash
./gradlew check            # formatting (Spotless), Error Prone, unit tests
./gradlew integrationTest  # the server as a separate process against MariaDB in Testcontainers
./gradlew spotlessApply    # format the code
```

- The game servers must speak the same exchange protocol version (`ExchangeProtocol`, currently 2): update
  JLoco-Game together with this server.
- Password hashes are shared with JLoco-Web: `auth/PasswordHasher` and its test vectors must match
  `src/Security/PasswordHasher.php` there.
- Design and history of the Java 21 migration: [docs/modernization.md](docs/modernization.md).

## Contribute

Feel free to open an issue or create a pull request.

<a href="https://discord.com/invite/k3Yk9DuhgY">![Discord Banner 2](https://discordapp.com/api/guilds/856945561421086730/widget.png?style=banner2)</a>

## Thanks us by buying us a coffee

<a href="https://www.buymeacoffee.com/jloco" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174"></a>
