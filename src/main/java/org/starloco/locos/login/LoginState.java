package org.starloco.locos.login;

import org.starloco.locos.account.Account;

/** Where a connection is in the login flow; each state carries what the next packet needs. */
public sealed interface LoginState {

    /** First packet: the client version. */
    record WaitingVersion() implements LoginState {}

    /** Account name, or "#S" for a character switch. */
    record WaitingAccount() implements LoginState {}

    /** Token from the game server after "#S". */
    record WaitingSwitchToken() implements LoginState {}

    record WaitingPassword(Account account) implements LoginState {}

    record WaitingNickname(Account account) implements LoginState {}

    /**
     * Authenticated: server list, server selection, friends' servers, GM commands.
     *
     * @param maintenanceBypass the client IP is authorized (GM console), so maintenance does not apply
     */
    record InMenu(Account account, boolean maintenanceBypass) implements LoginState {}
}
