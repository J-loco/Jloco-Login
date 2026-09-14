package org.starloco.locos.login;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime switches set from the console or GM commands (not persisted). */
public final class AdminState {

    private final Set<String> authorizedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> maintenanceAccounts = ConcurrentHashMap.newKeySet();

    /**
     * IPs allowed to open any account without its password (support/GM use). Every use is logged at WARN
     * level.
     */
    public Set<String> authorizedIps() {
        return authorizedIps;
    }

    /** Toggles maintenance for an account; returns true when the account is now under maintenance. */
    public boolean toggleMaintenance(String accountName) {
        String name = accountName.toLowerCase(Locale.ROOT);
        if (maintenanceAccounts.remove(name)) {
            return false;
        }
        maintenanceAccounts.add(name);
        return true;
    }

    public boolean isUnderMaintenance(String accountName) {
        return maintenanceAccounts.contains(accountName.toLowerCase(Locale.ROOT));
    }
}
