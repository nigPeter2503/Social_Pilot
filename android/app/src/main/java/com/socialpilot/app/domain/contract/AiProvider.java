package com.socialpilot.app.domain.contract;

import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;

public interface AiProvider {
    interface Listener {
        void onEvent(AiEvent event);
    }

    /**
     * An attempt executes at most once on the calling worker. Cancel is thread-safe and
     * idempotent. It can interrupt an attached HTTP call without interrupting the worker.
     */
    interface Attempt extends Runnable, Cancelable {
    }

    /**
     * Prepares an attempt without doing I/O or calling the listener. The consumer registers the
     * handle and reserves budget before executing it. Events are serialized on the worker.
     * A cancelled attempt that never runs is finalized by its owner before dispatch.
     */
    Attempt prepare(AiRequest request, Listener listener);
}
