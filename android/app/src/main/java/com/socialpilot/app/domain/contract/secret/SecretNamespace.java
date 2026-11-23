package com.socialpilot.app.domain.contract.secret;

/**
 * Separate encrypted stores. A namespace has its own private file and Keystore key, so deleting
 * or resetting an AI provider key can never remove a social token, and the reverse.
 */
public enum SecretNamespace {
    /** BYOK keys for LLM providers, owned by the AI platform (SP-027/SP-028). */
    AI_PROVIDER("ai"),
    /** Social access tokens, owned by the connection lifecycle (SP-022). */
    SOCIAL_TOKEN("social");

    public final String wireName;

    SecretNamespace(String wireName) {
        this.wireName = wireName;
    }

    static SecretNamespace fromWireName(String value) {
        for (SecretNamespace namespace : values()) {
            if (namespace.wireName.equals(value)) return namespace;
        }
        throw new IllegalArgumentException("Unknown secret namespace");
    }
}
