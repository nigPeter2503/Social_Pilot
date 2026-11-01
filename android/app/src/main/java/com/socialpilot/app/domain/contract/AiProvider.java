package com.socialpilot.app.domain.contract;
/** Extension contract; no provider implementation is registered yet. */
public interface AiProvider {
    interface Listener {
        void onText(String accumulatedText);
        void onComplete(String text);
        void onError(String code);
    }
    Cancelable generateText(String prompt, Listener listener);
}
