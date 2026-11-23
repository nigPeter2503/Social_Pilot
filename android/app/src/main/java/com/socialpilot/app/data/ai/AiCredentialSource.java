package com.socialpilot.app.data.ai;

/** Reads the credential for one provider from the caller's approved private source. */
public interface AiCredentialSource {
    String load();
}
