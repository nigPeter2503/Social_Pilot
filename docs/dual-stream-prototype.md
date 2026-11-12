# SP-014 — Prototype hai luồng và hủy độc lập

Issue [#16](https://github.com/nigPeter2503/Social_Pilot/issues/16) · F2.B1, F2.B4, F2.B5 · Owner Nguyễn Đức Đạt.

Prototype nhận hai yêu cầu text thuộc cùng phiên nhưng khác kênh. Runtime dùng hợp đồng `AiProvider.prepare()` của [SP-013](https://github.com/nigPeter2503/Social_Pilot/pull/72). Bằng chứng mạng trong tài liệu này dùng MockWebServer. Việc gắn vào Android UI, Room và Worker thuộc [SP-017](https://github.com/nigPeter2503/Social_Pilot/pull/70).

## Runtime port

| Thao tác | Hành vi |
|---|---|
| `start(a, b)` | Đăng ký một cặp cho vòng đời coordinator. Hai nhánh được admission riêng qua ledger chung. Nếu A bị từ chối, B vẫn có thể chạy |
| `snapshot()` và `observe(executor, observer)` | Đọc snapshot bất biến. Một observer cho một coordinator tại một thời điểm. Thay observer chỉ nhận state, không tạo request. Dùng main-thread executor cho UI và gộp lần cập nhật |
| `cancelBranch(channelId)` | Đóng state của nhánh ngay, giữ prefix đã nhận, gọi handle hủy HTTP và `Future.cancel(true)`. Hủy lặp là idempotent |
| `cancelSession()` | Áp dụng hủy cho các nhánh còn chạy. Giữ kết quả của nhánh đã terminal |
| `retry(request)` | Chỉ chấp nhận nhánh lỗi/hủy khi worker cũ đã dọn xong. Cùng session, channel, revision, step, task và request ID mới. Nhánh kia giữ ID, text và phase |
| `close()` | Hủy phần còn chạy và dừng nhận việc. Dùng khi owner phiên bị dispose, không dùng khi chỉ tháo observer vì xoay màn hình |

## Thứ tự, hủy và chi phí

- Hai worker thuộc một `ThreadPoolExecutor` có queue hữu hạn. Coordinator nhận tối đa hai attempt đang chạy hoặc đang dọn. Mọi transition của phiên đi qua cùng monitor. Worker áp dụng event đồng bộ trước khi tiếp tục đọc stream, tạo backpressure mà không xếp hàng từng token trên UI. Observer chỉ được gọi ngoài monitor.
- State nhận event khớp session/channel/revision/step/request/provider/model. Sequence tăng liên tục. Event cũ/lặp bị bỏ. Gap hoặc vượt giới hạn text chuyển lỗi protocol. Text được tích lũy một lần, snapshot chỉ materialize khi có người đọc.
- Bằng chứng dispatch được giữ tăng dần trong attempt. Sau text hoặc cờ dispatch đã nhận, terminal báo chưa dispatch bị coi là mâu thuẫn. Terminal sai thứ tự hoặc sai tiến trình giữ reservation ở UNCERTAIN. Chỉ terminal hợp lệ và không có bằng chứng dispatch mới được release. Terminal lặp không quyết toán lần hai.
- Handle chuẩn bị và `FutureTask` được giữ trước khi submit. Nếu cancel thắng trước khi worker vào, worker bị chặn dispatch và ledger release. Nếu worker đã vào, cancel transport và Future độc lập. Reservation được settle từ terminal đáng tin hoặc chuyển uncertain nếu có thể đã gửi mà chưa định giá được. Terminal tới muộn của attempt đang dọn vẫn có thể dùng cho ledger. UI đã hủy chỉ giữ prefix tại thời điểm hủy. Worker ra mà thiếu terminal làm nhánh đang chạy chuyển lỗi protocol và giữ reservation uncertain.
- Consumer cung cấp provider resolver, pricing và ledger dùng chung. Các phép tính/transaction ở ranh giới này cần hoàn thành nhanh và bảo toàn reservation khi lỗi. Test dùng ledger/giá giả. Runtime không tự retry hoặc fallback.

## Bàn giao cho Thiện

1. Giữ coordinator ở ViewModel/Repository cấp phiên. Nếu ledger dùng I/O, gọi lệnh runtime từ executor nghiệp vụ. `observe()` trả `Cancelable` để tháo observer của view. Khi tạo lại view, attach với main-thread executor và render snapshot mới nhất. Nếu callback cũ đã bắt đầu trước khi tháo, kiểm tra lifecycle hiện hành trước khi ghi view.
2. Map `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` và `INTERRUPTED` sang state SP-017. LiveData nhận snapshot, không xếp hàng token. Dùng `canRetry` để bật retry khi worker đã dọn xong. Với nhánh FAILED hoặc CANCELLED, chỉ gọi retry sau thao tác Bắt đầu/Thử lại tường minh của người dùng, cấp request ID và reserve mới. Nhánh còn lại giữ kết quả. Thiện xác nhận mapping nút này khi nối runtime vào SP-017.
3. Khi đọc checkpoint còn RUNNING sau process death, chuyển sang INTERRUPTED và giữ text đã lưu. `BranchSnapshot.afterProcessDeath()` minh họa phép chiếu thuần dữ liệu. Ví dụ `{sessionId: "session", channelId: "A", requestId: "a1", phase: "RUNNING", text: "prefix"}` render thành `INTERRUPTED` với cùng ID/text. Khi người dùng yêu cầu chạy lại, tạo phiên/attempt phù hợp với UI và đối soát ngân sách trước khi gửi.
4. Khi nối persist, kiểm tra revision và thực hiện ghi trong cùng transaction của module owner. WorkManager chỉ phụ trách việc trì hoãn hữu hạn. Adapter, wiring UI và thao tác thiết bị của #70 cần kiểm lại trên revision tích hợp.

## Kiểm chứng

| Ca | Method kiểm thử | Điều kiện đạt |
|---|---|---|
| T01–T02 · overlap, thứ tự | `DualStreamHttpTest.bothHttpRequestsOverlapAndFragmentedTextRetainsOrder` | Hai request đến server trước khi barrier nhả response. Monotonic clock và text UTF-8 phân mảnh khớp |
| T03–T04 · cancel A, giữ B | `DualStreamHttpTest.cancelAClosesBlockedHttpWhileBCompletesAndOnlyARetries`, `DualStreamCoordinatorTest.cancelStopsFutureAndTransportAndRetryRejectsOldCallbacks` | A thoát HTTP bị chặn, Future nhận interrupt, B hoàn tất |
| T05 · callback muộn và race terminal | `DualStreamReviewRegressionTest.terminalBeforeOrAfterCancelPreservesTheWinningOutcome` và ca callback cũ ở T03 | Bốn thứ tự success/error trước/sau cancel, giữ phase/text đã thắng và quyết toán một lần |
| T06 · lỗi riêng A | `DualStreamHttpTest.disconnectKeepsPartialAAndDoesNotFailB` | A lỗi, B hoàn tất và giữ text |
| T07 · retry lỗi và queue đầy | `DualStreamHttpTest.retryAfterHttpFailureOnlyAddsOneRequestForA`, `DualStreamReviewRegressionTest.failedBranchRetriesAfterQueueRejectionWithoutChangingB` | ID mới, chỉ thêm một HTTP request A sau lỗi. Submit bị reject kết thúc FAILED/EXECUTOR và release một lần. Khi có chỗ, retry ID mới thành công, B giữ nguyên |
| T08 · timeout | `DualStreamHttpTest.timeoutIsNotCancellationAndDoesNotFailB` | Timeout riêng A, B hoàn tất |
| T09–T10 · disconnect, partial | `DualStreamHttpTest.disconnectKeepsPartialAAndDoesNotFailB`, `retryAfterHttpFailureOnlyAddsOneRequestForA` | EOF thiếu terminal hoặc body bị cắt giữ prefix và báo lỗi |
| T11–T12 · rotation/restart | `DualStreamCoordinatorTest.rotateAndRestoreDoNotSubmitAgainAndCoalesceSnapshots` | Đổi observer không submit lại. Phép chiếu checkpoint RUNNING → INTERRUPTED giữ text. Emulator và Room sau wiring còn chờ |
| Hủy cả phiên đang chạy | `DualStreamHttpTest.cancelSessionStopsBothActiveHttpStreams`, `DualStreamReviewRegressionTest.cancelSessionInterruptsBothRunningFuturesAndCancelsBothTransports` | Hai HTTP stream cùng đang đọc đều thoát. Hai Future và hai handle hủy, mỗi ledger chuyển một lần |
| Hủy trước attach | `DualStreamHttpTest.cancelAfterWorkerEntryBeforeCallAttachDoesNotSendA` | Worker đã vào, cancel trước attach, A không gửi HTTP và release một lần, B hoàn tất |
| Ledger/protocol | `DualStreamReviewRegressionTest.invalidTerminalCannotReleaseAfterTextAndDuplicateCannotSettleAgain`, `validUndispatchedTerminalReleasesExactlyOnce`, `protocolGapWithoutDispatchEvidenceStillHoldsReservation` | Terminal mâu thuẫn/gap giữ UNCERTAIN. Chưa dispatch hợp lệ release. Terminal lặp không quyết toán lại |

### Kết quả local ngày 11/11/2026

- Windows, OpenJDK 21.0.13, SDK 36, Gradle Wrapper 8.13. Bản ghép local dùng SP-013 tại `06a49b9f01649aeec385d78a5b4cfd4fa6a68ebd` và source SP-014 trên base `0348982c8f295d946815da91eae491bd9645ac94`. Cây test `8206fb1663dcfb4fc87cb8892ac22501d18cd181` có cùng Java source và tests với bản bàn giao. Cây test được xuất riêng, ngoài các file tracked của nhánh.
- Tại `android`, `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` đạt. **37 unit test đạt**, gồm 11 ca SP-014 và 26 ca từ baseline/SP-013. Lint có 0 lỗi, 12 cảnh báo.
- Trace MockWebServer dùng microsecond kể từ đầu ca, A `[6074, 8290]`, B `[5785, 8185]`. Hai request cùng đến trước khi barrier nhả response. Ca disconnect cắt body ngắn hơn Content-Length trả `PROTOCOL_ERROR` theo adapter và giữ prefix.
- Emulator với runtime đã nối SP-017 và provider thật: `NOT_RUN`. PR SP-013 chưa có trên `main`. Khi dependency merge, đồng bộ nhánh từ `main` và chạy lại gates trước khi chuyển PR SP-014 sang Ready for review.

### Review follow-up ngày 12/11/2026

[Bằng chứng thực thi](evidence/sp-014-review-verification-20261112.txt) gắn với source `452b607892fbfd03e9e5ce0b58e09c4ddde18881`, dependency SP-013 `82bf8c6` và cây ghép `3f9a24947a908730f767ab644dbd1e414008dcf5`. Test hồi quy thất bại trên coordinator cũ. Bản sửa đạt cả ba tác vụ Android, **46 unit test**, lint 0 lỗi và 12 cảnh báo. Trong đó có 20 ca SP-014, bao gồm các ca bổ sung theo [review của Nghị](https://github.com/nigPeter2503/Social_Pilot/pull/73#pullrequestreview-5861818770).

CI độc lập của PR trên `main` còn chờ SP-013 merge. Sau khi đồng bộ dependency cần chạy lại gates trên head mới. Phần emulator/Room với runtime đã nối SP-017 tiếp tục được ghi `NOT_RUN`.
