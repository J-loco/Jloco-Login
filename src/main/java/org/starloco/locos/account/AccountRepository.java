package org.starloco.locos.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.starloco.locos.database.Jdbc;

/**
 * world_accounts. Only the columns the login server owns are written: the name, password hash and secret
 * question can change on the website while an account is loaded here.
 */
public final class AccountRepository {

    private static final String COLUMNS =
            "guid, account, pass, pseudo, question, logged, subscribe, banned, bannedTime";

    private final Jdbc jdbc;

    public AccountRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    /** The account whose login name matches (the column collation is case-insensitive). */
    public Optional<Account> findByName(String name) {
        return jdbc.one("SELECT " + COLUMNS + " FROM world_accounts WHERE account = ?", AccountRepository::map, name);
    }

    public Optional<Account> findById(int id) {
        return jdbc.one("SELECT " + COLUMNS + " FROM world_accounts WHERE guid = ?", AccountRepository::map, id);
    }

    public Optional<Account> findByPseudo(String pseudo) {
        return jdbc.one("SELECT " + COLUMNS + " FROM world_accounts WHERE pseudo = ?", AccountRepository::map, pseudo);
    }

    public boolean pseudoExists(String pseudo) {
        return jdbc.exists("SELECT 1 FROM world_accounts WHERE pseudo = ?", pseudo);
    }

    public void setLogged(int accountId, int state) {
        jdbc.update("UPDATE world_accounts SET logged = ? WHERE guid = ?", state, accountId);
    }

    /** Sets the nickname only if none was chosen yet; false when the account already has one. */
    public boolean choosePseudo(int accountId, String pseudo) {
        return jdbc.update(
                        "UPDATE world_accounts SET pseudo = ? WHERE guid = ? AND (pseudo IS NULL OR pseudo = '')",
                        pseudo,
                        accountId)
                == 1;
    }

    /** Replaces the password hash (upgrade to the configured scheme after a successful login). */
    public void updatePassword(int accountId, String passwordHash) {
        jdbc.update("UPDATE world_accounts SET pass = ? WHERE guid = ?", passwordHash, accountId);
    }

    /** Ends an expired temporary ban. */
    public void liftBan(int accountId) {
        jdbc.update("UPDATE world_accounts SET banned = 0, bannedTime = 0 WHERE guid = ?", accountId);
    }

    private static Account map(ResultSet row) throws SQLException {
        return new Account(
                row.getInt("guid"),
                row.getString("account"),
                row.getString("pass"),
                row.getString("pseudo"),
                row.getString("question"),
                row.getInt("logged"),
                row.getLong("subscribe"),
                row.getInt("banned") != 0,
                row.getLong("bannedTime"));
    }
}
