# SP-033 (phần 2h) — Mapping checkpoint và khôi phục pipeline

Issue [#35](https://github.com/nigPeter2503/Social_Pilot/issues/35) · F2.B4/B5 · Owner Nguyễn Đức Đạt (TV2). Phụ thuộc SP-031 ([pipeline văn bản](agent-pipeline-text-stages.md)), SP-029 ([ledger](ai-budget-ledger-contract.md)). Người cần chốt cùng: Thiện (schema/boundary), Minh (sự kiện ngắt kênh).

> **Trạng thái: PARTIAL.** Đây là phần 2h của ticket 12h. Đã có: policy thuần, seam lưu checkpoint, `restore` không dispatch, test bằng fake provider. **Chưa có:** store Room production, nối vào `AppContainer`/UI, ca restart/process death thật, xoay màn hình. Ticket #35 và F2.B4/B5 vẫn mở; PR này không đóng #35.

## 1. Đã làm trong 2h

| Phần | File | Ghi chú |
|---|---|---|
| Trạng thái gián đoạn | `StageResult.Status.INTERRUPTED`, `ChannelBranch.Status.INTERRUPTED` | Terminal; `resumeStage()` trả bước text đầu tiên chưa xong |
| Policy thuần | `domain/agent/CheckpointPolicy` | `recover`, `check` (guard revision), `mismatch`, `withoutChannel` |
| Seam lưu | `data/ai/pipeline/CheckpointStore`, `MemoryCheckpointStore` | `NONE` cho pipeline không checkpoint; memory store là hành vi tham chiếu, **không** sống qua process death |
| Runtime | `ChannelPipeline(gateway, workers, clock, store)`, `ChannelPipeline.restore(...)` | Ghi checkpoint ở mọi thay đổi cấu trúc, không ghi từng delta stream |

`AppContainer.pipeline` vẫn dùng `CheckpointStore.NONE`: nối memory store vào production không giúp gì khi process chết, nên chỉ nối khi có store Room.

## 2. Ranh giới định danh

| Mức | Khóa | Nguồn |
|---|---|---|
| Session | `sessionId` (UUID do pipeline tạo; giữ nguyên khi restore) | `ChannelPipeline.Session.sessionId` = `AiRequest.sessionId` |
| Kênh | `channelId` + `connectionRevision` | `ChannelTarget`; output luôn gắn với revision kết nối lúc chạy |
| Bước | `stepId`: `outline`, `draft`, `rule-check` (tuần 4: `image-prompt`, `image`, `package`) | `PipelineStage.stepId` = `AiRequest.stepId` |
| Lượt | `attempt` (1, 2, …) + `requestId` (mới cho mỗi lượt, kể cả retry) | `StageResult`; `requestId` là khóa nối sang ledger SP-029 |

Một lượt có phí luôn được nhận diện bằng `requestId`. Checkpoint không bao giờ tự tạo `requestId`; gateway tạo khi `submit`.

## 3. Mapping khi process chết

`CheckpointPolicy.recover(stored, now)` áp lên checkpoint cuối cùng đã được chấp nhận:

| Trong checkpoint | Sau khi khôi phục | Có gọi lại? |
|---|---|---|
| Stage `RUNNING` (đã/chưa có `requestId`) | `INTERRUPTED`; giữ `attempt`, `requestId`, text đã checkpoint; `finishedAtMillis = now`; usage/settlement để trống | Không. Chỉ khi người dùng `retry` |
| Branch `RUNNING` | `INTERRUPTED`, `failedStage` = stage đang chạy, hoặc bước text đầu tiên chưa xong nếu chưa bước nào chạy | Không |
| Stage `COMPLETED`, `FAILED`, `CANCELLED`, `REJECTED`, `NOT_STARTED`, `NOT_IMPLEMENTED` | Giữ nguyên | — |
| Branch `TEXT_READY`, `FAILED`, `CANCELLED` | Giữ nguyên (cùng object) | — |
| `revision` | `stored.revision + 1` | — |

`ChannelPipeline.restore(input, checkpoint, observer)`:

1. Từ chối (ném `IllegalArgumentException`) nếu `CheckpointPolicy.mismatch` khác null: `BRIEF_CHANGED`, `TARGETS_CHANGED`, `CONNECTION_CHANGED`. Không gọi gì.
2. Dựng session cùng `sessionId`, publish snapshot đã khôi phục, **không lên lịch bước nào**, không reserve ngân sách.
3. `Session.retry(channelId)` là hành động của người dùng: chạy lại từ `resumeStage()`, `attempt + 1`, `requestId` mới, reserve mới qua gateway. Các bước đã `COMPLETED` (ví dụ outline) không gửi lại; prompt draft dùng outline trong checkpoint.
4. `cancelBranch` trên branch `INTERRUPTED` trả `false` (không có gì đang chạy).

Text partial chỉ là phần đã có ở lần checkpoint cấu trúc gần nhất: delta stream không được ghi từng cái, nên phần stream sau đó mất khi process chết. Text partial không bao giờ được coi là output hoàn tất.

## 4. Chi phí của lượt bị gián đoạn

Checkpoint không quyết toán chi phí. Ledger SP-029 tự khôi phục lúc khởi động (`BudgetLedger.recoverInterrupted`): reservation `RESERVED` của process cũ thành `UNCERTAIN`, vẫn giữ trong tổng đã dùng/giữ cho đến khi đối soát. Stage `INTERRUPTED` giữ `requestId` để UI tra đúng dòng ledger; checkpoint không ghi số tiền, không coi chi phí là 0.

## 5. Kiểm revision trước khi ghi

- Mọi store phải gọi `CheckpointPolicy.check(stored, incoming)` trước khi ghi: chỉ nhận khi cùng `sessionId` và `revision` lớn hơn hẳn. Bằng hoặc nhỏ hơn: `STALE`, bỏ. Khác session: `OTHER_SESSION`, bỏ.
- Trong runtime, callback trễ của lượt cũ đã bị chặn bằng `generation` (SP-031) trước khi tạo snapshot; guard của store là lớp thứ hai khi ghi xuống đĩa không theo thứ tự.
- `save` được gọi trong lock của session để giữ thứ tự revision. Store Room phải đẩy vào một writer tuần tự (một thread), không chặn lock bằng I/O đĩa.

## 6. Ngắt kênh

Theo [Room contract](room-schema-contract.md) dòng AI session/checkpoint: checkpoint, partial text và log riêng của kênh được dọn khi ngắt. `CheckpointStore.forgetChannel(channelId)` bỏ branch của kênh đó khỏi mọi session (revision + 1); session không còn branch nào bị xóa. Ledger dùng `BudgetLedger.forgetChannel` riêng; quyết định giữ tổng chi phí sau ngắt vẫn chờ Minh (mục 7 Room contract). Chưa nối sự kiện ngắt kênh của SP-022 vào hai hàm này.

## 7. Đề xuất schema Room — CHƯA ÁP DỤNG, cần chốt với Thiện

Chưa chọn DB (DB chính hay `ai_ledger.db`), chưa chọn số version, chưa viết migration. Đề xuất để walk-through:

| Bảng | Cột chính | Khóa |
|---|---|---|
| `ai_pipeline_session` | `session_id`, `brief_id`, `revision`, `updated_at` | PK `session_id` |
| `ai_pipeline_branch` | `session_id`, `channel_id`, `platform`, `connection_revision`, `status`, `failed_step`, `rule_verdict`, `rule_issues` | PK (`session_id`, `channel_id`), FK session ON DELETE CASCADE |
| `ai_pipeline_stage` | `session_id`, `channel_id`, `step_id`, `status`, `attempt`, `request_id`, `text`, `rejection`, `block`, `error_code`, `settlement`, `estimate_micros`, `budget_stopped`, `started_at`, `finished_at` | PK (`session_id`, `channel_id`, `step_id`), FK branch ON DELETE CASCADE |

Điểm cần chốt: ghi cả snapshot trong một transaction và guard `revision` bằng `UPDATE … WHERE revision < :new`; có lưu `AiUsage` chi tiết hay chỉ dẫn `request_id` sang ledger; giữ `RuleCheck` dạng cột hay JSON; thời gian giữ session đã `TEXT_READY` sau khi Thiện áp dụng thành version (SP-036); không lưu key, token, prompt đầy đủ hay response gốc.

## 8. Danh sách test recovery

| Ca | Trạng thái | Bằng chứng |
|---|---|---|
| Stage RUNNING → INTERRUPTED, giữ requestId/text/attempt, không có settlement | PASS (unit) | `CheckpointPolicyTest.runningStageBecomesInterruptedAndKeepsItsRequestAndText` |
| Branch đã xong không đổi | PASS (unit) | `CheckpointPolicyTest.finishedBranchesAreUntouched` |
| Branch đã lên lịch nhưng chưa chạy → INTERRUPTED ở OUTLINE | PASS (unit) | `CheckpointPolicyTest.branchScheduledButNotStartedIsInterruptedAtItsFirstTextStage` |
| Guard revision: STALE / OTHER_SESSION | PASS (unit) | `CheckpointPolicyTest.writeGuardAcceptsOnlyNewerRevisionsOfTheSameSession`, `ChannelPipelineTest.storeDropsLateWritesAndForgetsDisconnectedChannels` |
| Mismatch brief/kênh/connection revision | PASS (unit) | `CheckpointPolicyTest.mismatchDetectsBriefTargetAndConnectionChanges`, `ChannelPipelineTest.restoreRefusesACheckpointBoundToAnOldConnection` |
| Ngắt kênh dọn branch, session rỗng bị xóa | PASS (unit) | `CheckpointPolicyTest.disconnectDropsOnlyThatChannelAndEmptiesTheSessionLast`, `ChannelPipelineTest.storeDropsLateWritesAndForgetsDisconnectedChannels` |
| Checkpoint ở thay đổi cấu trúc, không ở delta; revision tăng dần | PASS (unit) | `ChannelPipelineTest.structuralChangesAreCheckpointedButStreamedDeltasAreNot` |
| Restore không gửi, không reserve; retry dùng requestId mới, giữ outline | PASS (unit, fake provider qua gateway thật) | `ChannelPipelineTest.restoreAfterProcessDeathSendsNothingAndRetryUsesANewRequest` |
| Store Room: ghi/đọc/guard trong transaction | NOT_RUN | Chưa có store Room |
| Process death thật (`am kill` khi đang stream) rồi mở lại app | NOT_RUN | Chưa nối store/UI |
| Xoay màn hình giữa stream giữ session | NOT_RUN | Chưa có UI pipeline |
| Ledger `UNCERTAIN` khớp stage `INTERRUPTED` cùng requestId sau restart | NOT_RUN | Chưa nối hai luồng khởi động |
| Retry độc lập từng kênh sau restore với hai kênh | NOT_RUN ở production | Unit chỉ phủ một kênh sau restore; hai kênh độc lập đã phủ ở SP-031 khi không restore |
| Ngắt kênh thật (SP-022) gọi `forgetChannel` | NOT_RUN | SP-022 chưa merge |

Không dùng evidence prototype SP-017 thay cho các ca NOT_RUN.

## 9. Ngoài phạm vi 2h (10h còn lại của #35)

Store Room và migration; nối khởi động (đọc `all()`, restore, hiển thị trạng thái gián đoạn); ViewModel giữ session qua xoay màn hình; nối ngắt kênh; ca process death thật trên emulator; tích hợp coordinator SP-014 khi merge (snapshot giữ nguyên dạng). Không dựng lại coordinator SP-014 hay demo Room SP-017.
