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
| Brief | `briefId` + `briefFingerprint` | SHA-256 có phiên bản, tính từ topic, goal, audience, tone, language và danh sách nguồn đã chuẩn hóa, gồm kiểu và thứ tự nguồn |
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

1. `CheckpointStore.claimRecovery` đối chiếu session và revision yêu cầu với snapshot hiện hành rồi lưu bản phục hồi trong cùng thao tác nguyên tử. Hai lượt dùng cùng revision chỉ có một lượt được nhận. Store từ chối checkpoint thiếu, cũ hoặc lệch brief, nền tảng, tập kênh hay revision kết nối. Brief giữ nguyên ID mà đổi fingerprint trả `BRIEF_CHANGED`. Fingerprint thiếu hoặc khác phiên bản cũng từ chối phục hồi.
2. Dựng session từ snapshot đã được store xác nhận, giữ `sessionId` và công bố trạng thái phục hồi. Các bước AI chờ người dùng retry. `CheckpointStore.NONE` chỉ áp mapping lên snapshot caller cung cấp vì không giữ dữ liệu.
3. `Session.retry(channelId)` là hành động của người dùng: chạy lại từ `resumeStage()`, `attempt + 1`, `requestId` mới, reserve mới qua gateway. Các bước đã `COMPLETED` (ví dụ outline) không gửi lại; prompt draft dùng outline trong checkpoint.
4. `cancelBranch` trên branch `INTERRUPTED` trả `false` (không có gì đang chạy).

Text partial chỉ là phần đã có ở lần checkpoint cấu trúc gần nhất: delta stream không được ghi từng cái, nên phần stream sau đó mất khi process chết. Text partial không bao giờ được coi là output hoàn tất.

## 4. Chi phí của lượt bị gián đoạn

Checkpoint không quyết toán chi phí. Ledger SP-029 tự khôi phục lúc khởi động (`BudgetLedger.recoverInterrupted`): reservation `RESERVED` của process cũ thành `UNCERTAIN`, vẫn giữ trong tổng đã dùng/giữ cho đến khi đối soát. Stage `INTERRUPTED` giữ `requestId` để UI tra đúng dòng ledger; checkpoint không ghi số tiền, không coi chi phí là 0.

## 5. Kiểm revision trước khi ghi

- Store chỉ nhận revision mới hơn của cùng session. Revision bằng hoặc nhỏ hơn trả `STALE`. Snapshot của session đã xóa hoặc mất toàn bộ nhánh trả `INVALIDATED`. Snapshot còn nhánh hợp lệ có thể được ghi sau khi loại nhánh đã ngắt.
- Guard `generation` (SP-031) loại callback trễ của attempt cũ trước khi tạo snapshot.
- Snapshot đầu được lưu trước khi session chạy. Các lượt `save` tiếp theo giữ thứ tự revision bằng session lock. Store Room cần xếp ghi I/O vào writer tuần tự, rồi thực thi `claimRecovery` cùng ranh giới thứ tự và transaction khi khởi động. Khi một lượt `save` ném lỗi, pipeline ghi cảnh báo và tiếp tục giao snapshot trong bộ nhớ. Recovery vẫn chỉ dựa trên dữ liệu đã lưu thành công.

## 6. Ngắt kênh

Theo [Room contract](room-schema-contract.md), checkpoint, partial text và log riêng của kênh được dọn khi ngắt. `CheckpointStore.forgetChannel(channelId, retiredConnectionRevision)` ghi mốc revision đã ngắt cùng lượt dọn. Snapshot đến sau bị lọc nhánh cùng kênh ở revision đã ngắt hoặc cũ hơn. Lượt dọn giữ revision checkpoint để nhánh còn lại lưu được cập nhật kế tiếp. Session rỗng bị xóa và không nhận ghi trễ. Kết nối mới hơn vẫn được lưu. Ledger dùng `BudgetLedger.forgetChannel` riêng. Quyết định giữ tổng chi phí sau ngắt và việc nối sự kiện từ SP-022 còn chờ Minh.

## 7. Đề xuất schema Room — CHƯA ÁP DỤNG, cần chốt với Thiện

Đề xuất lưu checkpoint trong `socialpilot.db` của editor để đối chiếu scope và áp dụng version cùng transaction. Chốt số version sau khi #82 và #83 vào `main`. Chưa có migration.

