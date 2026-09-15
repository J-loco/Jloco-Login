package org.jloco.locos.login;

import java.time.Clock;
import org.jloco.locos.account.AccountRepository;
import org.jloco.locos.account.BanRepository;
import org.jloco.locos.account.PlayerRepository;
import org.jloco.locos.auth.CharacterSwitchToken;
import org.jloco.locos.auth.PasswordHasher;
import org.jloco.locos.config.LoginConfig;
import org.jloco.locos.exchange.GameServerRegistry;

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
