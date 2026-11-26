# SP-028 — Provider, lựa chọn model và usage dùng chung

Issue [#30](https://github.com/nigPeter2503/Social_Pilot/issues/30) · F8.B1, F8.B3, F8.B4 · Owner Nguyễn Đức Đạt (TV2). Phụ thuộc SP-027 (BYOK) và adapter SP-013. Bên tiêu thụ: Thiện (nội dung), Nghị (phân loại/trả lời comment, báo cáo), SP-031 pipeline, SP-029 ledger.

## 1. Đường gọi duy nhất

Mọi tác vụ AI đi qua `AppContainer.ai` (`data/ai/AiGateway`). Consumer **không** tạo `OpenAiProvider`/`GeminiAiProvider` riêng và không tự gọi `BudgetManager`.

```text
AiGateway.Call(session, channel+revision | shared, step, taskType, prompt, maxOutputTokens)
  → lựa chọn model của taskType (ModelSelectionStore)
  → registry: capability/verified/output cap/bảng giá (ModelEntry.blockFor)
  → key BYOK usable() (SP-027)
  → AiRequest mới với requestId UUID mới
  → BudgetManager.reserve(requestId, upper bound) với kỳ ngày/tháng ghim lúc reserve
  → một Attempt trên worker → settle | markUncertain | releaseUndispatched đúng một lần
  → UsageLog.record(UsageRecord) idempotent theo requestId
```

- `submit()` làm I/O (đọc trạng thái key), gọi từ worker. Callback chạy tuần tự trên worker của attempt; consumer tự chuyển về main thread.
- Mỗi lần `submit` là một dispatch với requestId mới. Gateway **không** tự retry, fallback hay đổi provider. Retry do consumer quyết định và lại đi qua `submit` (reserve mới).
- `resolve(call)` trả `AiRequest` typed hoặc lý do bị chặn, không reserve. SP-031 dùng nó để dựng request cho coordinator hai nhánh, coordinator tự reserve qua cùng `BudgetManager`.
- `Submission.cancel()`: an toàn trên mọi luồng, kể cả luồng UI. Hàm chỉ đánh dấu hủy và hủy HTTP Call; không đọc ghi ledger/usage, không gọi callback trên luồng gọi. Nếu lượt còn trong hàng đợi, chính job worker đã xếp sẽ kết thúc nó: không gọi provider, release đúng một lần và trả đúng một terminal `CANCELLED` với `mayHaveDispatched=false` trên worker. Nếu cancel trùng lúc worker vừa bắt đầu, attempt tự kết thúc `CANCELLED` chưa dispatch.

| Rejection | Ý nghĩa | Có gọi BudgetManager/mạng không |
|---|---|---|
| `NO_MODEL_SELECTED` | Tác vụ chưa chọn model | Không |
| `MODEL_BLOCKED` + `Block` | `UNVERIFIED`, `CAPABILITY_UNKNOWN`, `CAPABILITY_UNSUPPORTED`, `TASK_NOT_ALLOWED`, `OUTPUT_CAP_EXCEEDED`, `PRICE_MISSING` | Không |
| `PROVIDER_NOT_REGISTERED` | Không có adapter | Không |
| `KEY_NOT_USABLE` | Key không ở trạng thái `VALID` | Không |
| `BUDGET_NOT_CONFIGURED` | Chưa có ledger production (SP-029) | Không |
| `BUDGET_LIMIT_REACHED`, `DUPLICATE_REQUEST` | Admission từ ledger | Chỉ `reserve` |
| `WORKER_UNAVAILABLE` | Executor từ chối; reservation được release, không callback | `reserve` + `release` |

Sau khi được nhận, lỗi provider đi theo `AiError.Code` của SP-013 trong terminal event.

**Trạng thái production hiện tại:** `AppContainer` truyền `budget = null`, nên `dispatchEnabled() == false` và mọi `submit` bị `BUDGET_NOT_CONFIGURED`. Đây là chủ ý theo brief: không mở dispatch production khi ledger SP-029 chưa có. UI hiển thị "Gọi AI đang tạm khóa…".

## 2. Registry model và bảng giá

`assets/ai_model_registry.json` (đọc bằng `ModelRegistryParser`). File lỗi → registry rỗng, không có gì dispatch được. Mỗi dòng có provider, model, capability `text/streamingText/image/usage` (`SUPPORTED|UNSUPPORTED|UNKNOWN`), `maxOutputTokens`, danh sách `tasks`, `verification`, `price` và `source`.

- Một model chỉ dispatch được khi: tác vụ được phép, capability cần thiết là `SUPPORTED`, `verification = VERIFIED`, output yêu cầu ≤ cap, và có `price` với `priceRevision`, `source`, `capturedAt`.
- Ảnh (`AiTaskType.IMAGE`) dùng capability `image` riêng, không suy ra từ text. Cấu hình ảnh/template thuộc SP-032.
- **Registry hiện tại:** hai dòng ứng viên `gpt-4.1-mini` (OpenAI) và `gemini-2.5-flash` (Gemini) để chuẩn bị phép thử live. Capability đều `UNKNOWN`, `UNVERIFIED`, `price = null`. Tên model là đề xuất cần xác minh theo `source`, không phải thông tin đã chốt; không có giá nào được điền theo trí nhớ. Test `registryAssetParsesAndEveryCandidateIsBlocked` khóa điều kiện này.
- Khi có quyền thử: owner xác minh model với nguồn chính thức, ghi bảng giá (link, thời điểm lấy, currency), chạy tối đa số lượt được cấp, rồi mới đổi dòng sang `VERIFIED`/`SUPPORTED` kèm `price` trong PR riêng có evidence.

### Ước tính chi phí (`PriceSheet`)

- Upper bound trước dispatch: số byte UTF-8 của prompt (tokenizer byte-level không vượt 1 token/byte) × giá input cao nhất + `maxOutputTokens` × giá output.
- Từ usage: OpenAI `input_tokens` đã gồm cached, `output_tokens` đã gồm reasoning. Gemini `promptTokenCount` gồm cached content; `thoughtsTokenCount` tính thêm như output ngoài `candidatesTokenCount`.
- Chỉ usage `REPORTED` được quyết toán. `PARTIAL` (stream đứt) hoặc `MISSING` sau khi có thể đã gửi → `markUncertain`, estimate `null`. Usage 0 chỉ khi provider thật sự trả 0.
- Rủi ro đã biết: nếu provider tính thinking ngoài cap output, chi phí thật có thể vượt upper bound; ledger SP-029 phải chấp nhận settle lớn hơn reserve và ghi chênh lệch.

## 3. Lựa chọn model và usage

- `ModelSelectionStore` (file `ai_model_selection_v1`) lưu `provider|model` cho từng `AiTaskType`. Chỉ là cài đặt: đổi lựa chọn không chạm draft, key hay usage đã ghi. Model đã bị gỡ khỏi registry đọc ra là "chưa chọn".
- `UsageRecord` (mỗi requestId): session/channel/step, task, provider/model, kỳ `BudgetPeriod` (zone IANA + `dayKey` + `monthKey`) ghim lúc reserve, usage nguyên trạng, `SETTLED|UNCERTAIN|RELEASED`, estimate micros (chỉ khi `SETTLED`), currency, price revision.
- `UsageSummary.summarize(records, DAY|MONTH, key)`: theo provider/kỳ/currency; không trộn currency; lượt `UNCERTAIN` được đếm riêng (`complete() == false`) và không cộng như 0; trùng requestId chỉ tính một lần.
- `UsageLog` là interface; bản production là ledger Room của SP-029. Hiện `AppContainer.usageLog = null`, UI hiển thị "Chưa có sổ ghi usage".
- Kỳ dùng `Calendar`/`TimeZone` (API 23 không có `java.time` khi chưa bật desugaring). Zone không hợp lệ bị từ chối thay vì rơi về GMT.

## 4. Bàn giao

| Bên nhận | Dùng gì |
|---|---|
| Thiện (SP-036 và nội dung) | `AiTaskType.CONTENT_TEXT`, `AiGateway.submit` hoặc pipeline SP-031; hiển thị `Rejection`/`Block`/`AiError.Code` |
| Nghị (SP-043/report) | `COMMENT_CLASSIFICATION`, `COMMENT_REPLY`, `ANALYTICS_REPORT` cùng gateway; không có đường riêng |
| SP-029 | Implement `BudgetManager` + `UsageLog` trên cùng ledger, rồi truyền vào `AppContainer` để mở dispatch |
| SP-031 | `resolve()` để dựng request, `PriceSheet.upperBound/fromUsage` cho `Pricing` của coordinator |
| SP-032 | Dòng registry có `IMAGE` và capability `image` riêng |

## 5. Kiểm thử

| Ca | Kết quả |
|---|---|
| `ModelPricingTest` (9): block reasons, ảnh tách capability, registry trùng/không có, upper bound, bucket OpenAI/Gemini, usage thiếu ≠ 0, provenance giá, kỳ Bangkok qua nửa đêm/cuối tháng, zone lạ | JVM, đạt |
| `UsageSummaryTest` (4): uncertain/missing không cộng 0, tách provider/kỳ/currency, trùng requestId, ràng buộc estimate | JVM, đạt |
| `AiGatewayTest` (12): không ledger → không prepare/gửi; chặn trước reserve; requestId mới mỗi lần; scope/step/cap đúng; settle một lần dù terminal lặp; kỳ ghim lúc reserve khi terminal sau nửa đêm; missing/partial → uncertain; hủy trước worker release một lần; hủy từ luồng UI (kho từ chối I/O trên luồng đó) không ném lỗi, kết thúc trên worker, không gọi provider, đúng một callback `CANCELLED`; limit; worker đầy; đổi provider giữ usage; selection bền và quên model bị gỡ; adapter OpenAI thật qua MockWebServer (header, `max_output_tokens`, usage REPORTED); asset registry đều bị chặn | JVM, đạt |
| UI chọn model trên emulator API 37 | Đạt, xem [bằng chứng](evidence/sp-028-device-check-2026-11-23.md) |
| OpenAI text thật, Gemini text thật qua Android | **NOT_RUN** — chưa có key/model/quota/số lượt/trần chi được cấp; dispatch production còn khóa tới SP-029 |

Lệnh tại `android/`: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
