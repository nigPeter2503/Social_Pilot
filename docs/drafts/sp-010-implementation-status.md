# SP-010 implementation status — 11/11/2026

This draft records source work on `feat/sp-010-facebook-oauth-spike`, stacked on the current SP-009 contract branch. It does not change the tracker or create a PR.

## Implemented in source

- Backend `POST /api/oauth/facebook/start`, Facebook HTTPS authorization URL, ten-minute in-memory state/session, fixed `android-dev` redirect mapping, and server-side callback.
- Facebook code exchange and target Page lookup using the configured Page ID; checks returned Page identity and `CREATE_CONTENT` or `MANAGE` task before creating a handoff.
- One-time, 60-second handoff keyed by a digest; exchange requires the matching session and SHA-256 verifier challenge. Handoff removal is synchronized to reject concurrent replay.
- Android browser launch, `socialpilot://oauth/callback` handling for warm/cold launch, contract exchange, and AES-GCM local storage backed by Android Keystore. The app displays the verified Page name and ID.
- No App Secret, access token, verifier, authorization code, or handoff code is hardcoded in the source. Backend configuration is supplied from environment variables; Android API base URL comes from an environment variable or Gradle property.

## Still unverified / blocked on owner setup

- `backend:compileJava` passed. `android :app:compileDebugJavaWithJavac` passed with Android SDK 36. Gradle test suites and lint have not been run. The stale status-controller assertion was updated for the new POST start endpoint; OAuth session, TTL, replay, and race tests are still missing.
- No live OAuth start/callback, state/session-negative case, TTL boundary, replay/race case, process restart or device cold-start has been exercised.
- The actual Cloudflare hostname is not set in Meta's Valid OAuth Redirect URI or local environment. The exact callback URL and Android build base URL must use the same current Quick Tunnel host.
- No live Page token lifecycle/refresh observation and no test post ID/permalink have been recorded. Do not treat source implementation as acceptance evidence.
- SP-009 PR #66 remains pending human approval per owner update. Keep this branch separate; prepare the SP-010 PR after the dependency is merged and rebase onto `main`.

## Local configuration names

`FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET`, `FACEBOOK_REDIRECT_URI`, `FACEBOOK_TARGET_PAGE_ID`, `FACEBOOK_GRAPH_API_VERSION`, and Android `SOCIALPILOT_OAUTH_API_BASE_URL`. Keep secret values in an approved local/managed secret store. The App Secret must not be sent in chat or committed.