package org.starloco.locos.login;

import java.time.Clock;
import org.starloco.locos.account.AccountRepository;
import org.starloco.locos.account.BanRepository;
import org.starloco.locos.account.PlayerRepository;
import org.starloco.locos.auth.CharacterSwitchToken;
import org.starloco.locos.auth.PasswordHasher;
import org.starloco.locos.config.LoginConfig;
import org.starloco.locos.exchange.GameServerRegistry;

/** What the login steps work with. */
public record LoginServices(
        LoginConfig config,
        AccountRepository accounts,
        PlayerRepository players,
        BanRepository bans,
        GameServerRegistry servers,
        SessionRegistry sessions,
        AdminState admin,
        PasswordHasher hasher,
        CharacterSwitchToken switchTokens,
        Clock clock) {}
