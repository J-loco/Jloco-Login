# StarLoco-Login: Java 21 modernization

Status: done (2026-09-14). The login server was rewritten on Java 21 + Netty. It is wire-compatible with the
Dofus 1.39 client and speaks exchange protocol v2 with StarLoco-Game.

## Why

The login server (~3,000 lines) was a Java 8 project:

- **Build:** a Groovy Gradle build with no wrapper.
- **Libraries:** 18 vendored jars (MINA 2.0.9, logback 1.1.2, mysql-connector 5.1.44, log4j 1.2.17, an
  unknown `VPN_Detection.jar`).
- **Code:** static singletons, and string-concatenated SQL behind one global lock.

StarLoco-Game already ran on Java 21.

Decisions:
- **Netty 4.2** instead of MINA.
- **No application framework:** focused libraries, wired by hand in `LoginApplication`.
- **Fix the exchange protocol on both sides:** StarLoco-Game's `exchange/` package changed too.

## Defects found and fixed

| # | Defect (legacy code) | Fix |
|---|---|---|
| L1 | Console loop spun on `NoSuchElementException` without stdin: **102% CPU** in Docker | `AdminConsole` reads stdin until end-of-input. The container measured 0.16% after the change. |
| L2 | Game-server authentication bypass: wrong key → `SKR`, but no `return`, so the server got registered and `SKK` anyway | Challenge-response handshake. Refusal closes the connection. `ExchangeIT`, `ExchangeChannelHandlerTest`. |
| L3 | Exchange channel had no framing: coalesced TCP packets were misparsed. The key was sent in clear text. | `\n`-terminated lines on both sides. HMAC of a nonce instead of the key. |
| L4 | Login key and `#1` password logged together (the encoding is reversible with the key) | Packets traced at DEBUG only, password/token/key masked. `LoginFlowIT#neverLogsTheLoginKeyOrThePassword` enforces it. |
| L5 | Login key from `java.util.Random` | `SecureRandom` (`LoginKey`) |
| L6 | IP bans never matched (`WHERE 'ip' LIKE`). The game's `SB<ip>` was ignored. | `BanRepository` exact match. `SB` closes the login sessions of that IP (the game already writes the ban table). |
| L7 | All SQL serialized by one lock, concatenated queries, `UPDATE accounts` on a table that doesn't exist | `Jdbc` helper: pooled connections, prepared statements only, repositories returning records |
| L8 | Unsynchronized shared `HashMap`/`TreeMap`s. `PacketFilter` grew forever and banned permanently. | Concurrent collections. `ConnectionRateLimiter` (sliding window per IP, periodic purge, no permanent ban). |
| L9 | Ban countdown `hours % 60`, `days % 24` | `AccountQueue.banCountdown` + test |
| L10 | `ExchangeHandler.setLogged` NPE before authentication | Handler rewritten; state is only touched after authentication |
| L11 | `kick()` threw an NPE when no account was loaded (bad version, unknown account): the socket stayed open | `LoginSession.close()` doesn't depend on the account |
| L12 | `close(true)` right after `AlEf`/`AlEb` could drop the error packet: the client saw a plain disconnect | Close only after the pending writes are flushed (`sendAndClose`) |
| L13 | A connection that only typed an account name could kick that account's live session, and reset its `logged` flag on disconnect | The session claims the account only after the password or switch token is verified. The flag is reset only for authenticated sessions that were not handed to a game server. |
| G1 | StarLoco-Game's `.all` chat displayed `system.server.game.key` (the exchange secret) as the server label | `CommandPlayer` shows the server name |

## What it looks like now

```
src/main/java/org/starloco/locos/
  Main, LoginApplication       entry point (--write-sample-config), wiring and lifecycle
  config/   LoginConfig (records), ConfigLoader: same keys as before + STARLOCO_LOGIN_* env overrides, all errors listed at once
  auth/     PasswordHasher (legacy + pbkdf2, vectors shared with StarLoco-Web), PasswordCipher (#1), LoginKey, CharacterSwitchToken (jjwt 0.13)
  database/ DataSources (HikariCP), Jdbc
  account/  Account, Player (records), AccountRepository, PlayerRepository, BanRepository
  exchange/ ExchangeServer, ExchangeChannelHandler, ExchangeProtocol (v2), GameServerRegistry, WorldServer, WorldServerRepository
  login/    LoginServer, LoginChannelHandler, LoginSession, LoginState (sealed), PacketDispatcher (pattern-matching switch), SessionRegistry, AdminState
  login/step/  VersionStep, AccountStep, PasswordStep, NicknameStep, AccountQueue (Af), MenuStep (Ax/AX/AF/BA), GameMasterCommands
  net/      DofusCodec (NUL frames, 4 KiB max), SerialExecutor (per-connection order on virtual threads), ConnectionRateLimiter
  console/  AdminConsole
```

