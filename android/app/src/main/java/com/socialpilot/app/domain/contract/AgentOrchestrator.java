package com.socialpilot.app.domain.contract;
/** Extension contract; no provider implementation is registered yet. */
public interface AgentOrchestrator {
    Cancelable start(String briefId, AiProvider.Listener listener);
}
