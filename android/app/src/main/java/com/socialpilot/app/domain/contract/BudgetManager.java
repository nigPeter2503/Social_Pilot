package com.socialpilot.app.domain.contract;
/** Extension contract; no provider implementation is registered yet. */
public interface BudgetManager {
    boolean reserve(String requestId, long estimatedMicros);
    void settle(String requestId, long actualMicros);
}