Threading:
- Netty event loops only do I/O.
- Each connection's packets run in order on its `SerialExecutor`, backed by a virtual-thread-per-task
  executor, so blocking JDBC calls never block an event loop.
- The exchange handler runs on the event loop: it does no database work.

## Wire contracts

**Dofus client (port 450), unchanged:**
- UTF-8 frames terminated by NUL.
- On connect: the policy XML, then `HC` + 32 lowercase letters.
- Same packets and error codes as before.
- `<policy-file-request/>` is ignored.
- An idle connection is closed after `system.server.login.idle.timeout.seconds` (300).
- New connections are limited per IP to `system.server.login.connections.per.minute` (30).

**Exchange (port 666), protocol v2** (documented in `ExchangeProtocol`):
- `\n`-terminated UTF-8 lines.
- Handshake: login `SK?2;<nonce>`, then game `SK<id>;<hex HMAC-SHA256(world_servers.key, nonce)>;<free places>`,
  then login `SKK`, or `SKR` and close.
- 10 s to authenticate; nothing else is accepted before that.
- After authentication, unchanged: `SH`/`SHK`, `SS`, `F?`/`F`, `WA`, `WK`, `DM`, plus `SB`.
- A v1 game server, or a v2 game server facing a v1 login server, fails the handshake with a clear log line:
  **deploy both together.**

## Build and tooling

- **Build:** Gradle 9.7.1 wrapper, Kotlin DSL, version catalog (`gradle/libs.versions.toml`), toolchain 21,
  `-Xlint:all -Werror`, Error Prone 2.50 (`FutureReturnValueIgnored` off for Netty's fire-and-forget futures),
  Spotless with palantir-java-format and LF line endings.
- **Libraries:** Netty 4.2.18, HikariCP 7.1, MariaDB Connector/J 3.5, SLF4J 2.0 + Logback 1.6, jjwt 0.13.
  `libs/` and the MINA, log4j, commons-* and VPN detection jars are gone.
- **Tests:** JUnit 6, AssertJ, Netty `EmbeddedChannel`, Testcontainers 2.0 (MariaDB 11.3). 38 unit tests and
  20 integration tests:
  - `LoginFlowIT` and `ExchangeIT` start the real server as a separate JVM and talk to it over TCP.
  - `RepositoriesIT` runs the repositories against the real schema (`src/test/resources/schema/login.sql`,
    dumped from a db-init database).
- **Docker:** multi-stage `Dockerfile`.
  - The build stage runs `check installDist`.
  - The runtime is `eclipse-temurin:21-jre-alpine`, non-root, ZGC.
  - The healthcheck reads the listening sockets instead of opening a connection.
- **CI:**
  - `ci.yml`: check, integration tests, image build.
  - `release.yaml` (tags `v*`): tests, Docker Hub push, GitHub release with the distribution zip.

Differences from the original plan:
- Logback 1.6, JUnit 6 and Gradle 9.7 were the current stable versions.
- The console reads stdin until EOF instead of checking `System.console()`, which never returns null on JDK 22+.
- `SB` closes sessions instead of writing the ban: the game server already inserts it.

## Running and verifying

```bash
cd StarLoco-Login
./gradlew check integrationTest

cd ../StarLoco-Game
./gradlew jar                                    # the game image copies build/libs/game.jar
docker compose build starloco_login starloco_game
docker compose up -d                             # starloco_game waits for a healthy starloco_login
docker compose logs starloco_login | grep "authenticated"   # Game server 601 authenticated from ...
docker stats --no-stream                         # login container idle near 0% CPU
```

Checked on 2026-09-14 against the running stack:
- The real game server authenticated with v2.
- A scripted client:
  - was refused with a wrong password;
  - logged in with the right one, and its legacy hash was upgraded to pbkdf2;
  - saw server 601 online and was sent to it with `AYK…`; the game server received `WA` ("Loading account").
- The portal's `launcher/status.php` reported login and game up.

## Notes

- **Weak server keys:** the seeded `world_servers.key` values are short names (e.g. `eratz`), and so is
  `system.server.game.key`. The HMAC keeps the key off the wire, but a guessable key is still guessable. Set a
  long random value in both places for a public server. The exchange port is only published on 127.0.0.1 in
  compose.
- **Docker Desktop NAT:** on Windows/macOS, clients connecting through a published port appear as the gateway
  IP (e.g. 172.19.0.1). Every local player then shares the per-IP connection limit, and IP bans apply to all of
  them. Linux hosts keep the real client IP.
- **Authorized IPs:** the `AUTHORIZED` console/GM command still lets an IP open any account without a password
  (legacy support feature). Every use is logged at WARN.
