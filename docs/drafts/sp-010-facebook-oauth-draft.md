# SP-010 — Facebook OAuth spike: evidence and work plan

> **DRAFT — historical evidence snapshot from 10/11/2026; current verification is appended below (11/11/2026, Asia/Bangkok).** The original tables below retain the state at capture time and are superseded where the current update says so. No credentials are stored here.

## 1. Task and tracker

| Item | Current record |
|---|---|
| Issue | [SP-010 / #12](https://github.com/nigPeter2503/Social_Pilot/issues/12) |
| Parent / Project | [SP-002 / #4](https://github.com/nigPeter2503/Social_Pilot/issues/4) / [Project #5](https://github.com/users/nigPeter2503/projects/5) |
| Owner / reviewers | Ngô Gia Minh (`nigPeter2503`); Nguyễn Đức Đạt (`dat532006`) and Đỗ Phước Thiện (`PhuocThien06`) |
| Requirements | F1.B1, F1.B3, F5.B1, F5.B5; image/alt-text capability inventory relates to F4.N3 |
| Original milestone | End of workday 3 of Week 1; 8 owner hours. The milestone has passed; do not recalculate it from today. |
| Issue / Project | OPEN, assigned to `nigPeter2503`, label `question`; Project `In Progress` at last live read on 10/11/2026. |
| SP-010 branch | feat/sp-010-facebook-oauth-spike; pushed with OAuth fix and evidence updates. It retains SP-009 ancestry; prepare final PR after SP-009 merges and the branch is updated from main. |
| SP-009 dependency | PR [#66](https://github.com/nigPeter2503/Social_Pilot/pull/66) was OPEN, `REVIEW_REQUIRED`, with no reviews at last live read. Head `d09819594e5b939d88c810a83cf8165c0abcc12c`. Earlier CI success was reported for another head and does not prove checks for this head. |

Re-read tracker and PR before integration. Tracker and review state can change.

## 2. Meta and Page evidence

Evidence source: owner-provided screenshots and sanitized Graph API Explorer output on 10/11/2026.

| Item | Evidence | Status / limitation |
|---|---|---|
| Meta app | `Social Management MD`, App ID `1650819950076669` | Name and ID verified. App mode, exact use case/product and tester role were not visible. |
| API version | `v27.0` in Graph API Explorer | Applies to the Explorer request; confirm adapter version separately. |
| User token | Debugger: valid, type `User`, source `Web`, matching App ID; issued time unknown | Verified for the displayed User token, not a Page token. |
| Granted User-token scopes | `public_profile`, `pages_show_list`, `business_management`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_posts` | All shown as granted. This does not prove each Page endpoint succeeds. |
| User-token expiry | About 1 hour; data-access expiry about 3 months in the supplied debugger screenshot | Snapshot only; these are separate expirations. Refresh/extension was not tested. |
| Page-list API | `GET /me/accounts?fields=id,name,tasks,access_token` succeeded | Page-token values were sanitized and are not recorded. Confirms Page listing, not OAuth callback or publishing. |
| Intended test Page | `Demo Page MD 24-31`, ID `1427548503779474` | Intended target; confirm before publishing. Returned Page tasks include `CREATE_CONTENT` and `MANAGE`, plus `MODERATE`, `MESSAGING`, `ANALYZE`, `ADVERTISE`, `MANAGE_LEADS`. |
| Other returned Page | `Dung Lee`, ID `416045034933669` | Not the intended SP-010 test target. |
| Page Access Token | Present in the API response but sanitized; no Page-token Debugger result supplied | **NOT VERIFIED:** type, validity, scopes and expiry. Never record the token. A prior Page token was reported regenerated after exposure; invalidation of the old token is not independently verified. |
| Real post / permalink | No post ID or URL supplied | **NOT TESTED.** |
| Image/alt text, comments, insights, refresh | No endpoint result supplied | **NOT TESTED.** |

Keep the distinction: app can request a scope → user granted the scope → endpoint succeeds on the selected Page. Each needs its own evidence.

## 3. Callback proposal — pending confirmation

The owner has previously tested Cloudflare Tunnel and considers a custom scheme suitable for an educational spike. Candidate values only; not configured or implemented:

| Purpose | Candidate | State |
|---|---|---|
| Meta provider callback to backend | `https://<cloudflare-host>/api/oauth/facebook/callback` | Needs a running HTTPS tunnel, implemented endpoint and exact Meta allow-list entry. Host not recorded. |
| Backend return to Android | `socialpilot://oauth/callback` | Custom scheme proposal; confirm with Thiện and add Android intent handling. |
| Server-side redirect key | `android-dev` | Candidate `redirectId` mapping to a fixed allow-listed URI; never accept an arbitrary redirect URL. |

These are two different redirects. Intended flow: browser → Meta → backend callback over HTTPS → backend validates state and obtains one-time handoff → Android receives only the handoff code → Android exchanges it with backend. Never put provider access tokens, App Secret or verifier in a URL/log. A temporary tunnel hostname may change; update backend config and Meta's exact Valid OAuth Redirect URI if it does. Do not register a placeholder URI.

SP-009 supplies DTOs/wire examples, not session enforcement, provider adapters or replay protection. Backend currently returns `501 OAUTH_NOT_CONFIGURED`; Android has no OAuth callback intent filter. The proposed endpoint methods and the 10-minute authorization-session TTL remain pending technical review. Handoff TTL is 60 seconds in the SP-009 contract and must be enforced, not assumed from the DTO.

## 4. Acceptance evidence

| Criterion | Status | Evidence still needed |
|---|---|---|
| App/Page/scopes inventoried | **PARTIAL** | App mode/use case/tester role; replacement Page-token Debugger metadata; date/version in the final test record. |
| Page content-creation task | **REAL PASS — manual API test** | A real Page post was created and read back via Graph API Explorer; app-side publishing is still outside the verified path. |
| Browser → HTTPS backend → Android | **REAL PASS — basic callback** | Owner confirmed Facebook OAuth returned to the app on a physical Android device; negative state/replay and cold-start cases remain open. |
| State/session binding | **NOT TESTED** | Wrong/missing/replayed state and provider-error tests. |
| Handoff TTL/verifier/replay/race | **NOT TESTED** | MockMvc/session-store tests for expiry boundary, wrong session/verifier, replay and concurrent exchange. |
| Real post ID/permalink | **REAL PASS — manual API test** | Post ID, read-back message, permalink and timestamp are recorded below; owner confirmed the link opens the expected Page and post. |
| Token lifecycle/refresh | **NOT TESTED** | Page-token type/expiry, actual provider behavior, or a documented blocker. Re-login is not refresh evidence. |
| HTTPS/restart/cold start | **PARTIAL** | HTTPS callback was exercised through the tunnel on a device; restart and cold-start behavior were not separately tested. |
| Image/alt text | **NOT TESTED** | Separate approved image test and evidence of actual provider behavior. |
| Secret hygiene | **PARTIAL** | No credential in this file. Recheck source, logs, proxy output and screenshots after implementation. |

## 5. Remaining decisions and handoffs

| ID | Owner action | Current status |
|---|---|---|
| FB-D01 / FB-C01 | Confirm selected Page and actual role before publishing | Test Page was selected and a real text post succeeded; owner verified the Page/post permalink. |
| FB-C02 | Record app mode, use case/product and tester role | App name/ID known; remaining metadata unknown. |
| FB-C03 | Separate granted scopes from endpoint results | Historical Page-post success plus real permission/admin error and before/after Page-token metadata are now [recorded separately](../evidence/sp-010-permission-token-lifecycle-2026-11-12.md). This does not prove auto-refresh or every capability. |
| FB-D02 | Record actual Cloudflare HTTPS host, stability/limits, operator and cost ceiling | Host planning-theoretical-into-tmp.trycloudflare.com was used for the callback; stability limits and cost ceiling remain undocumented. |
| FB-D03 | Confirm Android callback and backend route with Thiện/Đạt | Android callback route and backend handoff returned to the app on a physical-device test; cold-start behavior remains unverified. |
| FB-D04 | Approve exact post text, audience/visibility, post count and cleanup expectation | Owner confirmed on 12/11 that content was reviewed, the Page checked and the POST deliberately sent. [Historical approval and screenshot](../evidence/sp-010-publish-approval-2026-11-12.md) recorded; exact approval time, prior audience choice and cleanup expectation were not supplied. |
| FB-C04 | Store App Secret/tokens only in an approved local/managed secret store; verify old Page token is invalid | No credential values recorded; invalidation and replacement Page-token metadata pending. |
| FB-D05 | Select device/emulator, browser and test window; coordinate handoff to Nghị | Physical Android device/browser OAuth test completed; separate cold-start and restart tests remain pending. |
| FB-C05 | Re-read Issue #12, Project #5 and PR #66 before integration | Last read: issue OPEN / Project In Progress; PR #66 OPEN / review required. |
| Handoff to Nghị | Provide Page ID, post ID/URL, API version, verified scopes/expiry and an approved private token-sharing method if needed | Page ID/tasks available; post and Page-token evidence pending. |

Never put access tokens, App Secret, authorization code, handoff code or verifier in this table, repository, issue or chat.

## 6. Implementation and verification sequence

1. Confirm PR #66 is merged and required checks pass before integrating SP-009. Keep SP-010 in its own branch/PR.
2. Confirm callback routes, session TTL/storage and Android scheme with Đạt/Thiện; use a fixed server-side redirect allow-list.
3. Implement backend session/state/handoff and Facebook adapter; add negative, expiry-boundary, replay and race tests. Label provider-fake tests as simulated.
4. Add Android handling for `socialpilot://oauth/callback`; exchange the one-time handoff with backend and handle missing session/cold start safely.
5. Run backend behind the chosen HTTPS tunnel, then register the exact provider callback in Meta. Test callback before publishing.
6. After explicit approval of Page/content/audience, submit one real post and verify Page, content, ID and permalink. Reconcile uncertain outcomes; do not blindly retry.
7. Record token lifecycle, restart, image/alt-text findings and blockers. Label each result `REAL`, `SIMULATED`, `BLOCKED` or `NOT TESTED`.

## 7. Required checks and completion

After implementation:

```powershell
# backend/
.\gradlew.bat test bootJar

# android/
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# repository root
git diff --check
```

Also test callback lifecycle on an emulator/device. Mocks do not replace real callback/Page-post evidence. Do not mark SP-010 complete until acceptance evidence is recorded, applicable checks pass, two human reviewers approve the final PR, and it is merged.

Suggested commit title:

```text
docs(docs): record SP-010 Facebook OAuth evidence [SP-010]
```
## Owner evidence and implementation update — 11/11/2026

The owner confirmed Facebook OAuth completed successfully on a real Android device, returning to the app with the intended Page connected. A real text post was also created through Graph API Explorer and read back with the expected message, permalink, and timestamp; the owner confirmed the permalink opens the intended Page and post. This proves the manual Page-post API test, not app-side queue publishing or the full token lifecycle.

- Meta app is in Development mode and the owner account is an Admin. Graph API Explorer showed Page-related permissions available for testing.
- The user reported a successful device connection after the backend change to parse Graph responses independently of the response Content-Type. No credential, authorization code or token is recorded here.
- The latest supplied Cloudflare tunnel hostname was `planning-theoretical-into-tmp.trycloudflare.com`; confirm the active run matches the configured callback before any further test.
- SP-010 is stacked on SP-009. Keep the SP-009 commits out of the final SP-010 PR by waiting for SP-009 to merge or rebuilding the SP-010 branch on the updated `main` before opening its PR.
- Real Page-post API test: Graph API Explorer POST /v27.0/1427548503779474/feed returned object ID 1427548503779474_122109132231532657 on 11/11/2026. A subsequent GET returned message "Bài đăng kiểm thử SocialPilot - 11/11/2026", permalink https://www.facebook.com/122109132717532657/posts/122109132231532657, and created time 2026-11-11T06:58:10+0000. The owner confirmed this permalink opens the expected Page and post. No token or authorization code is recorded.
- Token refresh/expiry, restart behavior, and cold-start callback remain unverified.

## Review remediation update — 12/11/2026

This update supersedes the historical implementation/review status above. SP-009 / PR #66 has merged; main was integrated into SP-010 with conflicts resolved. PR [#74](https://github.com/nigPeter2503/Social_Pilot/pull/74) exists. Four review findings have fixes and regression coverage: EN resources, atomic encrypted OAuth records, session expiry/current-session checks in the atomic consume, and bounded Page cursor traversal. Backend 39 tests and Android 27 unit tests/build/lint pass. On 12/11, both device Keystore tests passed on physical Samsung SM-G990E / Android 16 / API 36 at source `a6c8a9d`; the original installed APK was restored afterwards. API 23 and callback cold start are not covered. Legacy OAuth storage requires reconnect. Live OAuth/post/token-lifecycle evidence was not rerun. See [current status](sp-010-implementation-status.md) for commands, [device evidence](../evidence/sp-010-keystore-device-check-2026-11-12.txt), scope and remaining acceptance work; two human approvals and merge remain required.

## Owner approval and screenshot supplement — 12/11/2026

The owner confirmed reviewing the content and checking the correct Page before deliberately sending the POST in Graph API Explorer. This is a retrospective statement of the earlier action, recorded on 12/11, not a new publish authorization or a screenshot of the approval moment. The supplied image shows the matching test text on Demo Page MD 24-31 with the visible publisher label Social Management MD. [The evidence record](../evidence/sp-010-publish-approval-2026-11-12.md) preserves the image, statement, earlier post identifiers and their respective limits. No new POST was sent. Real permission-denial and Page-token lifecycle evidence remain open under Issue #12 / Minh; no external blocker is inferred solely from absence of a test.

## Permission and Page-token evidence update — 12/11/2026

This update supersedes the missing-permission/token evidence status above. The owner supplied a real POST permission/admin error (`OAuthException`, JSON `code: 200`), Page-token Debugger screenshots before/after manual extension, and a GET returning the intended Page ID/name. [The new evidence record](../evidence/sp-010-permission-token-lifecycle-2026-11-12.md) preserves the safe screenshots, exact expiry fields, error transcription and limits. The after-state is valid, Type Page, Expires “Never”, while data-access expiry remains `1802237096`; this does not establish automatic refresh or tested expiry/revocation in SocialPilot. Restart/cold-start and image/alt-text findings still require final disposition against the taskbrief. Owner: Minh; Issue #12 remains open pending evidence review and normal completion conditions.

## Android process-death callback update — 12/11/2026

The earlier unverified Android cold-start status is superseded for the specific owner-executed process-death case at source `2004a97`. The final terminal screenshot shows no app PID after `am kill`; the owner reports completing Facebook confirmation and returning to SocialPilot with the correct Page connected. [The evidence record](../evidence/sp-010-cold-start-callback-2026-11-12.md) includes the unchanged screenshot, APK hash and provenance limits. Backend restart during pending OAuth, real token expiry/revocation, and image/alt-text results remain unverified. This is not full F1.B3 acceptance or task completion.

## Backend restart and fresh-session recovery update — 12/11/2026

The earlier unverified backend-restart status is superseded by the [owner-executed restart evidence](../evidence/sp-010-backend-restart-callback-2026-11-12.md) at source `2004a97`. The owner confirmed following the instructed sequence, keeping the tunnel running while restarting only the backend. Phone screenshots show `INVALID_STATE` for the old callback and a connection-success toast with the intended Page for the new session. The images do not themselves show the restart operation or HTTP status. Both requested real-device lifecycle cases are now recorded; automatic app refresh, real token expiry/revocation, image/alt-text results and normal PR/task completion conditions remain separate.

## Photo and custom alt-text capability update — 12/11/2026

The earlier NOT TESTED/UNVERIFIED image/alt-text status is superseded by a [successful manual v27.0 photo and custom alt-text test](../evidence/sp-010-photo-alt-text-2026-11-12.md). The owner supplied photo ID `122109617541532657`, post ID `1427548503779474_122109617565532657`, an exact custom-text GET read-back and confirmation that the returned Facebook photo link opens correctly. Creation time is `2026-11-12T07:15:30+0000`. The result is preserved as safe JSON/text because the inspected local PNG still exposes a token fragment. This completes the previously missing spike capability finding without a scope waiver; it does not accept the full editor, application publishing queue or production token lifecycle. Final human evidence review, two approvals and merge remain required.
