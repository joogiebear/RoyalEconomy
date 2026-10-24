package com.mystipixel.royaleconomy.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.concurrent.locks.ReentrantLock;

public class MemoryEconomyService implements EconomyService {

    private final long startingBalance;
    private final long maxBalance;
    private final LongSupplier clock;
    private final ConcurrentHashMap<UUID, AtomicLong> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Change> pending = new ConcurrentLinkedDeque<>();

    public MemoryEconomyService(long startingBalance, long maxBalance, LongSupplier clock) {
        this.startingBalance = startingBalance;
        this.maxBalance = maxBalance == 0 ? Long.MAX_VALUE : maxBalance;
        this.clock = clock;
    }

    public void load(Map<UUID, Long> accounts) {
        balances.clear();
        for (Map.Entry<UUID, Long> entry : accounts.entrySet()) {
            balances.put(entry.getKey(), new AtomicLong(entry.getValue()));
        }
    }

    @Override
    public boolean hasAccount(UUID account) {
        return balances.containsKey(account);
    }

    @Override
    public boolean createAccount(UUID account) {
        if (balances.putIfAbsent(account, new AtomicLong(startingBalance)) != null) {
            return false;
        }
        return true;
    }

    @Override
    public long balance(UUID account) {
        AtomicLong bal = balances.get(account);
        return bal == null ? 0 : bal.get();
    }

    @Override
    public Result deposit(UUID account, long amount, String reason) {
        if (amount <= 0) {
            return new Result(Status.INVALID_AMOUNT, 0);
        }
        AtomicLong bal = balances.get(account);
        if (bal == null) {
            return new Result(Status.NO_ACCOUNT, 0);
        }
        long old;
        long next;
        do {
            old = bal.get();
            if (old > maxBalance - amount) {
                return new Result(Status.OVERFLOW, old);
            }
            next = old + amount;
        } while (!bal.compareAndSet(old, next));

        pending.addLast(new Change(account, amount, next, reason, clock.getAsLong()));
        return new Result(Status.OK, next);
    }

    @Override
    public Result withdraw(UUID account, long amount, String reason) {
        if (amount <= 0) {
            return new Result(Status.INVALID_AMOUNT, 0);
        }
        AtomicLong bal = balances.get(account);
        if (bal == null) {
            return new Result(Status.NO_ACCOUNT, 0);
        }
        long old;
        long next;
        do {
            old = bal.get();
            if (old < amount) {
                return new Result(Status.INSUFFICIENT_FUNDS, old);
            }
            next = old - amount;
        } while (!bal.compareAndSet(old, next));

        pending.addLast(new Change(account, -amount, next, reason, clock.getAsLong()));
        return new Result(Status.OK, next);
    }

    @Override
    public Result set(UUID account, long newBalance, String reason) {
        if (newBalance < 0) {
            return new Result(Status.INVALID_AMOUNT, 0);
        }
        if (newBalance > maxBalance) {
            return new Result(Status.OVERFLOW, 0);
        }
        AtomicLong bal = balances.get(account);
        if (bal == null) {
            return new Result(Status.NO_ACCOUNT, 0);
        }
        long old;
        do {
            old = bal.get();
        } while (!bal.compareAndSet(old, newBalance));

        long delta = newBalance - old;
        pending.addLast(new Change(account, delta, newBalance, reason, clock.getAsLong()));
        return new Result(Status.OK, newBalance);
    }

    @Override
    public Result transfer(UUID from, UUID to, long amount, String reason) {
        if (amount <= 0) {
            return new Result(Status.INVALID_AMOUNT, 0);
        }

        UUID first = from.compareTo(to) <= 0 ? from : to;
        UUID second = first == from ? to : from;
        ReentrantLock lockFirst = lockFor(first);
        ReentrantLock lockSecond = lockFor(second);

        lockFirst.lock();
        lockSecond.lock();
        try {
            AtomicLong fromBal = balances.get(from);
            if (fromBal == null) {
                return new Result(Status.NO_ACCOUNT, 0);
            }
            AtomicLong toBal = balances.get(to);
            if (toBal == null) {
                return new Result(Status.NO_ACCOUNT, 0);
            }

            long fromBalance = fromBal.get();
            long toBalance = toBal.get();

            if (fromBalance < amount) {
                return new Result(Status.INSUFFICIENT_FUNDS, fromBalance);
            }
            if (toBalance > maxBalance - amount) {
                return new Result(Status.OVERFLOW, toBalance);
            }

            fromBal.addAndGet(-amount);
            toBal.addAndGet(amount);

            long now = clock.getAsLong();
            pending.addLast(new Change(from, -amount, fromBalance - amount, reason, now));
            pending.addLast(new Change(to, amount, toBalance + amount, reason, now));

            return new Result(Status.OK, fromBalance - amount);
        } finally {
            lockSecond.unlock();
            lockFirst.unlock();
        }
    }

    @Override
    public List<Entry> top(int limit, int offset) {
        List<Entry> all = new ArrayList<>(balances.size());
        for (Map.Entry<UUID, AtomicLong> entry : balances.entrySet()) {
            all.add(new Entry(entry.getKey(), entry.getValue().get()));
        }
        all.sort(Comparator.comparingLong(Entry::balance).reversed());
        if (offset >= all.size()) {
            return List.of();
        }
        int end = Math.min(all.size(), offset + limit);
        return List.copyOf(all.subList(offset, end));
    }

    @Override
    public int accountCount() {
        return balances.size();
    }

    @Override
    public List<Change> drainChanges() {
        List<Change> changes = new ArrayList<>();
        Change c;
        while ((c = pending.pollFirst()) != null) {
            changes.add(c);
        }
        return changes;
    }

    @Override
    public void requeue(List<Change> changes) {
        for (int i = changes.size() - 1; i >= 0; i--) {
            pending.addFirst(changes.get(i));
        }
    }

    private ReentrantLock lockFor(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new ReentrantLock());
    }
}
