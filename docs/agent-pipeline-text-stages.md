# SP-031 (phần tuần 3) — Pipeline văn bản theo kênh

Issue [#33](https://github.com/nigPeter2503/Social_Pilot/issues/33) · F2.B1, F2.B2 (phần text) · Owner Nguyễn Đức Đạt (TV2). Phụ thuộc SP-028 ([gateway](ai-model-usage-contract.md)) và SP-029 ([ledger](ai-budget-ledger-contract.md)). Bên nhận: Thiện (SP-036, áp dụng output thành version mới chưa duyệt), Nghị (cùng service semantics).

## 1. Phạm vi

| Bước của mỗi nhánh (Proposal F2.B2) | Trạng thái |
|---|---|
| `OUTLINE` dàn ý | Tuần 3, đã làm |
| `DRAFT` bản nháp | Tuần 3, đã làm |
| `RULE_CHECK` tự rà quy tắc | Tuần 3, đã làm |
| `IMAGE_PROMPT` mô tả ảnh, `IMAGE` ảnh/template (SP-032), `PACKAGE` gói kết quả | Tuần 4, **chưa làm**: luôn `NOT_IMPLEMENTED`, không bao giờ hiển thị là hoàn tất |

Chưa đủ F2: `ChannelBranch.Status.TEXT_READY` chỉ nghĩa là ba bước text đã xong; `TextStageOutput.fullChainComplete` luôn `false` ở tuần 3.

## 2. Entry point

```java
PipelineInput.Mapping mapping = PipelineInput.from(brief, connectionRevisions); // hoặc new PipelineInput(...)
ChannelPipeline.Session session = container.pipeline.start(mapping.input, snapshot -> post(snapshot));
session.cancelBranch(channelId); session.cancel(); session.retry(channelId); session.snapshot();
```

- Target: 1–2 kênh, tối đa một `FACEBOOK` và một `INSTAGRAM`, khác `channelId`, mỗi kênh có `connectionRevision > 0` từ chủ kết nối. Platform khác (ví dụ `LINKEDIN` còn trong `Brief` ở `main`) trả `UNSUPPORTED_PLATFORM` và không chạy. Pipeline không tự thêm kênh.
- **Một target:** đúng một nhánh, mỗi bước một `AiGateway.submit` (một requestId, một reservation). Không gọi `DualStreamCoordinator.start(a, b)` với null hoặc request giả, không dispatch kênh thứ hai.
- **Hai target:** hai nhánh độc lập chạy cùng lúc trên executor `io`. Lỗi, bị từ chối hay hủy ở nhánh A không chạm nhánh B. Timestamp `startedAtMillis`/`finishedAtMillis` của từng bước chứng minh chồng lấp (test `twoTargetsOverlapAndOneFailureDoesNotTouchTheOther`).
- **Quyết định tạm thời:** coordinator hai nhánh SP-014 (PR #73) chưa merge. Trong lúc chờ, hai target dùng hai nhánh gateway độc lập. Khi SP-014 merge, nhánh đôi sẽ chuyển sang coordinator; hình dạng `PipelineSnapshot` giữ nguyên. Một target vẫn đi đường một nhánh.

## 3. Gate, hủy, retry

- Mọi bước đi qua `AppContainer.ai`, nên chung lựa chọn model, BYOK, reserve trước dispatch, hủy và usage của SP-028/SP-029. Bị từ chối (`NO_MODEL_SELECTED`, `MODEL_BLOCKED`, `KEY_NOT_USABLE`, `BUDGET_NOT_SET`, `BUDGET_LIMIT_REACHED`...) ghi vào đúng bước (`StageResult.Status.REJECTED`, `rejection`, `block`), nhánh `FAILED` với `failedStage`.
- Lỗi provider ghi `AiError.Code` ở đúng bước. Bước `COMPLETED` nhưng không có text là `FAILED`/`INCOMPLETE`, không chạy tiếp trên dữ liệu rỗng.
- Khi hủy thắng trước terminal, nhánh giữ text đã nhận, chặn delta và kết quả đến sau. Gateway vẫn quyết toán usage từ terminal theo contract. Vượt trần ngân sách giữa chừng → nhánh `FAILED`, `budgetStopped = true`, giữ partial.
- **Không tự retry.** `retry(channelId)` là thao tác của người dùng: chạy lại từ bước text đầu tiên chưa xong, attempt mới, requestId mới, reservation mới; các bước đã xong giữ nguyên. Callback của attempt cũ không ghi đè (generation guard).
- Session hoạt động trong bộ nhớ. Sau khi process chết, `restore` công bố checkpoint đã phục hồi và chờ người dùng retry. Store Room thuộc phần tích hợp tiếp theo của SP-033. [Hợp đồng checkpoint](agent-checkpoint-mapping.md)
- Snapshot được xếp hàng theo `revision` và giao tuần tự ngoài khóa của session, kể cả khi đang stream. Observer đưa cập nhật giao diện về main thread. Nếu executor từ chối lượt giao, callback chạy trên luồng gọi để vẫn giao được trạng thái. Lỗi runtime của observer không dừng pipeline. Lỗi runtime ở bước admission được ghi `SUBMISSION_FAILED` để người dùng có thể retry. Nếu adapter ném lỗi trong `prepare()`, gateway ghi `PROVIDER_PREPARE_FAILED` và giải phóng khoản giữ. Ngoại lệ sau khi vào `run()` giữ partial và quyết toán `UNCERTAIN`.

## 4. Prompt và quy tắc

- `PromptBuilder` rào brief và nguồn bằng `<<<DATA … DATA>>>`, vô hiệu marker nằm trong dữ liệu. Mọi chỉ dẫn trong dữ liệu (URL, comment, "duyệt/đăng") bị bỏ qua; model chỉ tạo bản nháp, người dùng duyệt trước khi đăng. Ngôn ngữ nội dung theo `brief.language` (vi/en), không theo ngôn ngữ UI.
- Không nêu giới hạn ký tự nào vì chưa có số đã xác minh. `RuleCheck` luôn có `LENGTH_LIMIT_UNVERIFIED`.
- `RuleCheck` = verdict của model (`PASS`/`REVISE`/`UNPARSED`, danh sách issue) cộng kiểm tra cục bộ: `EMPTY_DRAFT`, `LINK_NOT_IN_SOURCES` (link trong nháp không có trong nguồn).
- `RuleCheck` so URL đầy đủ trước, rồi thử bỏ dấu câu ở cuối từ trong nháp khi phần còn lại khớp một URL nguồn. Host và scheme không phân biệt hoa thường, còn path, query và fragment giữ nguyên hoa thường. Nguồn được đánh dấu URL có thể dùng host/path thiếu scheme để khớp cả HTTP và HTTPS. Nguồn ghi scheme tường minh chỉ khớp scheme đó. Bản tự rà có issue đã trích xuất luôn cần xem lại, kể cả khi verdict là `PASS`.
- Output cap mỗi bước: outline 400, draft 1000, rule check 300 token; registry vẫn chặn model có cap thấp hơn.

## 5. Bàn giao cho Thiện (SP-036)

`TextStageOutput.of(snapshot, channelId, language)` trả DTO bất biến khi nhánh `TEXT_READY`: `sessionId`, `briefId`, `channelId`, `connectionRevision`, `platform`, `language`, `outline`, `draft`, `ruleCheck`, `requestIds` theo bước, `fullChainComplete = false`. Thiện áp dụng thành version mới **chưa duyệt** sau expected-current guard. DTO không phải lệnh đăng, không phải approval. Fixture test: `ChannelPipelineTest`, `TextStagesTest`.

## 6. Kiểm thử

| Test | Nội dung | Kết quả |
|---|---|---|
| `ChannelPipelineTest` (provider giả có nhãn + gateway thật + `RecordingBudget`) | Một target: 3 request đúng thứ tự, requestId riêng, không kênh thứ hai; hai target chồng lấp (barrier + timestamp) và cô lập lỗi; budget từ chối; chưa chọn model; hủy một nhánh giữa stream; hủy cả session; retry từ bước lỗi với requestId mới; callback cũ không ghi đè; vượt trần giữa chừng; hoàn tất rỗng | Pass, chạy lặp 6 lần không flaky |
| `ChannelPipelineHttpTest` | Pipeline → gateway → `OpenAiProvider` thật → MockWebServer: 3 request có Bearer, đúng cap từng bước, draft mang outline, mỗi bước settle 80 micros | Pass |
| `TextStagesTest` | Luật target, mapping brief, rào dữ liệu prompt, parse rule check, bước tuần 4 `NOT_IMPLEMENTED` | Pass |
| Bộ 20 brief (10 VI/10 EN, Proposal §5.1) với provider thật | **NOT_RUN**: chưa có model đã xác minh, key/quota/trần thử được cấp | — |
| UI | Không có trong phạm vi tuần 3 (consumer UI là SP-036 của Thiện) | — |
