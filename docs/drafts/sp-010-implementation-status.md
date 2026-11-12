# SP-010 implementation and verification status — 12/11/2026

## Delivered

- Facebook OAuth backend start/callback, state/session binding and one-time Android handoff on the SP-009 contract.
- Android browser callback and secure token persistence using Android Keystore.
- Safe Facebook Graph diagnostics and URI/configuration validation.
- A real Facebook login completed on a physical Android device; the app returned with the intended Page connected.
- A real text post was created through Meta Graph API Explorer and read back successfully. Page ID: 1427548503779474; post ID: 1427548503779474_122109132231532657; permalink: https://www.facebook.com/122109132717532657/posts/122109132231532657; created 2026-11-11T06:58:10+0000. The owner confirmed the permalink opens the expected Page and post. No credential or token is recorded.
- On 12/11 the owner retrospectively confirmed reviewing the content, preparing the POST, checking the intended Page and deliberately sending the API request. A supplied screenshot shows the matching text on Demo Page MD 24-31 and the visible publisher label Social Management MD. [Approval and screenshot evidence](../evidence/sp-010-publish-approval-2026-11-12.md) distinguishes that historical attestation from the later screenshot; no exact approval/capture time is invented and no post was repeated.
- On 12/11 the owner supplied a real Page-feed permission/admin rejection (`OAuthException`, JSON error code `200`), Page-token Debugger metadata before/after manual extension (expiry changes from a timestamp to “Never”; data-access expiry remains separate), and a GET returning the intended Page. [Permission and token evidence](../evidence/sp-010-permission-token-lifecycle-2026-11-12.md) records the results and image hashes. These are manual Meta-tool checks, not automatic refresh in SocialPilot.
- The 11/11 owner verification used 23 backend tests. Review remediation below supersedes those build/test counts; the real device and Page-post evidence above has not been rerun in this remediation.

## Review remediation — 11–12/11/2026, Asia/Bangkok

Scope: four comments on PR #74 at head `d64da4565d7d0a9f43ac331dc87d5b0c5a986876`. Changes are based on integration commit `8aef746` (SP-010 plus `origin/main` at `a1b4ac8`); the final review-fix commit contains this record and the tests below.

- Added all five missing EN resources, preserving the `%1$s` placeholders.
- Replaced per-field OAuth writes with versioned, individually authenticated `connected_v2` and `pending_v2` payloads. Each payload is encrypted before one preferences commit. A failed commit is treated as unavailable even if Android changed its in-memory preferences. Corrupt/incomplete records cannot leave a connected Page summary without its token.
- Legacy v1 OAuth fields are discarded on first read and require reconnect; they cannot be safely migrated because they may already mix Page/token generations. This affects only OAuth preferences, not Room, drafts or data belonging to other modules.
- Unified all session/state/handoff indexes, cleanup and consume under one short store lock. Provider I/O stays outside the lock; callback rechecks the current session after I/O. Injected `Clock` enforces session and handoff deadlines, including equality, inside the atomic state transition.
- Added bounded cursor traversal for `/me/accounts`: maximum 25 requests, 30-second elapsed-budget check between requests, repeated/missing-cursor detection. Requests are rebuilt on the fixed Graph host with bearer authentication; `paging.next` is never followed. Incomplete traversal/provider errors return `PROVIDER_ERROR`, not a false permission denial. This budget does not interrupt an HTTP request already in progress.

Local verification: Windows, Oracle JDK 21.0.7, Gradle Wrapper 8.13, Android compile SDK 36. All credentials in automated tests are fake; Graph requests use MockRestServiceServer and never reach Meta.

| Check | Result |
|---|---|
| Backend `.\gradlew.bat test bootJar` | PASS: 39 tests, 0 failures/errors |
| Android `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` | PASS: 27 JVM unit tests, app/test APKs built, lint 0 errors (14 warnings) |
| `git diff --check` | PASS |
| Device instrumentation via ADB AndroidJUnitRunner, 12/11 | PASS: 2 tests on physical Samsung SM-G990E, Android 16 / API 36, source `a6c8a9d` |
| `:app:connectedDebugAndroidTest` Gradle task | NOT RUN: built APKs installed with `adb install -r` and runner invoked directly, then the original installed APK restored to preserve its configuration; see device evidence |

Regression evidence:

- [Session tests](../../backend/src/test/java/com/socialpilot/oauth/flow/FacebookOAuthSessionServiceTest.java): wrong state/session/verifier, replay, expiry at the exact handoff/session boundary, expiry/cleanup while waiting for the store lock, two concurrent exchanges, callback expiry during suspended provider I/O. These assert rejection of the reviewed bugs, rather than assertions that accept the old broken behavior.
- [Pagination tests](../../backend/src/test/java/com/socialpilot/oauth/facebook/FacebookPagePaginationTest.java): target on page 2, hostile next URL, cursor encoding/repetition, missing cursor, last page without target, later-page HTTP failure and request limit.
- [OAuth storage unit tests](../../android/app/src/test/java/com/socialpilot/app/data/security/EncryptedOAuthStoreTest.java): encryption failure before writes, memory-first failed commits with failed cleanup/restart, pending-pair consistency, corruption, unknown version, incomplete credentials, legacy reconnect and record-swap authentication. Cipher and preferences are injected; these tests do not claim Android Keystore execution.
- [Device tests](../../android/app/src/androidTest/java/com/socialpilot/app/data/security/EncryptedOAuthStoreDeviceTest.java): real Keystore reconnect/pending round trips and corruption handling, using an isolated preferences file with the `sp010_test_` prefix, never the app's normal OAuth file. Executed on physical Samsung SM-G990E / API 36 on 12/11: 2 PASS. [Device evidence](../evidence/sp-010-keystore-device-check-2026-11-12.txt) records tested revision/APK hashes, runner command, results and original-APK restoration. API 23, process death and cold-start callback remain untested.

## Still open

- The previously missing real permission/admin error and Page-token metadata/manual extension evidence are now recorded in the supplement above; reviewers must confirm the final evidence scope. The permission response is safely transcribed because the local raw PNG still exposes part of a credential; it is not committed.
- Negative callback/state, replay/race and expiry boundaries now have service-level tests with a fake provider; live negative callback behavior and device lifecycle still need evidence.
- Page-token type/expiry and manual extension outcome are recorded. Automatic app refresh, real expiry/revocation and application recovery are not verified; F1.B3 is not fully accepted by the provider-tool demonstration. Owner: Minh; tracked in Issue #12.
- Restart and Android cold-start callback behavior have not been separately exercised.
- The real post was made through Graph API Explorer; SocialPilot's app-side publish queue/production publishing flow is not implemented or verified by this spike.
- Image/alt-text publishing was not tested.
- SP-009 PR #66 merged on 11/11. `origin/main` has been integrated with conflicts resolved; the final diff must be reviewed against main, including the test-only Facebook configuration retained for the Spring context.
- Issue #12 was OPEN, assigned to `nigPeter2503`, Project In Progress at the remediation's live read. PR #74 was OPEN with no human approvals. Do not mark SP-010 complete before the remaining acceptance evidence, two human approvals and merge. The original W1 handoff milestone remains overdue.

## Configuration

The backend reads FACEBOOK_APP_ID, FACEBOOK_APP_SECRET, FACEBOOK_REDIRECT_URI, FACEBOOK_TARGET_PAGE_ID, and FACEBOOK_GRAPH_API_VERSION. The Android API base URL is supplied through SOCIALPILOT_OAUTH_API_BASE_URL or the documented Gradle property. Keep secrets and tokens in an approved local/managed secret store; never put them in source, logs, screenshots, chat, or Git.
