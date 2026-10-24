package com.mystipixel.royaleconomy.core;

/** Outcome of a balance change. {@code balance} is the balance after the call, or the unchanged balance on failure. */
public record Result(Status status, long balance) {
    public boolean ok() {
        return status == Status.OK;
    }
}
