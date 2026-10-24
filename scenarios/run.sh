#!/bin/bash
# Live test on a SpawnLoft Paper server named econ-a with Vault, RoyalBank and RoyalEconomy installed
# and database.type: mysql pointing at a local MariaDB database "royaleconomy" (user re / retest).
cd ~/retest
SL="node $HOME/spawnloft/spawnloft.mjs"
D=~/.local/share/mcctl/instances/econ-a
db() { mariadb -ure -pretest royaleconomy -N -e "$1"; }
reset() { $SL stop econ-a >/dev/null 2>&1; sudo mariadb -e "DROP DATABASE IF EXISTS royaleconomy; CREATE DATABASE royaleconomy;"; $SL start econ-a | tail -1; }

echo "### 0. fresh database, start"
reset
db "show tables"

echo "### 1. basic scenario"
$SL bot run scen/basic.json > results/basic.txt 2>&1; echo "exit $?"
grep -E '^(FAIL|PASSED|FAILED)' results/basic.txt

echo "### 2. baltop (after the 5 second snapshot refresh)"
sleep 6
$SL cmd econ-a baltop 2>&1 | tail -4

echo "### 3. crossing pays, then an immediate stop"
$SL bot run scen/spam.json > results/spam.txt 2>&1; echo "exit $?"
grep -E '^(FAIL|PASSED|FAILED)' results/spam.txt
$SL stop econ-a | tail -1
echo "database after stop (alice 960, bob 1040, total 2000 expected):"
db "select a.uuid, a.balance from royaleconomy_accounts a order by balance"
db "select sum(balance) from royaleconomy_accounts where balance > 0 and uuid in (select uuid from royaleconomy_ledger where reason='pay')"
echo "ledger rows by reason:"; db "select reason, count(*), sum(delta) from royaleconomy_ledger group by reason"

echo "### 4. restart and read balances back"
$SL start econ-a | tail -1
$SL bot run scen/after-restart.json > results/after.txt 2>&1; echo "exit $?"
grep -E '^(FAIL|PASSED|FAILED)' results/after.txt

echo "### console problems"
grep -E 'Exception|SEVERE|ERROR\]' $D/logs/latest.log | grep -vE 'unsupported|ViaVersion|ViaBackwards' | tail -8
grep -E '\[RoyalEconomy\] (Enabling|RoyalEconomy enabled)' $D/logs/latest.log | cut -c1-90 | tail -2
echo RUN_DONE
