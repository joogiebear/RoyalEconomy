package com.mystipixel.royaleconomy.core;

import java.util.UUID;

/**
 * One applied balance change, queued for the database. {@code delta} and {@code balanceAfter} are minor units.
 * {@code reason} is a short tag such as "deposit", "withdraw", "pay", "set" or the calling plugin's name.
 */
public record Change(UUID account, long delta, long balanceAfter, String reason, long atMillis) {
}
