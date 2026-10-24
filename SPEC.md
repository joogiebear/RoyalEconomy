# RoyalEconomy design

A Paper plugin that registers a Vault `Economy` provider. Balances live in memory and are written behind to MySQL or SQLite. Plugins such as RoyalBank and RoyalSkyblock find it through Vault's service lookup.

Package root: `com.mystipixel.royaleconomy`. The contracts in `core/` (`EconomyService`, `AccountStore`, `Change`, `Result`, `Status`, `Entry`) are fixed. Implement them, do not change them.

## Rules

- Money is a `long` of minor units. With `fraction-digits: 2`, 1 unit = 100. Convert at the edges only: Vault (`double`), commands (text) and config.
- Vault calls are on the main thread. They must not touch the database. They read and write `EconomyService`, which only uses memory.
- Persistence is write-behind. `EconomyService` queues one `Change` per applied change. A flusher drains the queue on the `DbExecutor` every `flush-interval-ticks` and on disable, and calls `AccountStore.apply`. If `apply` throws, `requeue` the batch and log once per failure at WARNING.
- All accounts are loaded into memory at startup with `loadAll`. There is no lazy loading.
- Use royal-common for storage: `DbExecutor` for the DB thread, `TxRunner.inTransaction` for transactions, `SchemaVersion.ensure` for migrations, `Dialect` for SQL differences. Do not write your own retry or migration code.
- Player-facing text comes from `messages.yml`, never from string literals. Console log lines may be literals. No emoji, no em-dashes, no section-banner comments, comments only where the code cannot say it.
- SQL uses prepared statements only. Table names are built from the validated `table-prefix` (`[A-Za-z0-9_]+`, otherwise the plugin disables itself with a clear log line).
- Java 21 language level. No dependencies beyond paper-api, VaultAPI, royal-common, Hikari and the JDBC drivers.

## Schema (version 1)

`<prefix>accounts`: `uuid` CHAR(36) primary key, `balance` BIGINT NOT NULL, `created_at` BIGINT NOT NULL, `updated_at` BIGINT NOT NULL.

`<prefix>ledger`: `id` auto-increment BIGINT primary key, `uuid` CHAR(36) NOT NULL, `delta` BIGINT NOT NULL, `balance_after` BIGINT NOT NULL, `reason` VARCHAR(64) NOT NULL, `at` BIGINT NOT NULL. Index on `(uuid, at)`.

`AccountStore.apply` adds `delta` to the stored balance (`balance = balance + ?`) rather than overwriting it, creates a missing account row first, and inserts the ledger row, all in one transaction.

## Classes to write

`core/`
- `Money`: `static long parse(String text, int digits)` returns minor units or throws `NumberFormatException` for negative, non-numeric, NaN, infinite or too-precise input (more fractional digits than allowed is an error, not rounded). `static long fromDouble(double, int digits)` rounds half-up, rejects NaN, infinity and values that overflow a long. `static double toDouble(long, int digits)`. `static String plain(long, int digits)` formats with grouping, for example `1,234.50`.
- `MemoryEconomyService implements EconomyService`: constructor takes the starting balance (minor units), the max balance (0 means `Long.MAX_VALUE`) and a `LongSupplier` clock. Uses a `ConcurrentHashMap<UUID, long[]>` or equivalent where each account change is atomic. `deposit` returns OVERFLOW instead of exceeding the max. `transfer` locks the two accounts in a fixed order (compare UUIDs) so opposite transfers cannot deadlock, and fails without changing anything if the source lacks funds, the target is missing, or the target would overflow. Seed with `load(Map<UUID, Long>)`.

`storage/`
- `Database`: builds a `HikariDataSource` from `config.yml` for sqlite or mysql (sqlite: one connection, file in the plugin data folder, `PRAGMA journal_mode=WAL`), owns the `TxRunner` and `Dialect`, and closes the pool.
- `JdbcAccountStore implements AccountStore`: schema creation and migration through `SchemaVersion`, plus the three methods.
- `Flusher`: schedules itself with a Bukkit repeating task, drains `EconomyService` changes, submits `AccountStore.apply` to the `DbExecutor`, requeues on failure, and has `flushNow(Duration)` that blocks for onDisable.

`vault/`
- `VaultEconomy implements net.milkbowl.vault.economy.Economy`. `isEnabled` true, `getName` "RoyalEconomy", `hasBankSupport` false, `fractionalDigits` from config, `format` uses symbol plus `Money.plain`, currency names from config. Player accounts only: every `String`-name method resolves through `Bukkit.getOfflinePlayer(name)` and delegates to the `OfflinePlayer` method; the `world` overloads ignore the world. `getBalance`, `has`, `withdrawPlayer`, `depositPlayer`, `hasAccount` and `createPlayerAccount` map to `EconomyService`. A negative amount returns `EconomyResponse.ResponseType.FAILURE`. Every bank method returns `NOT_IMPLEMENTED`. Convert `double` with `Money.fromDouble`; reason string for ledger rows is the calling plugin name when it can be found, else `vault`.

`command/`
- `BalanceCommand`, `PayCommand`, `BaltopCommand`, `EcoCommand`, each a `CommandExecutor` and `TabCompleter`. `/pay` is `transfer`, rejects paying yourself and amounts below `min-pay`, and messages both players when the target is online. `/balance <player>` needs `royaleconomy.balance.others`. `/baltop` pages `baltop-page-size` entries from a snapshot rebuilt every `baltop-refresh-seconds` on the DB executor, never computed on the main thread per command. `/eco give|take|set|reset|reload`; `reset` sets the starting balance. Unknown player names (never joined) give `player-not-found`.
- A `Messages` class loads `messages.yml`, replaces `{prefix}` and the placeholders, and translates `&` colour codes.

Root
- `RoyalEconomyPlugin extends JavaPlugin`. `onEnable`: save default config and messages, validate the table prefix, open the database, load accounts, build the service, register `VaultEconomy` with `ServicesManager` at `ServicePriority.Highest`, start the flusher, register commands, and create an account on `PlayerJoinEvent` for players who have none (through the service; the flusher persists it). `onDisable`: unregister the service, flush with a 10 second limit, close the database. If anything in `onEnable` fails, log the cause and disable the plugin.

## Tests (JUnit 5, no Bukkit server, no Mockito)

- `MoneyTest`: parsing (valid, negative, too many digits, NaN, huge), formatting, round trip with 0, 2 and 4 digits.
- `MemoryEconomyServiceTest`: deposit, withdraw, insufficient funds, invalid amount, missing account, overflow at max, set, transfer success and failure leaving both balances unchanged, drain and requeue order, and a concurrency test (many threads depositing and withdrawing, plus opposite transfers between the same pair) that checks the total is conserved and nothing deadlocks within a timeout.
- `JdbcAccountStoreTest` on a temporary SQLite file: schema creation, running it twice, `loadAll`, `apply` adds deltas and writes ledger rows, a batch with a failing row writes nothing, and `createIfAbsent` does not overwrite.
- `FlusherTest` for the drain, requeue-on-failure and flush-now paths, using a fake `AccountStore` and no Bukkit scheduler (keep the flush logic in a method that does not need one).

The MySQL path is checked on a real server through Spawnloft, not in unit tests.
