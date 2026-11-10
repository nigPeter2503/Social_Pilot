package com.socialpilot.app.domain.contract;
/** Extension contract for the session coordinator owned by SP-014. */
public interface AgentOrchestrator {
    Cancelable start(String briefId, AiProvider.Listener listener);
}
