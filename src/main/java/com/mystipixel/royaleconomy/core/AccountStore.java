package com.mystipixel.royaleconomy.core;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence for the economy. Implementations are blocking and must only be called off the main thread. */
public interface AccountStore {

    /** Every account and its balance in minor units. Called once at startup. */
    Map<UUID, Long> loadAll() throws SQLException;

    /**
     * Writes a batch in one transaction: creates missing accounts, adds each change's delta to the stored balance,
     * and appends one ledger row per change. Either all changes are written or none.
     */
    void apply(List<Change> changes) throws SQLException;

    /** Creates the account row with the given balance if it does not exist yet. */
    void createIfAbsent(UUID account, long startingBalance) throws SQLException;
}
