package com.mystipixel.royaleconomy.core;

import java.util.List;
import java.util.UUID;

/**
 * In-memory economy. Every method is safe to call from any thread and never touches the database;
 * changes are queued and written later by the flusher.
 */
public interface EconomyService {

    boolean hasAccount(UUID account);

    /** Creates the account with the configured starting balance. Returns false if it already existed. */
    boolean createAccount(UUID account);

    /** Balance in minor units, 0 if there is no account. */
    long balance(UUID account);

    /** Fails with NO_ACCOUNT when the account does not exist. Amount must be greater than zero. */
    Result deposit(UUID account, long amount, String reason);

    /** Fails with INSUFFICIENT_FUNDS rather than going below zero. Amount must be greater than zero. */
    Result withdraw(UUID account, long amount, String reason);

    /** Sets the balance (zero or more). Records the difference as the change delta. */
    Result set(UUID account, long newBalance, String reason);

    /**
     * Moves money atomically: either both sides change or neither does. Two concurrent transfers
     * in opposite directions between the same accounts must not deadlock.
     */
    Result transfer(UUID from, UUID to, long amount, String reason);

    /** Accounts sorted by balance, highest first. Built on demand from memory. */
    List<Entry> top(int limit, int offset);

    int accountCount();

    /** Removes and returns every queued change in the order it was applied. */
    List<Change> drainChanges();

    /** Puts changes back at the front of the queue after a failed write, keeping their order. */
    void requeue(List<Change> changes);
}
