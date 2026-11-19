package com.socialpilot.app.domain.editor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a public web URL from ACTION_SEND text without fetching it. */
public final class SharedUrlParser {
    private static final Pattern WEB_URL = Pattern.compile("https?://[^\\s<>]+",
            Pattern.CASE_INSENSITIVE);

    private SharedUrlParser() { }

    public static String firstPublicWebUrl(String sharedText) {
        if (sharedText == null || sharedText.length() > 16_384) return null;
        Matcher matcher = WEB_URL.matcher(sharedText);
        while (matcher.find()) {
            String candidate = matcher.group();
            while (!candidate.isEmpty() && ".,;:!?)]}".indexOf(
                    candidate.charAt(candidate.length() - 1)) >= 0) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            if (candidate.length() > 2048) continue;
            try {
                URI uri = new URI(candidate);
                String host = uri.getHost();
                if (host == null || uri.getUserInfo() != null || uri.getFragment() != null
                        || !host.contains(".") || host.endsWith(".")
                        || host.toLowerCase(java.util.Locale.ROOT).endsWith(".local")
                        || host.matches("[0-9.]+")) continue;
                return candidate;
            } catch (URISyntaxException ignored) {
                // Try another link in the same share text.
            }
        }
        return null;
    }
}
