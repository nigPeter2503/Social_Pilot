# SP-029 — Ledger ngân sách AI nguyên tử

Issue [#31](https://github.com/nigPeter2503/Social_Pilot/issues/31) · F8.N1, F8.B4 · Owner Nguyễn Đức Đạt (TV2). Phụ thuộc SP-028 ([ai-model-usage-contract.md](ai-model-usage-contract.md)) và [AI contract §3](ai-provider-contract.md). Bên tiêu thụ: Thiện, Nghị, pipeline SP-031, ảnh SP-032.

## 1. Một ledger cho mọi lượt gọi

- `domain/contract/ai/AttemptLedger` = `BudgetManager` + `UsageLog` + trần + chi tiêu hiện tại + listener khi vượt trần. Bản production là `data/ai/ledger/BudgetLedger` trên Room, giữ ở `AppContainer.ledger` và truyền vào `AppContainer.ai`.
- Mọi provider, tác vụ (viết, phân loại, trả lời, báo cáo, sau này ảnh) và kênh dùng **cùng** instance. Không có đường riêng: consumer gọi `AppContainer.ai.submit` hoặc pipeline SP-031; coordinator SP-014 nhận `AppContainer.ledger` làm `BudgetManager`.
- Mỗi attempt là một dòng `ai_attempts` theo `requestId`. Reservation, quyết toán và usage nằm cùng dòng, nên báo cáo không thể lệch với admission.

## 2. Ngữ nghĩa

| Thao tác | Quy tắc |
|---|---|
| `reserve(request, taskType, upperBound)` | Một transaction Room: kiểm requestId chưa tồn tại → đọc trần → tính kỳ theo zone của trần → cộng đã quyết toán + đang giữ của ngày **và** tháng → chèn dòng `RESERVED`. Trả `RESERVED`, `LIMIT_REACHED` hoặc `DUPLICATE_REQUEST`. Không có trần, khác currency hoặc tràn `long` đều là `LIMIT_REACHED` |
| Kỳ và giá | `zoneId`, `dayKey`, `monthKey`, `currency`, `priceRevision` ghim lúc reserve và không bao giờ tính lại |
| `settle(id, estimate)` | Idempotent. Áp từ `RESERVED` hoặc `UNCERTAIN` (đối soát muộn). Bỏ qua nếu đã `SETTLED`/`RELEASED`. Currency hoặc price revision khác bản ghim → giữ ở `UNCERTAIN`, không áp số |
| `releaseUndispatched(id)` | Chỉ từ `RESERVED`, một lần. Dòng đã `SETTLED`/`UNCERTAIN` không bao giờ được giải phóng |
| `markUncertain(id)` | Từ `RESERVED`: có thể đã gửi nhưng thiếu usage tính được. Vẫn giữ số đã reserve |
| `record(UsageRecord)` | Ghi usage một lần theo requestId; không đổi trạng thái hay kỳ. Gateway không còn gọi riêng `settle`/`markUncertain`/`releaseUndispatched` + `record` |
| `complete(id, mayHaveDispatched, estimate, usage)` | **Đường terminal của gateway.** Một transaction: chuyển trạng thái (release / settle / `UNCERTAIN`) **và** ghi usage cùng lúc, hoặc không ghi gì. Lỗi ghi được ném ra và rollback toàn bộ: dòng vẫn `RESERVED`, lúc khởi động lại thành `UNCERTAIN` với usage `MISSING` (không bao giờ `SETTLED` mà thiếu usage). Trả `UsageRecord` dựng từ dòng đã lưu, nên kỳ, currency và price revision trong callback đúng là bản ghim lúc reserve. Lặp lại thì không đổi gì, trả bản đã lưu |
| `recoverInterrupted()` | Gọi khi khởi động. Dòng `RESERVED` của process cũ (khác `bootId`) → `UNCERTAIN`, `interrupted = 1`. Không bao giờ coi là chi phí 0 |
| `forgetChannel(channelId)` | Xóa `channelId`/`sessionId`/`stepId`/`connectionRevision` của kênh, giữ chi phí, trạng thái và kỳ. Xem §5 |

Tổng chi tiêu của một kỳ = `settledMicros` của dòng `SETTLED` + `reservedMicros` của dòng `RESERVED`/`UNCERTAIN`. `RELEASED` tính 0.

**Ví dụ 100 micros:** A và B cùng reserve 60, chỉ một lượt được cấp (`twoBranchesRacingForTheLastBudgetAdmitOnlyOne`). A hủy sau dispatch và thiếu usage → `UNCERTAIN`, vẫn giữ 60. Retry/fallback tạo requestId mới và reserve lại từ phần còn lại; ID cũ bị từ chối kể cả sau khi đã settle.

## 3. Trần và dừng khi vượt

- Người dùng đặt trần ngày và tháng (USD) trong màn hình AI. Zone là zone thiết bị lúc lưu. Trần ngày không được lớn hơn trần tháng; số dương, tối đa 6 chữ số thập phân (`BudgetInput`).
- Chưa đặt trần thì gateway trả `BUDGET_NOT_SET` và không reserve, không gửi.
- Đầy trần đúng bằng giới hạn là bình thường: lượt mới bị `BUDGET_LIMIT_REACHED`, lượt đang chạy vẫn chạy tiếp.
- **Vượt** trần (quyết toán lớn hơn reserve, hoặc người dùng hạ trần dưới mức đã dùng) gọi `ExhaustionListener`. `AiGateway` hủy mọi lượt đang chạy hoặc đang chờ: lượt chưa gửi được release, lượt đã gửi đi theo quy tắc terminal (thường là `UNCERTAIN`). `Finished.budgetStopped = true`, text partial được giữ để consumer lưu kèm lý do.
- Gateway đăng ký lượt vào danh sách đang chạy **trước** khi reserve. Nếu trần bị hạ ngay sau khi reservation commit nhưng trước khi lượt được kích hoạt, lượt không được gửi, khoản giữ được release đúng một lần và `submit` trả `BUDGET_LIMIT_REACHED` (không callback).
- Lỗi ghi ledger ở terminal: caller vẫn nhận `onFinished` với `Finished.ledgerWriteFailed = true` và `usage = null`; chi phí coi là chưa biết, khoản giữ vẫn tính vào trần.
- Đổi zone sau khi đã có reservation: dòng cũ giữ kỳ đã ghim; kỳ "hiện tại" tính theo zone mới. Chuyển zone hoặc qua nửa đêm không mở thêm ngân sách cho attempt cũ.

## 4. Lưu trữ và schema

- File riêng `ai_ledger.db` (`LedgerDatabase` version 1, schema export `android/app/schemas/com.socialpilot.app.data.ai.ledger.LedgerDatabase/1.json`). Không có FK sang `socialpilot.db`.
- **Quyết định điều phối schema (Đạt):** không chiếm lượt version của `AppDatabase`, vì v2/v3 đang thuộc các PR của Thiện. Xóa dữ liệu kênh trong `socialpilot.db` không thể cascade xóa dấu vết đối soát. Không có destructive migration fallback; migration sau này của ledger đi theo quy tắc [Room contract](room-schema-contract.md) và do owner ledger giữ lượt.
- Bảng `ai_attempts` (index `dayKey`, `monthKey`, `state`) và `ai_budget_settings` (một dòng `id = 1`).
- Mọi database đã bị loại khỏi backup và device transfer (`backup_rules.xml`, `data_extraction_rules.xml`), nên ledger không theo sang máy khác.
- Các method của ledger làm I/O database; gọi từ worker.

## 5. Còn chờ quyết định

| Việc | Trạng thái |
|---|---|
| Giữ aggregate chi phí sau khi ngắt kênh | **Đề xuất, chờ Minh (SP-024) duyệt:** khi ngắt kênh gọi `forgetChannel(channelId)`. Ledger giữ chi phí để trần và đối soát đúng, nhưng không còn định danh kênh/session. Chưa nối vào luồng ngắt kênh; chưa coi là policy đã chốt |
| Thời gian giữ dòng ledger | Chưa có policy dọn; hiện giữ toàn bộ. Chờ cùng quyết định retention SP-024 |
| Currency khác USD | Chưa hỗ trợ: estimate khác currency bị `LIMIT_REACHED` |
| Ảnh không tính theo token (SP-032) | Đi qua cùng `reserve/settle` với `AiCostEstimate` riêng của ảnh |

## 6. Kiểm thử

| Lớp | Nội dung | Kết quả |
|---|---|---|
| `BudgetLedgerTest` (JVM, `MemoryLedgerStore` có nhãn: một writer mỗi lúc, copy vào/ra, rollback khi transaction lỗi) | Race A/B 60/100, 40 lượt đồng thời không vượt trần, trần ngày/tháng, retry ID mới, settle/release một lần, uncertain, restart, qua nửa đêm/đổi zone, lệch price revision/currency, vượt trần và hạ trần, tràn số, báo cáo đa kênh/provider, quên kênh; `complete` ghi quyết toán + usage cùng lúc trong kỳ ghim, idempotent; release/uncertain/lệch giá; lỗi ghi rollback toàn bộ và restart giữ `UNCERTAIN` | Pass |
| `AiGatewayTest` | `BUDGET_NOT_SET` không reserve; vượt trần hủy lượt đang chạy (giữ partial, `UNCERTAIN`) và lượt đang chờ (release); hạ trần giữa reserve và dispatch: không gửi, release một lần, không callback; lỗi ghi ledger: caller vẫn nhận kết thúc với `ledgerWriteFailed`, dòng vẫn giữ; qua nửa đêm giữa gateway và ledger: callback dùng kỳ ledger ghim (ledger thật) | Pass |
| `BudgetInputTest` | Parse/format số tiền | Pass |
| `LedgerDatabaseDeviceTest` (Room thật trên emulator) | Cài mới không trần; reserve đồng thời từ hai ledger trên cùng DB không vượt trần và trùng ID chỉ một lượt thắng; đóng/mở lại DB giữ reservation ở `UNCERTAIN`; `complete` lỗi sau khi đã ghi dòng vào SQLite thì Room rollback cả quyết toán lẫn usage, ghi lại sau đó thành công một lần | Pass, xem [evidence](evidence/sp-029-device-check-2026-11-23.md) |
| Lượt AI thật qua ledger | **NOT_RUN**: registry chỉ có ứng viên chưa xác minh, chưa được cấp key/quota/trần thử | — |

Ledger giả trong test không phải trần production và số micros trong fixture không phải hóa đơn provider.
