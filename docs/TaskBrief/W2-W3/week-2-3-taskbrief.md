# Combined Week 2 + Week 3 Task Brief

Planning snapshot: November 14, 2026, Asia/Bangkok. Documentation task: [SP-062 / #78](https://github.com/nigPeter2503/Social_Pilot/issues/78), under [SP-008 / #10](https://github.com/nigPeter2503/Social_Pilot/issues/10). Owner: Ngô Gia Minh (`nigPeter2503`). Planned human reviewers: Nguyễn Đức Đạt (`dat532006`) and Tô Lãng Nghị (`bob35306`); no approvals are claimed.

## 1. Overview

The combined brief covers **November 9–21, 2026**. Build on the completed foundations to deliver production connections, secure AI access, persistent editing and publishing, while starting real comment and analytics collection. **Week 4, November 23–29, is the forward-looking portion.**

The plan preserves all **44 committed requirements** and excludes Optional F3.N2 and F6.N1. It uses the documented capacity of **24 hours/person/week, 96 team-hours/week**, including review, coordination, testing and documentation. The original Week 2–3 milestones remain targets, but the remaining estimates expose a delivery gap; this brief does not approve a change to those milestones. See [README: planning and responsibilities](../../../README.md), [requirements](../../requirements.md) and [Proposal §§4.2–4.4](../../SocialPilot_Proposal_Java.md).

Owner abbreviations follow [Proposal §4.1](../../SocialPilot_Proposal_Java.md): **Minh = TV1 — Ngô Gia Minh; Đạt = TV2 — Nguyễn Đức Đạt; Thiện = TV3 — Đỗ Phước Thiện; Nghị = TV4 — Tô Lãng Nghị**. Post–Week 1 owners are documented proposals, not confirmed GitHub assignments. Existing implementation issues are reused; publishing this brief does not change their assignees, estimates or board status.

## 2. Completed / assumed done

For this planning scenario, all **seven open PRs** below are completed inputs. Their existing scope is subtracted from the new workload; none is an approval blocker. This assumption does not claim that GitHub has actually merged them or that every related F requirement has passed acceptance.

- [PR #69 — SP-016](https://github.com/nigPeter2503/Social_Pilot/pull/69): brief, version and approval contracts.
- [PR #70 — SP-017](https://github.com/nigPeter2503/Social_Pilot/pull/70): sample UI, Room persistence and Worker lifecycle prototype.
- [PR #73 — SP-014](https://github.com/nigPeter2503/Social_Pilot/pull/73): independent cancellation and dual-stream coordinator.
- [PR #74 — SP-010](https://github.com/nigPeter2503/Social_Pilot/pull/74): Facebook OAuth handoff and verification spike.
- [PR #75 — SP-018](https://github.com/nigPeter2503/Social_Pilot/pull/75): Facebook comment, reply and metrics verification.
- [PR #76 — SP-019](https://github.com/nigPeter2503/Social_Pilot/pull/76): LinkedIn comment and metrics verification package.
- [PR #77 — SP-011](https://github.com/nigPeter2503/Social_Pilot/pull/77): LinkedIn OAuth and text-publishing spike.

Already merged foundations are also excluded: [SP-009 / PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66) social contract, [SP-012 / #71](https://github.com/nigPeter2503/Social_Pilot/pull/71) Room contract, [SP-013 / #72](https://github.com/nigPeter2503/Social_Pilot/pull/72) AI contract/text adapters, [SP-015 / #68](https://github.com/nigPeter2503/Social_Pilot/pull/68) baseline verification and [SP-020 / #67](https://github.com/nigPeter2503/Social_Pilot/pull/67) analytics contract. The existing topic-to-Room baseline and device-locale VI/EN resources are reused, not rebuilt.

## 3. Workload by week

Estimates are **remaining owner-hours**, including implementation, integration, testing and module documentation. Review and shared schema coordination allowances appear in §4. Module work includes its own VI/EN resources, offline persistence, loading/empty/error/permission states and applicable cancellation/migration checks, as required by [CONTRIBUTING §§5–7](../../../CONTRIBUTING.md).

SP-022 **8h**, SP-023 **6h** and SP-028 **6h** are provisional remaining-effort estimates, reduced from the original issue estimates of 16h/8h/12h to account for delivered OAuth, connection UI and text adapters. Owners must validate these reductions. Other split rows retain the original ticket totals. Repeated IDs are scheduling portions of one existing ticket; a partial allocation is not completion. If a portion needs its own independently merged implementation PR, record a child ticket with a new verified ID first, per [CONTRIBUTING §§1–2](../../../CONTRIBUTING.md).

Execute prerequisite handoffs before dependent integration, including within the same week. Fixture-based UI work is explicitly identified and does not count as real-data acceptance.

### Week 2 — November 9–15

| Task ID | Title | Description | Source file/section | Dependencies | Priority | Estimate | Owner (if known) |
|---|---|---|---|---|---|---:|---|
| [SP-021](https://github.com/nigPeter2503/Social_Pilot/issues/23) | Foundation handoff | Close the outstanding Week 1 handoff: record delivered revisions, evidence and remaining estimates (DOCS/INFRA). | [W1 brief, SP-021](../W1/week-1-taskbrief.md#sp-021) | Completed foundation outputs | High | 8h team | Minh; all participate |
| [SP-027](https://github.com/nigPeter2503/Social_Pilot/issues/29) | Secure BYOK storage | Implement key entry, validation, masking and deletion; keep separate social-token namespaces (F8.B2). | [Proposal F8](../../SocialPilot_Proposal_Java.md); [Room contract §4](../../room-schema-contract.md) | SP-012; SP-013 | High | 10h | Đạt |
| [SP-028](https://github.com/nigPeter2503/Social_Pilot/issues/30) | Production AI access | Wire existing adapters to BYOK, persistent model selection and usage accounting; validate both providers (F8.B1/B3/B4). | [AI contract §§1–5](../../ai-provider-contract.md) | SP-027; SP-013 | High | 6h | Đạt |
| [SP-022](https://github.com/nigPeter2503/Social_Pilot/issues/24) | Production token lifecycle | Extend delivered OAuth with production storage integration, expiry/revocation handling and refresh where entitled (F1.B1/B3). | [Proposal F1](../../SocialPilot_Proposal_Java.md) | SP-027; completed OAuth spikes | High | 8h | Minh |
| [SP-053](https://github.com/nigPeter2503/Social_Pilot/issues/55) | HTTPS deployment — setup | Prepare hosting, configuration and operational measurements; finish the runbook in Week 3 (INFRA/DOCS). | [Proposal §3.3](../../SocialPilot_Proposal_Java.md) | Completed OAuth spikes | High | 6h of 8h | Minh |
| [SP-024](https://github.com/nigPeter2503/Social_Pilot/issues/26) | Disconnect contract | Define cancellation/deletion hooks and settle retention policy before persistence integration (F1.B4). | [Social contract §7](../../social-contract.md); [Proposal §4.1](../../SocialPilot_Proposal_Java.md) | SP-022; SP-012; SP-020 | High | 2h of 12h | Minh |
| [SP-035](https://github.com/nigPeter2503/Social_Pilot/issues/37) | Persistent brief form | Complete objective, audience, tone, language and channel inputs; preserve existing drafts (F3.B1/F9.B2). | [Proposal F3](../../SocialPilot_Proposal_Java.md) | SP-012; completed SP-016 | High | 8h | Thiện |
| [SP-036](https://github.com/nigPeter2503/Social_Pilot/issues/38) | Editor — persistence and preview | Implement manual editing, channel limits and persistent version-bound approval (F4.B1/B2). | [Proposal F4](../../SocialPilot_Proposal_Java.md) | SP-035; completed SP-016 | High | 8h of 12h | Thiện |
| [SP-045](https://github.com/nigPeter2503/Social_Pilot/issues/47) | Start real snapshots | Implement collection/storage and begin permitted post/follower measurements with actual timestamps (F7.B1/B2/B3). | [Analytics contract §9](../../analytics-data-contract.md) | SP-022; completed SP-018/019; SP-020 | High | 10h of 12h | Nghị |
| [SP-041](https://github.com/nigPeter2503/Social_Pilot/issues/43) | Comment sync — persistence | Build production adapters, pagination, deduplication and cursor persistence from completed verification findings (F6.B2). | [Proposal F6](../../SocialPilot_Proposal_Java.md); [analytics contract §4](../../analytics-data-contract.md) | SP-022; completed SP-018/019; SP-020 | High | 6h of 12h | Nghị |
| [SP-055](https://github.com/nigPeter2503/Social_Pilot/issues/57) | Weekly coordination | Hold two short checkpoints and an integrated demo; record workload, decisions and reporting inputs (DOCS/INFRA). | [CONTRIBUTING §§8–9](../../../CONTRIBUTING.md) | SP-021 handoff | Normal | 8h team | Minh; all participate |

### Week 3 — November 16–21 delivery cutoff

| Task ID | Title | Description | Source file/section | Dependencies | Priority | Estimate | Owner (if known) |
|---|---|---|---|---|---|---:|---|
| [SP-053](https://github.com/nigPeter2503/Social_Pilot/issues/55) | HTTPS deployment — verification | Finish callback wake/restart checks and the reproducible operations runbook (INFRA/DOCS). | [Proposal §3.3](../../SocialPilot_Proposal_Java.md) | Week 2 deployment setup | High | 2h remaining | Minh |
| [SP-023](https://github.com/nigPeter2503/Social_Pilot/issues/25) | My Channels | Complete account/Page identity, connection state and operation-specific capability presentation (F1.B2). | [Proposal F1](../../SocialPilot_Proposal_Java.md) | SP-022 | High | 6h | Minh |
| [SP-036](https://github.com/nigPeter2503/Social_Pilot/issues/38) | Editor — approval verification | Finish boundary, restart and stale-callback checks; hand immutable approved versions to publishing (F4.B1/B2). | [Proposal §5.1/F4](../../SocialPilot_Proposal_Java.md) | Week 2 editor portion | High | 4h remaining | Thiện |
| [SP-025](https://github.com/nigPeter2503/Social_Pilot/issues/27) | Publishing — first production channel | Connect approved content to production publishing; persist actual post ID, URL and result (F5.B1/B5). | [Proposal F5](../../SocialPilot_Proposal_Java.md) | SP-022; completed SP-036 | High | 8h of 12h | Minh |
| [SP-029](https://github.com/nigPeter2503/Social_Pilot/issues/31) | Atomic AI budget | Implement shared reservation, settlement and uncertain-cost handling before production orchestration (F8.N1/B4). | [AI contract §3](../../ai-provider-contract.md) | SP-028; SP-012 | High | 12h | Đạt |
| [SP-031](https://github.com/nigPeter2503/Social_Pilot/issues/33) | Agent pipeline — text stages | Implement typed outline, draft and channel-rule stages using the completed coordinator (F2.B1/B2). | [Proposal F2](../../SocialPilot_Proposal_Java.md) | SP-029; SP-028; SP-035; completed SP-014 | High | 6h of 12h | Đạt |
| [SP-045](https://github.com/nigPeter2503/Social_Pilot/issues/47) | Snapshot verification | Verify multiple real observation times, gaps, missing metrics and restart persistence (F7.B1/B2/B3). | [Proposal §5.1/F7](../../SocialPilot_Proposal_Java.md) | Week 2 collection started | High | 2h remaining | Nghị |
| [SP-041](https://github.com/nigPeter2503/Social_Pilot/issues/43) | Comment sync — completion | Finish incremental/background sync, error handling and disconnect guards (F6.B2). | [Analytics contract §4](../../analytics-data-contract.md) | Week 2 adapter/persistence portion | High | 6h remaining | Nghị |
| [SP-046](https://github.com/nigPeter2503/Social_Pilot/issues/48) | Analytics repository | Implement compatible-metric calculations, weekly deltas, top posts and grouped queries (F7.B1/B2/B3/N1). | [Analytics contract §§5–7](../../analytics-data-contract.md) | Completed SP-045; SP-020 | High | 12h | Nghị |
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

| Week | Delivery work | Handoff / meetings / demo | Review allowance | Shared schema coordination | Total | Expected outcome |
|---|---:|---:|---:|---:|---:|---|
| **2** | 64h | 16h | 14h | 2h | **96h** | Secure inputs, persistent editing and real-data collection started. |
| **3** | 72h | 8h | 12h | 2h | **94h** | First production publishing path, budget protection, analytics repository and dashboard framework. |
| **4 — planned** | 74h | 8h | 12h | 2h | **96h** | Multi-channel publishing/queue, image outputs, real dashboard, inbox and reviewed replies; checkpoint integration remains partial. |

**Total: 286h against 288h nominal capacity.** SP-021 adds 2h/person in Week 2; SP-055 adds 2h/person each week. Shared schema coordination adds 2h/week to Đạt, while module owners implement/test their own migrations. The proposed review-hour distribution below is an allowance to validate when routing actual PRs, not a record of reviews performed or approvals obtained.

| Week | Minh: delivery + coordination + review | Đạt: delivery + coordination + schema + review | Thiện: delivery + coordination + review | Nghị: delivery + coordination + review |
|---|---:|---:|---:|---:|
| 2 | 16 + 4 + 4 = **24h** | 16 + 4 + 2 + 2 = **24h** | 16 + 4 + 4 = **24h** | 16 + 4 + 4 = **24h** |
| 3 | 16 + 2 + 4 = **22h** | 18 + 2 + 2 + 2 = **24h** | 18 + 2 + 4 = **24h** | 20 + 2 + 2 = **24h** |
| 4 | 20 + 2 + 2 = **24h** | 18 + 2 + 2 + 2 = **24h** | 16 + 2 + 6 = **24h** | 20 + 2 + 2 = **24h** |

The [Proposal §§4.2–4.3](../../SocialPilot_Proposal_Java.md) targets a one-channel flow in Week 2 and complete multi-channel creation–approval–publishing in Week 3. **This capacity-constrained forecast does not support those dates.** It exposes the shortfall rather than approving a milestone change. Weeks 2 and 4 have no reserve; Week 3 has only 2h. SP-062 authoring/publication effort is additional and must be recorded separately, not assumed to fit inside these delivery allocations.

## 5. Dependencies & risks

- **Critical sequence:** secure BYOK → production AI access → atomic budget → pipeline/image integration. Publishing requires persistent approval and authenticated connections; queue completion follows the production publishing handoff. Schedule these dependencies in order even within a week. SP-032 uses the agreed image contract and can proceed without waiting for all of SP-031; final SP-031 packaging consumes SP-032 output.
- **Analytics sequence:** authenticated connections → real snapshots → calculation repository → production dashboard. Start collection in Week 2; missing history cannot be reconstructed with fixtures. Use actual observation times, compatible definitions and explicit unavailable values, not synthetic zeros. See [analytics contract §§5–9](../../analytics-data-contract.md).
- **Retention:** Minh, Đạt and Nghị must settle disconnect/tombstone policy before queue retention is implemented. Proposed 30-day retention is not approved. Channel invalidation, module deletion hooks and final cross-module verification remain part of SP-024/SP-052. See [social contract §7](../../social-contract.md) and [Room contract §§4 and 7](../../room-schema-contract.md).
- **External capabilities:** use the completed verification findings for the exact account, product, grant and connection revision. Production access remains capability-specific; OAuth success alone is insufficient. Refresh depends on entitlement, as noted in [Proposal F1](../../SocialPilot_Proposal_Java.md) and [LinkedIn refresh documentation](https://learn.microsoft.com/en-us/linkedin/shared/authentication/programmatic-refresh-tokens). No open-PR approval wait is counted as a blocker.
- **Capacity and later milestone:** retained post–Week 4 work totals approximately **110 owner-hours** before review, schema coordination and the next weekly demo. This already exceeds one 96h team week. The December 6 demo requires updated estimates, added capacity or an explicitly agreed schedule decision; moving work into Week 6 is not an assumed solution.
- **Review and quality:** review allowances must be checked against actual PR count, reviewer eligibility and re-review. If insufficient, report the gap rather than exceeding member capacity or reducing review. Future implementation retains two human approvals and applicable [development gates](../../development.md), migration, cancellation, offline and real-platform verification under [CONTRIBUTING §§4 and 7](../../../CONTRIBUTING.md).

## 6. Assumptions & open questions

- **Dates:** repository Week 3 is November 16–22; this brief uses the requested **November 21 cutoff**. November 22 is not hidden contingency. Week 4 retains November 23–29.
- **Elapsed time:** these are full-window planning allocations, not retrospective evidence of hours worked. Reconcile Week 2 with actual time already spent as of November 14; 96h is not capacity newly available from that date.
- **Estimates:** confirm the reduced SP-022/SP-023/SP-028 remaining estimates with their owners. The 24h nominal baseline is used for Weeks 2–4; the historical Week 1 self-allocation/overtime arrangement is not assumed to extend to these weeks.
- **Handoff:** SP-021 remains open without recorded completion evidence, so its documented 8 team-hours are included. Remove only effort confirmed as already performed. SP-013 has merged code but its document still records missing live-provider evidence; validate the production path under SP-028 without rebuilding its delivered adapters.
- **Beyond Week 4:** remaining SP-024 (10h), SP-033 (10h), SP-030 (8h), SP-034 (8h), SP-038 (10h), SP-039 (12h), SP-040 (8h), SP-044 (12h), SP-048 (8h), SP-049 (10h), SP-050 (10h) and unallocated SP-054 (4h) remain in scope: **110 owner-hours**. SP-052 and SP-056–059 retain the documented Week 6 acceptance/handover role. The partial SP-033 allocation does not complete F2.B4/B5. SP-051 supplies the settings/framework; later modules still need their own F9 implementation and final integrated acceptance. See [Proposal §§4.2 and 5.1](../../SocialPilot_Proposal_Java.md) and existing [SP-022–SP-059 issues](https://github.com/nigPeter2503/Social_Pilot/issues?q=is%3Aissue).
- **Governance:** confirm the review roster, actual remaining hours and capacity response before treating this forecast as a committed delivery schedule. No product scope, milestone or feature assignee is changed by publishing it. Only SP-062 is newly created for this document; existing implementation issues are not duplicated.
- **Exclusions:** F3.N2 image-topic input and F6.N1 comment notifications remain Optional and unscheduled. The December 6 demo does not replace acceptance of the 44 committed requirements in Week 6.
