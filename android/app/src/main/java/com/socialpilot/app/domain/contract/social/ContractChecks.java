package com.socialpilot.app.domain.contract.social;

import java.net.URI;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

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

    static void timestamp(String value, String name) {
        text(value, name);
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")) {
            throw new IllegalArgumentException(name + " must use UTC yyyy-MM-dd'T'HH:mm:ss'Z'");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        if (format.parse(value, position) == null || position.getIndex() != value.length()) {
            throw new IllegalArgumentException(name + " must be a valid UTC timestamp");
        }
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
