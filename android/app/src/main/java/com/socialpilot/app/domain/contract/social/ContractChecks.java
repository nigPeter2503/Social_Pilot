package com.socialpilot.app.domain.contract.social;

import java.net.URI;

final class ContractChecks {
    private ContractChecks() {}

    static String text(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    static long revision(long value) {
        if (value < 1) throw new IllegalArgumentException("connectionRevision must be positive");
        return value;
    }

    static String httpsUrl(String value) {
        text(value, "postUrl");
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("postUrl must be an absolute HTTPS URL");
        }
        return value;
    }
}