| Bảng | Cột chính | Khóa |
|---|---|---|
| `ai_pipeline_session` | `session_id`, `brief_id`, `brief_fingerprint`, `revision`, `updated_at` | PK `session_id` |
| `ai_pipeline_branch` | `session_id`, `channel_id`, `platform`, `connection_revision`, `status`, `failed_step`, `rule_check_v1` | PK (`session_id`, `channel_id`), FK session ON DELETE CASCADE |
| `ai_pipeline_stage` | `session_id`, `channel_id`, `step_id`, `status`, `attempt`, `request_id`, `text`, `rejection`, `block`, `error_code`, `budget_stopped`, `started_at`, `finished_at` | PK (`session_id`, `channel_id`, `step_id`), FK branch ON DELETE CASCADE |

- Ghi cả snapshot trong một transaction, kiểm tra brief/kênh còn hợp lệ và guard `revision` bằng `UPDATE … WHERE revision < :new`. Xóa checkpoint khi brief bị xóa hoặc dọn nhánh theo revision lúc kênh bị ngắt.
- `request_id` tra usage và quyết toán trong `ai_ledger.db`. Nếu thiếu bản ghi ledger, giữ trạng thái chi phí chưa xác định.
- `rule_check_v1` dùng định dạng có phiên bản, giữ đủ verdict, modelIssues, localIssues và unknownLinks để dựng lại cùng kết quả.
- Giữ checkpoint `TEXT_READY` đến khi việc áp dụng version được xác nhận bền hoặc có chính sách retention đã chốt. Lượt xóa phải lưu tombstone để chặn ghi muộn.
- Không lưu key, token, prompt đầy đủ hay response gốc.

## 8. Danh sách test recovery

| Ca | Trạng thái | Bằng chứng |
|---|---|---|
| Stage RUNNING → INTERRUPTED, giữ requestId/text/attempt, không có settlement | PASS (unit) | `CheckpointPolicyTest.runningStageBecomesInterruptedAndKeepsItsRequestAndText` |
| Branch đã xong không đổi | PASS (unit) | `CheckpointPolicyTest.finishedBranchesAreUntouched` |
| Branch đã lên lịch nhưng chưa chạy → INTERRUPTED ở OUTLINE | PASS (unit) | `CheckpointPolicyTest.branchScheduledButNotStartedIsInterruptedAtItsFirstTextStage` |
| Guard revision: STALE / OTHER_SESSION | PASS (unit) | `CheckpointPolicyTest.writeGuardAcceptsOnlyNewerRevisionsOfTheSameSession`, `ChannelPipelineTest.storeDropsLateWritesAndForgetsDisconnectedChannels` |
| Mismatch brief/kênh/connection revision | PASS (unit) | `CheckpointPolicyTest.mismatchDetectsBriefTargetAndConnectionChanges`, `ChannelPipelineTest.restoreRefusesACheckpointBoundToAnOldConnection` |
| Brief sửa cùng ID bị từ chối trước khi đổi checkpoint, phát event hay reserve | PASS (unit) | `ChannelPipelineTest.restoreRejectsEditedBriefWithTheSameIdBeforeChangingTheCheckpoint` |
| Đổi ngôn ngữ, loại nguồn, nội dung nguồn, thứ tự hoặc thiếu fingerprint | PASS (unit) | `CheckpointPolicyTest.briefFingerprintIncludesContentLanguageAndTheTypeAndOrderOfSources` |
| Ngắt kênh dọn branch, giữ nhánh còn lại và kết nối mới | PASS (unit) | `ChannelPipelineTest.cancellationCallbackCannotRestoreADisconnectedCheckpoint`, `ChannelPipelineTest.disconnectKeepsTheOtherBranchNextRevisionAndAllowsReconnect` |
| Từ chối checkpoint cũ và chỉ nhận một lượt restore đồng thời từ cùng revision | PASS (unit) | `ChannelPipelineTest.staleCheckpointCannotRestoreCompletedStagesForRetry`, `ChannelPipelineTest.concurrentRecoveryClaimsFromTheSameRevisionHaveOneWinner` |
| Writer từ chối ghi, pipeline vẫn hoàn tất và observer vẫn nhận trạng thái | PASS (unit) | `ChannelPipelineTest.checkpointWriterFailureDoesNotLeaveAnAsyncStageRunning` |
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
