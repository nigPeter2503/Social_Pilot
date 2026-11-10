# SP-013 — Hợp đồng AI và spike OpenAI/Gemini

Issue [#15](https://github.com/nigPeter2503/Social_Pilot/issues/15) · F8.B1, F8.B3, F8.B4, INFRA · Owner Nguyễn Đức Đạt.

Hai adapter Java dùng OkHttp trên Android. Spike giới hạn ở text stream với một kết quả văn bản. Ứng dụng chưa đăng ký adapter vào UI, chưa có kho BYOK, budget production hay tự động retry/fallback. Test HTTP dùng payload tổng hợp và credential giả.

## 1. Request, model và event

- `AiRequest` mang `sessionId`, `channelId`, `connectionRevision`, `stepId`, `requestId`, provider/model, loại tác vụ, prompt và giới hạn output. Scope dùng chung có cả channel và revision null. Scope gắn kênh phải có cả ID và revision dương. `requestId` mới cho mỗi lần dispatch, kể cả khi retry/fallback cùng logical task. Đối chiếu quy ước ID trong [PR SP-012](https://github.com/nigPeter2503/Social_Pilot/pull/71). Spike giữ ledger ở ranh giới contract.
- Consumer cung cấp danh sách `ModelCapabilities` theo đúng provider, model và output cap. Các capability có trạng thái supported, unsupported hoặc unknown. Adapter chỉ cho phép text và text streaming đã được xác nhận trong registry. Model thử thật chưa được chọn. Với image, consumer có thể đề xuất template cục bộ có nhãn riêng hoặc một adapter hỗ trợ image trong task sau.
- `AiProvider.prepare(request, listener)` tạo một `Attempt` có thể run một lần. Prepare không gọi callback, đọc credential hoặc gửi mạng. Consumer giữ ngân sách và đăng ký handle trước khi submit. `run()` chạy trên worker gọi nó, callback được phát tuần tự trên cùng worker. Listener cần trả về bình thường và chuyển snapshot về đúng dispatcher/UI của consumer.
- `TEXT_DELTA` có sequence tăng dần theo attempt. Consumer append mỗi delta đã chấp nhận một lần. Terminal `COMPLETED`, `FAILED` hoặc `CANCELLED` có usage và cờ `mayHaveDispatched`. Terminal thất bại hoặc hủy có `partial=true` khi đã phát text. Lỗi và credential provider không được gửi sang domain dưới dạng message thô.

| Trường hợp | Chuỗi event ví dụ | Xử lý consumer |
|---|---|---|
| Thành công | `STARTED(1) → TEXT_DELTA(2, "Xin ") → TEXT_DELTA(3, "chào") → COMPLETED(4)` | Lưu kết quả `Xin chào` cùng usage hoặc nhãn thiếu |
| Đứt trước terminal | `STARTED(1) → TEXT_DELTA(2, "Xin ") → FAILED(3, INCOMPLETE)` | Giữ prefix làm partial. Outcome chi phí có thể chưa xác định |
| Hủy giữa stream | `STARTED(1) → TEXT_DELTA(2, "Xin ") → CANCELLED(3)` | Dừng Call, giữ prefix theo policy. Coordinator chặn callback đã xếp hàng khi attempt không còn hiện hành |
| Từ chối capability | `FAILED(1, UNSUPPORTED_CAPABILITY)` | Không đọc credential hay dispatch. Budget denial thuộc consumer trước submit |
| Hủy trước khi worker bắt đầu | `CANCELLED(1)` nếu worker vẫn được gọi | Nếu Future đã bị hủy trước khi run, consumer tự kết thúc attempt chưa dispatch và release một lần |

## 2. Wire và lỗi

| Provider | Transport | Điểm kết thúc và usage |
|---|---|---|
| OpenAI | POST Responses, SSE, Bearer header, `store=false`, `max_output_tokens` | Append `response.output_text.delta` cho một text part. `response.output_text.done` không thêm text. Chỉ terminal `response.completed` với trạng thái completed là ứng viên thành công. Map incomplete, failure và refusal riêng. Dùng usage terminal nếu có |
| Gemini | POST `streamGenerateContent?alt=sse`, `x-goog-api-key` header, một candidate và `maxOutputTokens` | Append text parts hiển thị. `STOP` và stream kết thúc hợp lệ mới hoàn tất, cho phép metadata theo sau `STOP`. Không append thought. Lấy snapshot usage cuối, không cộng các snapshot lặp. Khi stream đứt trước terminal, metadata đã quan sát là partial |

SSE reader xử lý CR/LF, comment, nhiều dòng data và UTF-8 phân mảnh. Có giới hạn dòng/frame/output, connect/read/call timeout hữu hạn. Stream không có terminal hợp lệ, trả content type sai hoặc frame hỏng được phân loại riêng. Chỉ báo thành công khi có text đã phát. Wire contract tham chiếu [OpenAI streaming events](https://developers.openai.com/api/reference/resources/responses/streaming-events) và [Gemini generate content](https://ai.google.dev/api/generate-content).

Hai adapter dùng URL HTTPS cố định cho provider của mình. Constructor dùng endpoint khác chỉ khả dụng trong package cho HTTP fixtures. Credential chỉ xuất hiện trong header tương ứng. Client không có interceptor ghi log, tắt redirect và connection retry, request body one-shot. Response được đóng khi worker kết thúc. Backend OAuth nằm ngoài đường gọi này.

Error code phân biệt credential sai, thiếu quyền, rate limit, quota cứng khi provider có mã tương ứng, timeout, lỗi kết nối, capability không hỗ trợ, provider unavailable, request lỗi, content blocked, incomplete và lỗi protocol. `Retry-After` dạng số giây được giữ khi có. Dạng khác hoặc thiếu header để null. Error taxonomy giúp consumer quyết định hành động. Adapter tự gửi đúng một attempt.

## 3. Usage, estimate và ngân sách

- `AiUsage` giữ input/output/total, cached input và reasoning tokens dạng nullable, với `REPORTED`, `PARTIAL`, `MISSING`. Giá trị 0 được giữ nguyên nếu provider thực sự gửi. Snapshot quan sát giữa stream Gemini bị đứt chỉ dùng làm partial.
- Token field giữ semantics provider. OpenAI output có thể bao gồm reasoning; Gemini gửi candidate tokens và thoughts riêng. Consumer đối chiếu bảng giá và quy tắc model để tạo các bucket không trùng. `AiCostEstimate.text()` nhận uncached input, cached input và output đã chuẩn hóa, giá theo triệu token, currency và price revision. Số micros được làm tròn lên. Chỉ định price revision bằng nguồn và thời điểm chốt trong bằng chứng. Không ước tính usage thiếu bằng 0 và không dùng estimate làm invoice.
- Dùng một `BudgetManager` cho tất cả provider, nhánh và task. Reserve nguyên tử theo `requestId` trước khi execute, pin ngày/tháng theo zone đã chọn và bảng giá. ID đã từng reserve không được cấp lại. `settle` dùng estimate từ usage đủ để tính, idempotent khi terminal lặp. `releaseUndispatched` chỉ dùng khi chắc chắn chưa gửi. Nếu có thể đã gửi và thiếu usage có thể tính giá, dùng `markUncertain` để giữ reservation. Có thể settle sau đối soát. Cancel phía máy không xác nhận chi phí provider bằng 0.
- Ví dụ giả lập với 100 micros còn lại. A/B cùng reserve 60, chỉ một lượt được cấp. A hủy sau dispatch và thiếu usage giữ 60 ở trạng thái uncertain. Retry/fallback phải reserve bằng ID mới từ phần còn lại. Khi đã đến trần, giữ partial và báo lý do. Chỉ đề xuất fallback cho lỗi khả dụng, khi scope/capability/budget cho phép. Spike không tự fallback.

## 4. Bàn giao SP-014, Thiện và Nghị

1. Cấp attempt ID và lưu scope/revision trước khi submit. Dùng fake `AiProvider` trong unit test, hoặc hai adapter với MockWebServer trong HTTP test. Contract test minh họa listener nhận event theo thứ tự.
2. Khi hủy, đổi trạng thái branch để chặn callback muộn, gọi `Attempt.cancel()` để hủy Call rồi hủy Future đang chạy. Một delta đã được chọn ngay trước thao tác hủy có thể đến callback sau đó. Kiểm tra attempt/revision tại thời điểm áp dụng event. Quyết toán ngân sách theo terminal hoặc theo quy tắc chưa dispatch nếu Future không bao giờ run.
3. Coordinator append delta đúng thứ tự, giữ snapshot bất biến và gộp lần cập nhật LiveData khi cần. Retry tạo attempt mới cho đúng nhánh. Việc dựng lại view chỉ đăng ký observer, không tự submit lại.
4. Thiện/Nghị có thể kiểm tra request, success/error/cancel, usage thiếu và capability trong `AiProvidersTest` và `BudgetContractTest`. Hợp đồng phục vụ module viết lại, phân loại và báo cáo. Nghiệp vụ riêng của từng module do owner triển khai.

## 5. Nghiệm thu

| Phần | Bằng chứng |
|---|---|
| Offline | `AiProvidersTest` dùng JUnit và MockWebServer cho fragmented SSE, order, usage, provider errors, timeout, disconnect, redirect, one-shot và hủy HTTP đang đọc. `BudgetContractTest` dùng ledger giả cho reserve đồng thời, retry, settle và uncertainty. Kết quả thực thi theo PR và CI của revision đang review |
| OpenAI text thật | **NOT_RUN** · chờ quyền API riêng, model, quota, nguồn credential cục bộ và trần chi |
| Gemini text thật | **NOT_RUN** · cùng gate, bằng chứng riêng cho Gemini |
| Image, budget production, UI BYOK | Theo phạm vi các task sau |

Sau khi có quyền riêng, chạy từ Android tối đa một request mỗi provider với prompt tổng hợp, output cap và trần đã duyệt. Hồ sơ ghi thời điểm, revision, model, request count, output đã che dữ liệu, usage hoặc trạng thái thiếu, bảng giá và estimate. Không đóng #15 hoặc báo F8.B1 đạt chỉ dựa trên fixture.
