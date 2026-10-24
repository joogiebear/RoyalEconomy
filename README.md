# RoyalEconomy

A Vault economy provider for Paper. Balances are kept in memory and written to MySQL or SQLite in the background, so Vault calls never wait on the database.

Requires Vault (or a compatible fork such as VaultUnlocked). See SPEC.md for the design.

## Commands

- `/balance [player]`: show a balance
- `/pay <player> <amount>`: send money
- `/baltop [page]`: richest players
- `/eco <give|take|set|reset|reload> [player] [amount]`: admin

## Building

```sh
mvn package -B
```

Needs JDK 25 to build. The jar targets Java 21.
