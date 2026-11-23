# Combined Week 2 + Week 3 Task Brief

Planning baseline: November 14, 2026; review reconciliation: November 15, 2026, Asia/Bangkok. Documentation task: [SP-062 / #78](https://github.com/nigPeter2503/Social_Pilot/issues/78), under [SP-008 / #10](https://github.com/nigPeter2503/Social_Pilot/issues/10). Owner: Ngô Gia Minh (`nigPeter2503`). Planned human reviewers: Nguyễn Đức Đạt (`dat532006`) and Tô Lãng Nghị (`bob35306`); no approvals are claimed.

## 1. Overview

The combined brief covers **November 9–21, 2026**. Build on the completed foundations to deliver production connections, secure AI access, persistent editing and publishing, while starting real comment and analytics collection. **Week 4, November 23–29, is the forward-looking portion.**

The plan preserves all **44 committed requirements** and excludes Optional F3.N2 and F6.N1. It uses the documented capacity of **24 hours/person/week, 96 team-hours/week**, including review, coordination, testing and documentation. The original Week 2–3 milestones remain targets, but the remaining estimates expose a delivery gap; this brief does not approve a change to those milestones. See [README: planning and responsibilities](../../../README.md), [requirements](../../requirements.md) and [Proposal §§4.2–4.4](../../SocialPilot_Proposal_Java.md).

Owner abbreviations follow [Proposal §4.1](../../SocialPilot_Proposal_Java.md): **Minh = TV1 — Ngô Gia Minh; Đạt = TV2 — Nguyễn Đức Đạt; Thiện = TV3 — Đỗ Phước Thiện; Nghị = TV4 — Tô Lãng Nghị**. Post–Week 1 owners are documented proposals, not confirmed GitHub assignments. Existing implementation issues are reused; publishing this brief does not change their assignees, estimates or board status.

The complete package is tracked together under **SP-062 / PR #79**. Detailed personal briefs:

- [Ngô Gia Minh — @nigPeter2503](tv1-minh-taskbrief.md).
- [Nguyễn Đức Đạt — @dat532006](tv2-dat-taskbrief.md).
- [Đỗ Phước Thiện — @PhuocThien06](tv3-thien-taskbrief.md).
- [Tô Lãng Nghị — @bob35306](tv4-nghi-taskbrief.md).

The personal briefs apply the latest user instruction that Week 1, including SP-021, is assumed complete. Its 8 team-hours remain historical allowance in the original baseline, not reassigned work: original scenario allocations are **88/94/96h = 278h**, plus 8 historical hours = 286h. The corrected review provision in §4 adds 5.5h: **88/96.75/98.75h = 283.5h** of forecast work; it is not a confirmed capacity commitment. Week 2 partial handoffs use **Monday, November 16, 2026**; Week 3 and forward-looking Week 4 retain their stated cutoffs.

## 2. Historical planning inputs

In the November 15 baseline, all **seven Week 1 PRs** below are treated as completed inputs. Their existing scope is subtracted from the new workload; none is an approval blocker. This assumption does not claim that GitHub has actually merged them or that every related F requirement has passed acceptance.

- [PR #69 — SP-016](https://github.com/nigPeter2503/Social_Pilot/pull/69): brief, version and approval contracts.
- [PR #70 — SP-017](https://github.com/nigPeter2503/Social_Pilot/pull/70): sample UI, Room persistence and Worker lifecycle prototype.
- [PR #73 — SP-014](https://github.com/nigPeter2503/Social_Pilot/pull/73): independent cancellation and dual-stream coordinator.
- [PR #74 — SP-010](https://github.com/nigPeter2503/Social_Pilot/pull/74): Facebook OAuth handoff and verification spike.
- [PR #75 — SP-018](https://github.com/nigPeter2503/Social_Pilot/pull/75): Facebook comment, reply and metrics verification.
- [PR #76 — SP-019](https://github.com/nigPeter2503/Social_Pilot/pull/76): LinkedIn comment and metrics verification package.
- [PR #77 — SP-011](https://github.com/nigPeter2503/Social_Pilot/pull/77): LinkedIn OAuth and text-publishing spike.

**Actual GitHub status checked November 15:** PR #69 / SP-016 is merged into `main` at `7def84d`; #70 and #73–#77 remain open. The branch incorporates that `main` revision. The completed-input assumption is retained for effort accounting; integration and live acceptance require the actual revisions/evidence below. No approval wait is charged as new feature implementation effort.

Already merged foundations are also excluded: [SP-009 / PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66) social contract, [SP-012 / #71](https://github.com/nigPeter2503/Social_Pilot/pull/71) Room contract, [SP-013 / #72](https://github.com/nigPeter2503/Social_Pilot/pull/72) AI contract/text adapters, [SP-015 / #68](https://github.com/nigPeter2503/Social_Pilot/pull/68) baseline verification and [SP-020 / #67](https://github.com/nigPeter2503/Social_Pilot/pull/67) analytics contract. The existing topic-to-Room baseline and device-locale VI/EN resources are reused, not rebuilt.

## 3. Workload by week

**November 20 scope update.** The two delivery targets are Facebook Page and Instagram Professional. PRs #76 and #77 document the former channel. Minh must re-estimate SP-022/023/025 for linked Instagram identity, token lifecycle and approved feed images. Nghị uses the November 20 Explorer observations for SP-041/043/045. The hour tables retain the November 15 estimates pending these handoffs.

Estimates are **remaining owner-hours**, including implementation, integration, testing and module documentation. Review and shared schema coordination allowances appear in §4. Module work includes its own VI/EN resources, offline persistence, loading/empty/error/permission states and applicable cancellation/migration checks, as required by [CONTRIBUTING §§5–7](../../../CONTRIBUTING.md).

SP-022 **8h**, SP-023 **6h** and SP-028 **6h** are provisional remaining-effort estimates, reduced from the original issue estimates of 16h/8h/12h to account for delivered OAuth, connection UI and text adapters. Owners must validate these reductions. Other split rows retain the original ticket totals. Repeated IDs are scheduling portions of one existing ticket; a partial allocation is not completion. If a portion needs its own independently merged implementation PR, record a child ticket with a new verified ID first, per [CONTRIBUTING §§1–2](../../../CONTRIBUTING.md).

Execute prerequisite handoffs before dependent integration, including within the same week. Fixture-based UI work is explicitly identified and does not count as real-data acceptance.

### Week 2 — November 9–15

| Task ID | Title | Description | Source file/section | Dependencies | Priority | Estimate | Owner (if known) |
|---|---|---|---|---|---|---:|---|
| [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23) | Foundation handoff — assumed done | Historical Week 1 handoff allowance retained for baseline reconciliation; no new work assigned (DOCS/INFRA). | [W1 brief, SP-021](../W1/week-1-taskbrief.md#sp-021) | Completed foundation outputs | High | 8h historical team allowance | Minh; all participate |
| [SP-027](https://github.com/nigPeter2503/Social_Pilot/issues/29) | Secure BYOK storage | Implement key entry, validation, masking and deletion; keep separate social-token namespaces (F8.B2). | [Proposal F8](../../SocialPilot_Proposal_Java.md); [Room contract §4](../../room-schema-contract.md) | SP-012; SP-013 | High | 10h | Đạt |
| [SP-028](https://github.com/nigPeter2503/Social_Pilot/issues/30) | Production AI access | Wire existing adapters to BYOK, persistent model selection and usage accounting; validate both providers (F8.B1/B3/B4). | [AI contract §§1–5](../../ai-provider-contract.md) | SP-027; SP-013 | High | 6h | Đạt |
| [SP-022](https://github.com/nigPeter2503/Social_Pilot/issues/24) | Production token lifecycle | Extend delivered OAuth with production storage integration, expiry/revocation handling and refresh where entitled (F1.B1/B3). | [Proposal F1](../../SocialPilot_Proposal_Java.md) | SP-027; Facebook callback and linked Instagram identity | High | 8h | Minh |
| [SP-053](https://github.com/nigPeter2503/Social_Pilot/issues/55) | HTTPS deployment — setup | Prepare hosting, configuration and operational measurements; finish the runbook in Week 3 (INFRA/DOCS). | [Proposal §3.3](../../SocialPilot_Proposal_Java.md) | Completed OAuth spikes | High | 6h of 8h | Minh |
| [SP-024](https://github.com/nigPeter2503/Social_Pilot/issues/26) | Disconnect contract | Define cancellation/deletion hooks and settle retention policy before persistence integration (F1.B4). | [Social contract §7](../../social-contract.md); [Proposal §4.1](../../SocialPilot_Proposal_Java.md) | SP-022; SP-012; SP-020 | High | 2h of 12h | Minh |
| [SP-035](https://github.com/nigPeter2503/Social_Pilot/issues/37) | Persistent brief form | Complete objective, audience, tone, language and channel inputs; preserve existing drafts (F3.B1/F9.B2). | [Proposal F3](../../SocialPilot_Proposal_Java.md) | SP-012; completed SP-016 | High | 8h | Thiện |
| [SP-036](https://github.com/nigPeter2503/Social_Pilot/issues/38) | Editor — persistence and preview | Implement manual editing, channel limits and persistent version-bound approval (F4.B1/B2). | [Proposal F4](../../SocialPilot_Proposal_Java.md) | SP-035; completed SP-016 | High | 8h of 12h | Thiện |
| [SP-045](https://github.com/nigPeter2503/Social_Pilot/issues/47) | Start real snapshots | Implement collection/storage and begin permitted post/follower measurements with actual timestamps (F7.B1/B2/B3). | [Analytics contract §9](../../analytics-data-contract.md) | SP-027 → SP-022; Channel/PublishedPost roots and schema slot (§5); SP-018 (Facebook) and SP-066 (Instagram) evidence; SP-020 | High | 10h of 12h | Nghị |
| [SP-041](https://github.com/nigPeter2503/Social_Pilot/issues/43) | Comment sync — persistence | Build production adapters, pagination, deduplication and cursor persistence from completed verification findings (F6.B2). | [Proposal F6](../../SocialPilot_Proposal_Java.md); [analytics contract §4](../../analytics-data-contract.md) | SP-027 → SP-022; Channel/PublishedPost roots and schema slot (§5); SP-018 (Facebook) and SP-066 (Instagram) evidence; SP-020 | High | 6h of 12h | Nghị |
| [SP-055](https://github.com/nigPeter2503/Social_Pilot/issues/57) | Weekly coordination | Hold two short checkpoints and an integrated demo; record workload, decisions and reporting inputs (DOCS/INFRA). | [CONTRIBUTING §§8–9](../../../CONTRIBUTING.md) | SP-021 handoff | Normal | 8h team | Minh; all participate |

### Week 3 — November 16–21 delivery cutoff

| Task ID | Title | Description | Source file/section | Dependencies | Priority | Estimate | Owner (if known) |
|---|---|---|---|---|---|---:|---|
| [SP-053](https://github.com/nigPeter2503/Social_Pilot/issues/55) | HTTPS deployment — verification | Finish callback wake/restart checks and the reproducible operations runbook (INFRA/DOCS). | [Proposal §3.3](../../SocialPilot_Proposal_Java.md) | Week 2 deployment setup | High | 2h remaining | Minh |
| [SP-023](https://github.com/nigPeter2503/Social_Pilot/issues/25) | My Channels | Complete account/Page identity, connection state and operation-specific capability presentation (F1.B2). | [Proposal F1](../../SocialPilot_Proposal_Java.md) | SP-022 | High | 6h | Minh |
| [SP-036](https://github.com/nigPeter2503/Social_Pilot/issues/38) | Editor — approval verification | Finish boundary, restart and stale-callback checks; hand immutable approved versions to publishing (F4.B1/B2). | [Proposal §5.1/F4](../../SocialPilot_Proposal_Java.md) | Week 2 editor portion | High | 4h remaining | Thiện |
| [SP-025](https://github.com/nigPeter2503/Social_Pilot/issues/27) | Publishing — first production channel | Publish a manually edited approved version on the first production channel; persist actual post ID, URL and result (F5.B1/B5). | [Proposal F5](../../SocialPilot_Proposal_Java.md) | SP-022; completed SP-036 | High | 8h of 12h | Minh |
| [SP-029](https://github.com/nigPeter2503/Social_Pilot/issues/31) | Atomic AI budget | Implement shared reservation, settlement and uncertain-cost handling before production orchestration (F8.N1/B4). | [AI contract §3](../../ai-provider-contract.md) | SP-028; SP-012 | High | 12h | Đạt |
| [SP-031](https://github.com/nigPeter2503/Social_Pilot/issues/33) | Agent pipeline — text stages | Implement typed outline, draft and channel-rule stages using the completed coordinator (F2.B1/B2). | [Proposal F2](../../SocialPilot_Proposal_Java.md) | SP-029; SP-028; SP-035; completed SP-014 | High | 6h of 12h | Đạt |
| [SP-045](https://github.com/nigPeter2503/Social_Pilot/issues/47) | Snapshot verification | Verify multiple real observation times, gaps, missing metrics and restart persistence (F7.B1/B2/B3). | [Proposal §5.1/F7](../../SocialPilot_Proposal_Java.md) | Week 2 collection started | High | 2h remaining | Nghị |
| [SP-041](https://github.com/nigPeter2503/Social_Pilot/issues/43) | Comment sync — completion | Finish incremental/background sync, error handling and disconnect guards (F6.B2). | [Analytics contract §4](../../analytics-data-contract.md) | Week 2 adapter/persistence portion | High | 6h remaining | Nghị |
| [SP-046](https://github.com/nigPeter2503/Social_Pilot/issues/48) | Analytics repository | Implement compatible-metric calculations, weekly deltas, top posts and grouped queries (F7.B1/B2/B3/N1). | [Analytics contract §§5–7](../../analytics-data-contract.md) | Early SP-045 schema/DTO/labelled fixtures; SP-020; real samples for final reconciliation | High | 12h | Nghị |
| [SP-047](https://github.com/nigPeter2503/Social_Pilot/issues/49) | Dashboard framework | Build XML/ViewModel screens with labelled fixtures; production binding follows in Week 4 (F7.B1/B2/B3). | [Proposal §4.1](../../SocialPilot_Proposal_Java.md) | SP-020 contract; SP-046 required for final binding | Normal | 6h of 12h | Thiện |
| [SP-051](https://github.com/nigPeter2503/Social_Pilot/issues/53) | Language and offline framework | Add persistent in-app VI/EN selection and shared offline/freshness presentation; each owner keeps module cache responsibilities (F9.B1/B2). | [Proposal F9 and §4.1](../../SocialPilot_Proposal_Java.md) | SP-035; completed SP-016 | Normal | 8h | Thiện |
| [SP-055](https://github.com/nigPeter2503/Social_Pilot/issues/57) | Weekly coordination | Demo the integrated result and explicitly record the Week 3 milestone gap (DOCS/INFRA). | [CONTRIBUTING §§8–9](../../../CONTRIBUTING.md) | Weekly deliverables | Normal | 8h team | Minh; all participate |

### Week 4 — November 23–29, planned

| Task ID | Title | Description | Source file/section | Dependencies | Priority | Estimate | Owner (if known) |
|---|---|---|---|---|---|---:|---|
| [SP-032](https://github.com/nigPeter2503/Social_Pilot/issues/34) | Channel images and templates | Implement channel-specific image generation and template fallback; hand outputs to packaging and publishing (F2.B3/F8.B3/B4). | [Proposal F2](../../SocialPilot_Proposal_Java.md) | SP-028; SP-029; completed SP-016 | High | 10h | Đạt |
| [SP-031](https://github.com/nigPeter2503/Social_Pilot/issues/33) | Agent pipeline — packaging | Complete remaining stages and package independent channel results with image outputs (F2.B1/B2). | [Proposal F2](../../SocialPilot_Proposal_Java.md) | Week 3 text stages; SP-032 for final output | High | 6h remaining | Đạt |
| [SP-033](https://github.com/nigPeter2503/Social_Pilot/issues/35) | Production checkpoint integration — start | Begin wiring existing stream/cancellation behavior to production persistence and UI recovery (F2.B4/B5). | [Proposal §3.8](../../SocialPilot_Proposal_Java.md) | Completed SP-031; completed SP-017 | High | 2h of 12h | Đạt |
| [SP-025](https://github.com/nigPeter2503/Social_Pilot/issues/27) | Publishing — second channel and images | Complete channel-independent outcomes and image payload integration (F5.B1/B5). | [Proposal F5](../../SocialPilot_Proposal_Java.md) | Week 3 publishing portion; SP-032 | High | 4h remaining | Minh |
| [SP-026](https://github.com/nigPeter2503/Social_Pilot/issues/28) | Durable publishing queue | Implement transactional deduplication, offline recovery, bounded retry and reconciliation before resending uncertain requests (F5.B2/B3/B4). | [Proposal F5](../../SocialPilot_Proposal_Java.md); [social contract §§4–7](../../social-contract.md) | Completed SP-025; SP-012; approved retention policy | High | 16h | Minh |
| [SP-047](https://github.com/nigPeter2503/Social_Pilot/issues/49) | Dashboard — real data | Bind repository outputs and real history; verify timestamps, chart gaps and unavailable values (F7.B1/B2/B3). | [Proposal §5.1/F7](../../SocialPilot_Proposal_Java.md) | SP-046; Week 3 framework | High | 6h remaining | Thiện |
| [SP-037](https://github.com/nigPeter2503/Social_Pilot/issues/39) | URL and Sharesheet input | Import accessible public articles with attribution and manual fallback; ACTION_SEND never publishes automatically (F3.N1/N3). | [Proposal F3](../../SocialPilot_Proposal_Java.md) | SP-035 | Normal | 10h | Thiện |
| [SP-042](https://github.com/nigPeter2503/Social_Pilot/issues/44) | Unified inbox | Display cached comments with channel/post/status filters and freshness/error states (F6.B1/F9.B2). | [Proposal F6](../../SocialPilot_Proposal_Java.md) | SP-041 | High | 8h | Nghị |
| [SP-043](https://github.com/nigPeter2503/Social_Pilot/issues/45) | Classification and reviewed replies | Evaluate classification, generate editable suggestions and verify correct-thread replies without duplicate sends (F6.B3/B4). | [Proposal §5.1/F6](../../SocialPilot_Proposal_Java.md) | SP-041; SP-042; SP-029 | High | 12h | Nghị |
| [SP-055](https://github.com/nigPeter2503/Social_Pilot/issues/57) | Weekly coordination | Demo publishing, inbox and analytics; reconcile remaining scope against the December 6 demo (DOCS/INFRA). | [CONTRIBUTING §§8–9](../../../CONTRIBUTING.md) | Weekly deliverables | Normal | 8h team | Minh; all participate |

## 4. Weekly summary

### Original baseline and scenario reconciliation

| Week | Delivery work | Handoff / meetings / demo | Review allowance | Shared schema coordination | Total | Expected outcome |
|---|---:|---:|---:|---:|---:|---|
| **2** | 64h | 16h | 14h | 2h | **96h** | Secure inputs, persistent editing and real-data collection started. |
| **3** | 72h | 8h | 12h | 2h | **94h** | First production publishing path, budget protection, analytics repository and dashboard framework. |
| **4 — planned** | 74h | 8h | 12h | 2h | **96h** | Multi-channel publishing/queue, image outputs, real dashboard, inbox and reviewed replies; checkpoint integration remains partial. |

**Original baseline: 286h against 288h nominal capacity.** This includes 2 historical SP-021 hours/person in Week 2; SP-021 is assumed done and is not reassigned. Original scenario work is 278h (88/94/96h), before the review correction below. SP-055 adds 2h/person each week. Shared schema coordination adds 2h/week to Đạt, while module owners implement/test their own migrations. The proposed review-hour distribution below retains the original baseline for comparison; it is an allowance to validate when routing actual PRs, not a record of reviews performed or approvals obtained. Each member's remaining Week 2 allocation is 22h after excluding the historical SP-021 2h.

| Week | Minh: delivery + coordination + review | Đạt: delivery + coordination + schema + review | Thiện: delivery + coordination + review | Nghị: delivery + coordination + review |
|---|---:|---:|---:|---:|
| 2 | 16 + 4 + 4 = **24h** | 16 + 4 + 2 + 2 = **24h** | 16 + 4 + 4 = **24h** | 16 + 4 + 4 = **24h** |
| 3 | 16 + 2 + 4 = **22h** | 18 + 2 + 2 + 2 = **24h** | 18 + 2 + 4 = **24h** | 20 + 2 + 2 = **24h** |
| 4 | 20 + 2 + 2 = **24h** | 18 + 2 + 2 + 2 = **24h** | 16 + 2 + 6 = **24h** | 20 + 2 + 2 = **24h** |

The [Proposal §§4.2–4.3](../../SocialPilot_Proposal_Java.md) targets a one-channel flow in Week 2 and complete multi-channel creation–approval–publishing in Week 3. **This capacity-constrained forecast does not support those dates.** It exposes the shortfall rather than approving a milestone change. The original baseline used all 96h in Weeks 2 and 4 and left 2h in Week 3. Excluding historical SP-021 makes the Week 2 scenario 88h; the nominal 8h difference is not verified availability before November 16. The review-corrected forecast below also exceeds individual capacity in Weeks 3 and 4. SP-062 authoring/publication effort is additional and must be recorded separately, not assumed to fit inside these delivery allocations.

### Review-corrected forecast

Retain the original delivery estimates and reviewer pairs except **SP-045: Đạt + Thiện**, because Nghị's PR includes Room migration and Thiện consumes the dashboard data. These are proposed routing instructions for future PRs, not changes to GitHub assignments. Any other schema-changing PR must also have Đạt plus a consumer when Đạt is not the author; if that changes a pair, recalculate the load before dispatching it.

Count one final implementation PR per ticket at its completion week. Partial handoff reviews, child PRs, re-review and SP-062 review are additional, not hidden in the count. Use the existing issue allowance of **0.75h/reviewer/PR**, retaining higher original allowances where already budgeted.

| Week | Final PRs counted | Minh: minimum / provision | Đạt: minimum / provision | Thiện: minimum / provision | Nghị: minimum / provision |
|---|---|---:|---:|---:|---:|
| 2 | SP-027, SP-028, SP-022, SP-035 | 1.5 / 4h | 1.5 / 2h | 0.75 / 4h | 2.25 / 4h |
| 3 | SP-053, SP-023, SP-036, SP-029, SP-045, SP-041, SP-046, SP-051 | 2.25 / 4h | 4.5 / **4.5h** | 3 / 4h | 2.25 / **2.25h** |
| 4 | SP-032, SP-031, SP-025, SP-026, SP-047, SP-037, SP-042, SP-043 | 1.5 / 2h | 3 / **3h** | 3.75 / 6h | 3.75 / **3.75h** |

| Week | Delivery | SP-055 | Schema coordination | Corrected review provision | Forecast total | Historical SP-021, excluded |
|---|---:|---:|---:|---:|---:|---:|
| 2 | 64h | 8h | 2h | 14h | **88h** | 8h |
| 3 | 72h | 8h | 2h | 14.75h | **96.75h** | 0h |
| 4 | 74h | 8h | 2h | 14.75h | **98.75h** | 0h |

| Week | Minh total | Đạt total | Thiện total | Nghị total | Individual excess over 24h |
|---|---:|---:|---:|---:|---|
| 2 | 22h | 22h | 22h | 22h | None in the full-window scenario; actual remaining time unverified |
| 3 | 22h | **26.5h** | 24h | **24.25h** | Đạt 2.5h; Nghị 0.25h |
| 4 | 24h | **25h** | 24h | **25.75h** | Đạt 1h; Nghị 1.75h |

Corrected forecast **283.5h**, plus historical 8h = **291.5h**, versus the original 286h baseline. Spare hours belonging to Minh in Week 3 do not automatically cover Đạt's schema reviews. By the November 16 checkpoint, Minh must record actual availability and route the excess before confirming dates: protect early SP-045/SP-041/SP-036 schema reviews; SP-051's November 21 final review is at risk under Đạt's load, and Week 4 SP-037/SP-047 final reviews are at risk under Nghị's load. A reviewer substitution is allowed only after checking schema involvement, two eligible humans and the replacement's capacity; otherwise record a revised review/completion forecast on those tasks. The target dates remain visible until that decision; none of these overloaded dates is represented as guaranteed. Extra implementation or handoff effort must be re-estimated within the affected ticket, not treated as free capacity.

## 5. Dependencies & risks

- **Critical sequence:** secure BYOK → production AI access → atomic budget → pipeline/image integration. Publishing requires persistent approval and authenticated connections; queue completion follows the production publishing handoff. Schedule these dependencies in order even within a week. SP-032 uses the agreed image contract and can proceed without waiting for all of SP-031; final SP-031 packaging consumes SP-032 output.
- **Analytics sequence:** authenticated connections and scoped roots → start real collection; early schema/DTO/fixtures → develop calculation repository in parallel → reconcile real samples → production dashboard. Start collection in Week 2; missing history cannot be reconstructed with fixtures. Use actual observation times, compatible definitions and explicit unavailable values, not synthetic zeros. See [analytics contract §§5–9](../../analytics-data-contract.md).
- **Retention:** Minh, Đạt and Nghị must settle disconnect/tombstone policy before queue retention is implemented. Proposed 30-day retention is not approved. Channel invalidation, module deletion hooks and final cross-module verification remain part of SP-024/SP-052. See [social contract §7](../../social-contract.md) and [Room contract §§4 and 7](../../room-schema-contract.md).
- **External capabilities.** Verify the linked Instagram Professional account, token expiry, revocation and reconnect in SocialPilot. Track publishing, comment and Insights access separately for each channel.
- **Capacity and later milestone:** retained post–Week 4 work totals approximately **110 owner-hours** before review, schema coordination and the next weekly demo. This already exceeds one 96h team week. The December 6 demo requires updated estimates, added capacity or an explicitly agreed schedule decision; moving work into Week 6 is not an assumed solution.
- **Review and quality:** review allowances must be checked against actual PR count, reviewer eligibility and re-review. If insufficient, report the gap rather than exceeding member capacity or reducing review. Future implementation retains two human approvals and applicable [development gates](../../development.md), migration, cancellation, offline and real-platform verification under [CONTRIBUTING §§4 and 7](../../../CONTRIBUTING.md).

### Ordered handoffs and acceptance gates

The dates below are required handoff targets within the existing task estimates, not evidence that the work has happened. By November 16, each named owner records an actual revision/time and consumer confirmation, or an explicit miss with affected tasks. A shared date never permits a consumer to run before its prerequisite.

| Target / order | Accountable handoff | Consumer and acceptance boundary |
|---|---|---|
| November 16, before SP-022 integration | Đạt / SP-027: secret-reference read/write/delete and error contract | Minh / SP-022: persist authenticated Channel identity/revision; production secret wiring follows this handoff |
| November 16, before post-level persistence | Minh / SP-022 owns Channel upsert and scoped identity lookup; Nghị / SP-041 owns upsert of existing provider posts into PublishedPost, independent of PublishJob | Nghị reuses these roots for SP-045; Minh's SP-025 later reuses the same `(channelId, externalPostId)` identity. Provider IDs/URNs stay opaque; no synthetic root or cross-channel FK |
| Before each schema edit, targeting November 16 for first collection | Đạt assigns a serialized database/version slot: Channel roots → existing PublishedPost roots → Comment/cursor and Snapshot/MetricSample; module owners supply migration/tests | Do not assign version numbers until preceding schema has integrated. Thiện's brief/version migration also needs a slot; record order with its consumer, never edit shared database concurrently |
| November 16, after roots and operation evidence | Nghị / SP-041, SP-045: first real comment sync and snapshot collection where permitted | Channel follower collection may start once Channel exists; post-level data waits for PublishedPost. Parsers, fixtures and DTOs proceed while roots or grants are unavailable; record the missed real observation time |
| November 17, early development handoff | Nghị / SP-045 supplies schema/query/definition contract, typed sample DTOs and labelled fixtures to SP-046 and Thiện / SP-047 | Start the 12h repository work without waiting for SP-045 final completion; freeze/review changes with consumers |
| Target November 19; final verification by November 21 | Nghị supplies a second compatible real observation and reconciles SP-045 → SP-046; Thiện receives expected values/source IDs | A missed November 19 sample triggers a revised reconciliation forecast immediately. Missing/incompatible samples at November 21 leave live acceptance pending; SP-047 may continue fixture UI, but its Week 4 real-data acceptance is at risk |
| November 21 / SP-025 first channel | Thiện / SP-036 hands Minh an immutable, manually edited approved version | Demonstrates editor → approval → production publishing only. Đạt owns text-output contract in SP-031; Thiện owns applying it as a new unapproved version through SP-036's expected-current guard |
| Week 4 SP-031 packaging, then SP-036 → SP-025 integration | Đạt hands text/image/channel/revision output to Thiện; Thiện wires and invalidates stale approval; Minh publishes only after explicit approval | Full brief → AI → editor → approval → publish test tracks one revision and all resulting version IDs. Its implementation/test time is not yet separately validated within existing estimates; record remaining work if it does not fit. SP-033 has only 2h allocated and cannot stand in for complete recovery/integration |
| November 21 / SP-051; Week 4 / SP-026; final SP-052 | Thiện verifies language/draft/cache/offline UI now; Minh supplies durable enqueue/restart behavior in Week 4 | Queue UI may use labelled states before SP-026. Real approved enqueue while offline and recovery are Week 4 integration tests, retained for final cross-module acceptance |

### Live evidence by operation

This November 15 evidence snapshot preserves Week 1 as completed for effort accounting. It does not promote fixture/contract delivery to production acceptance. Source documents on open branches must be checked again at their integrated revision before a live run.

| Consumer / operation | Evidence available | Next owner / gate; work that can proceed now |
|---|---|---|
| SP-028: OpenAI text; Gemini text | [SP-013](../../ai-provider-contract.md) records **NOT_RUN for each provider** | Đạt supplies separate authorised key/model/quota/output-cap/request-count/spend-limit evidence, then Android wiring/usage results. Fixture/model-selection/service tests proceed; unrestricted production dispatch waits for SP-029 |
| SP-041: Facebook read comments; SP-043: Facebook reply | [SP-018 / PR #75](https://github.com/nigPeter2503/Social_Pilot/blob/13f9de33bb3f50f4585af5cb639b608a64dc9fef/docs/facebook-comments-metrics-verification.md) has real Page reads and a reviewed, reconciled reply; some run times/connection revisions remain missing | Nghị completes per-run timestamp/revision provenance; Minh supplies the matching production connection/grant. Adapter/pagination/error/duplicate-send tests can use labelled fixtures; new live writes require explicit content approval |
| SP-045: Facebook follower and post metrics | SP-018 has real follower/reaction/comment and two lifetime insight metrics; shares is PROVIDER_OMITTED, capability provenance is incomplete | Nghị with Minh binds each supported metric to exact Page/grant/revision/definition and actual observation times. Collect permitted subsets; keep missing fields null/reason, no full ER without compatible components |
| SP-041/SP-045/SP-043: Instagram comments, metrics, replies | The November 20 Explorer run verified comments, a public threaded reply, follower count and lifetime reach on the demo account. | Minh supplies the app connection and revision. Nghị verifies pagination, repeat sync and two timestamped observations. Verify additional metrics individually. |
| SP-043: AI classification and suggestions | Shared SP-028/SP-029 production gate still required; SP-013 live providers NOT_RUN | Đạt supplies gated service; Nghị owns prompt/60-sample evaluation and correct-thread reviewed reply. Fake AI can test routing/cancel; permission for reply is a separate provider gate |

## 6. Assumptions & open questions

- **Dates:** repository Week 3 is November 16–22; this brief uses the requested **November 21 cutoff**. November 22 is not hidden contingency. Week 4 retains November 23–29.
- **Elapsed time:** these are full-window planning allocations, not retrospective evidence of hours worked. Reconcile Week 2 with actual time already spent as of November 14; 96h is not capacity newly available from that date.
- **Estimates:** confirm the reduced SP-022/SP-023/SP-028 remaining estimates with their owners. The 24h nominal baseline is used for Weeks 2–4; the historical Week 1 self-allocation/overtime arrangement is not assumed to extend to these weeks.
- **Handoff:** per the latest user instruction, Week 1 and SP-021 are assumed complete. Keep the historical 8 team-hours only for baseline reconciliation; this assumption does not change the actual GitHub issue status. SP-028 still validates the new production provider wiring without rebuilding delivered Week 1 adapters.
- **Beyond Week 4:** remaining SP-024 (10h), SP-033 (10h), SP-030 (8h), SP-034 (8h), SP-038 (10h), SP-039 (12h), SP-040 (8h), SP-044 (12h), SP-048 (8h), SP-049 (10h), SP-050 (10h) and unallocated SP-054 (4h) remain in scope: **110 owner-hours**. SP-052 and SP-056–059 retain the documented Week 6 acceptance/handover role. The partial SP-033 allocation does not complete F2.B4/B5. SP-051 supplies the settings/framework; later modules still need their own F9 implementation and final integrated acceptance. See [Proposal §§4.2 and 5.1](../../SocialPilot_Proposal_Java.md) and existing [SP-022–SP-059 issues](https://github.com/nigPeter2503/Social_Pilot/issues?q=is%3Aissue).
- **Governance:** confirm the review roster, actual remaining hours and capacity response before treating this forecast as a committed delivery schedule. No product scope, milestone or feature assignee is changed by publishing it. SP-062 is the single active documentation ticket for the common and four personal briefs; existing implementation issues are not duplicated.
- **Exclusions:** F3.N2 image-topic input and F6.N1 comment notifications remain Optional and unscheduled. The December 6 demo does not replace acceptance of the 44 committed requirements in Week 6.
