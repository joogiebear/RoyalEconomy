# Local model comparison: radiance vs Paiton

Task: implement RoyalEconomy from SPEC.md with a local model, using the same driver script, prompts, sampling settings (temperature 0.6, top-p 0.95, top-k 20) and a 24,000 token limit per call. Each run starts from the skeleton commit and gets up to 6 compile or test fix rounds. Hardware: 2x Radeon AI PRO R9700 on the vllm-test VM.

## Results

| Run | Setup | Thinking | Model calls | Time | Tokens | Truncated calls | Outcome |
|---|---|---|---|---|---|---|---|
| radiance (first pass) | radiance 0.9.3, 27B MXFP4 + DFlash, 2 cards | on | 20 | 19.2 min | 220K | 1 | Passed after 6+ fix rounds. Spec lacked the Vault API reference, so VaultEconomy was guessed. |
| radiance (rerun) | same | on | 8 | 6.8 min | 76K | 0 | Compiled and passed its own tests after 4 fix rounds |
| Paiton MXFP4 | Paiton 27B MXFP4 `--mode long`, 1 card | off (its default) | 11 | 7.5 min | 54K | 0 | Did not compile after 6 rounds |
| Paiton MXFP4 | same | on | 11 | 37.8 min | 260K | 10 | Did not compile after 6 rounds; the fix calls used the full token limit while thinking and wrote nothing |

Earlier HumanEval run (164 questions, thinking on, one card, Paiton 27B 3-bit in its default 65K mode): 94.5% against radiance's 99.4%, with answers about 2.4 times longer and 5 cut off at the 16K cap. Prefix caching showed no benefit in that mode.

## Reviewed output

The radiance first pass, once reviewed by Claude, had real defects: balance changes that bypassed the locks transfer relied on, new accounts and starting balances never persisted, queued changes lost on non-SQL errors, a fake clock, and a blocking name lookup. The model fixed all of them from a numbered defect list. The reviewed result passes 71 unit tests and live Paper tests on VM104 (MySQL and SQLite, RoyalBank through Vault, a restart, and a hard kill).

## What this does and does not show

- The Paiton runs did not produce a working plugin under this task and driver. The radiance run did.
- This compares whole serving setups, not weights alone. Radiance serves the model with a custom chat template and default sampling overrides; Paiton uses its stock template. That is the most likely reason for the much longer thinking, but it was not isolated.
- Not tried: Paiton with a larger per-call token limit, Paiton 3-bit on this task, Flash Next (needs 128 GB or more of RAM, the host has 92 GB).
- One task, one run per setup, temperature above zero. Treat the gap as strong evidence for this workload, not a general ranking.

## Takeaways

- Keep radiance as the builder on this hardware.
- A strong reviewer in the loop mattered more than the serving stack: the first pass of the winning model still had money-handling bugs.
- Give the local model the real API sources. Leaving out the Vault interface cost 2.8 times the time and tokens.
