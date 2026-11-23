package com.socialpilot.app.data.remote.social;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Sanitized provider payloads only. HTTP, Channel, grants and clocks remain MO_PHONG. */
final class Sp066Evidence {
    static final String ACCOUNT = "17841459976957663";
    static final String PAGE = "1427548503779474";
    static final String MEDIA = "18111630677338097";

    private Sp066Evidence() {}

    static String body(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "../../docs/api-verification/instagram/evidence/responses", file)), StandardCharsets.UTF_8);
    }
}
