# SP-011 — Contract sửa lỗi sau review

Bản đề xuất ngày 15/11/2026 cho Minh (owner), Đạt và Nghị (reviewer). Mốc tái hiện là `a3858c7`, mốc tách phần SP-011 khỏi dependency là `15875c4`. Các ô dưới đây dành cho kết quả trên revision sửa. [Issue #13](https://github.com/nigPeter2503/Social_Pilot/issues/13) · [PR #77](https://github.com/nigPeter2503/Social_Pilot/pull/77) · [Social v1](../social-contract.md)

Owner đã duyệt contract và yêu cầu hỏi trước các quyết định còn mở. Owner chọn giữ
channelId cùng mốc revision bền vững riêng, cấp ID mới nếu mất cả mốc và giữ khóa
chống đăng trùng theo tài khoản.

Cập nhật sau triển khai local ngày 15/11. Sáu mục sửa đã có code và regression.
Gate cuối đạt 93 JVM tests, 7 instrumentation tests trên SM-G990E / Android 16,
assembleDebug và lintDebug đạt, lint 0 errors/5 warnings. Kết quả và hash code đã thử
ở [report](../evidence/sp-011-review-fixes-2026-11-15.json). Code/tests đã commit 26b594b theo ủy quyền push ngày 15/11; hồ sơ trong commit đi kèm.

Owner chọn hàng đợi callback theo sessionId, kiểm tra lại TTL lúc exchange và dialog
xác nhận xử lý quyền/chờ đủ trước lần duyệt lại, có kiểm tra Retry-After.

### 1. Chặn lần POST thứ hai ở tầng HTTP — P1

- [x] Trong `LinkedInSocialConnector`, dùng JSON request body có `isOneShot() = true` cho lệnh đăng. Repo đã có mẫu giữ nguyên content type, content length và cách ghi body trong `HttpTextProvider.jsonBody()`. Giữ cấu hình tắt redirect và retry hiện có.
- [x] Cho MockWebServer trả lần lượt 503 với `Retry-After: 0` rồi 201. Sau một lần duyệt, server phải đếm đúng **một POST**, ledger lưu `UNKNOWN/RECONCILE`, không có Post ID. Bấm lại hoặc mở app lại phải giữ yêu cầu đối soát. Chạy cả ca 201 bình thường để xác nhận vẫn lấy được Post ID. [Nhận xét P1](https://github.com/nigPeter2503/Social_Pilot/pull/77#discussion_r4578333248)

### 2. Bảo toàn danh tính của mỗi lần kết nối — P2

- [x] Sửa `EncryptedOAuthStore` trước khi sửa luồng thử lại sau reconnect. Với cùng channelId, lưu bền vững revision lớn nhất đã cấp và chỉ cấp revision cao hơn. Khi không thể xác định mốc an toàn sau hỏng dữ liệu, cấp channelId nội bộ mới. Giữ externalAccountId riêng để nhận diện tài khoản LinkedIn.
- [x] Khi nâng bản ghi v2/v3 còn đọc được, giữ danh tính và revision hiện có, khởi tạo mốc từ bản ghi đó. Ghi mốc và credential nhất quán, chỉ bỏ nguồn migration sau khi ghi thành công. Nếu buộc đổi channelId, xử lý các attempt đang chờ đối soát của cùng tài khoản để đổi ID không mở đường đăng trùng.
- [x] Test reload, hỏng bản ghi rồi OAuth lại, đổi A → B → A và lỗi ghi giữa migration. Cặp channelId/revision cũ phải bị từ chối khi lấy token mới. Cờ reauth từ phiên cũ không được vô hiệu hóa phiên mới. [Nhận xét revision](https://github.com/nigPeter2503/Social_Pilot/pull/77#discussion_r4578333263)

### 3. Giữ kết quả từ chối và cho phép duyệt lại đúng trường hợp — P2

- [x] Trong `LinkedInSpikePublisher`, `LinkedInPostVerification.previousResult()` và ViewModel, đọc lại được outcome, mã lỗi và hành động tiếp theo từ ledger, kể cả sau reload. Hiển thị hướng xử lý bằng resource VI/EN. Giữ được các record hiện có và xử lý payload hỏng theo hướng cần đối soát.

| Kết quả đã lưu | Hành vi khi người dùng quay lại |
|---|---|
| REJECTED do 401 | Hiện yêu cầu kết nối lại. Sau OAuth mới và duyệt lại nội dung, cho tạo một attempt mới. |
| REJECTED do 403 hoặc 429 | Hiện yêu cầu xử lý quyền hoặc chờ. Cho thử lại tường minh sau khi xử lý, tôn trọng Retry-After khi có. |
| UNKNOWN hoặc đang gửi | Giữ khóa theo kênh và phiên bản nội dung, yêu cầu đối soát. |
| PUBLISHED_URL_PENDING hoặc SUCCESS | Khôi phục Post ID và URL đã biết, giữ khóa chống đăng lại qua reconnect. |

- [x] Chuyển từ lần bị từ chối sang attempt mới trong một thao tác đặt chỗ có kiểm tra requestId cũ. Hai lần bấm nhanh chỉ một lần được gửi. Test từ 401 → lưu → reload → reconnect → duyệt lại → 201, kiểm số POST ở từng bước. Thêm ca 403/429 và sửa test hiện đang kỳ vọng lỗi đã biết biến thành UNKNOWN. [Nhận xét ledger](https://github.com/nigPeter2503/Social_Pilot/pull/77#discussion_r4578333250)

### 4. Gắn xác nhận URL với đúng lần đăng — P2

- [x] Khi mở dialog, Activity lấy khóa attempt, requestId và Post ID từ kết quả đang hiển thị. Truyền chúng cùng URL qua ViewModel. Trong cùng khóa đồng bộ, publisher đọc lại bản ghi đích, kiểm tra danh tính và trạng thái rồi mới ghi. Nếu bản ghi đã bị thay thế, trả thông báo để người dùng mở lại bài. Chỉ cập nhật phần hiển thị mới nhất nếu vẫn trỏ đúng attempt.
- [x] Test theo thứ tự xác định: mở dialog cho A, cho B đăng xong, rồi xác nhận A. B phải giữ nguyên Post ID, URL và trạng thái. Xác nhận lại cùng URL cho A phải an toàn sau reload. [Nhận xét xác nhận nhầm attempt](https://github.com/nigPeter2503/Social_Pilot/pull/77#discussion_r4578333253)

### 5. Chặn URL tự mâu thuẫn với Post ID — P2

- [x] Trong `ownerConfirmed()`, sau kiểm tra HTTPS/host/path, lấy URN từ URL feed/update. Nếu URN cùng loại share hoặc ugcPost với Post ID đã lưu, yêu cầu khớp toàn bộ URN. Giữ bước người dùng mở và xác nhận cho activity URL và /posts/ khi thiếu ánh xạ từ provider.
- [x] Test share:123 với share:456 và cặp ugcPost tương tự. Cả hai phải báo lỗi, giữ PUBLISHED_URL_PENDING và ID cũ. Test URL cùng URN, activity URL và /posts/ hợp lệ vẫn lưu OWNER_CONFIRMED. Có thể làm bước này độc lập với bước 4, sau đó kiểm tra chung qua dialog. [Nhận xét URN](https://github.com/nigPeter2503/Social_Pilot/pull/77#discussion_r4578333258)

### 6. Xử lý đủ callback OAuth khi hai phiên về gần nhau — P2

- [x] Trong `OAuthCoordinator`, tiếp nhận callback theo sessionId và điều phối exchange theo phiên. MainActivity chỉ đánh dấu Intent đã tiếp nhận sau khi coordinator nhận trách nhiệm xử lý. Mỗi phiên hợp lệ phải có kết quả thành công hoặc lỗi về ViewModel. Kiểm tra lại TTL, provider và trạng thái pending tại lúc exchange. Callback lặp chỉ được exchange một lần.
- [x] Dùng hai pending Facebook/LinkedIn và chặn exchange thứ nhất bằng latch. Gửi callback thứ hai trong lúc thứ nhất đang chạy rồi nhả latch. Kiểm tra đúng hai exchange, đúng verifier/provider từng phiên, cả hai kết quả đến UI và không còn trạng thái bận. Lặp ca một callback hết hạn và ca callback trùng. Chạy lại đường Intent trên emulator hoặc thiết bị với provider giả. [Nhận xét callback](https://github.com/nigPeter2503/Social_Pilot/pull/77#discussion_r4578333260)

### 7. Kiểm tra bản tích hợp và bàn giao review

- [ ] Với từng bước, chuyển ca tái hiện thành test khẳng định hành vi mong muốn. Ghi nhận test thất bại ở bản cũ và đạt ở bản sửa. Dùng MockWebServer cho tầng HTTP, Keystore trên Android cho migration và fixture có kiểm soát thứ tự cho hai ca đồng thời.
- [x] Tại thư mục Android, chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, sau đó `.\gradlew.bat :app:connectedDebugAndroidTest` trên thiết bị thử. Ghi revision, máy/API, lệnh, số ca đạt và ca chưa chạy. Khi cập nhật dependency làm đổi backend, chạy `.\gradlew.bat test bootJar` tại backend.
- [x] Cập nhật [handoff](../sp-011-handoff.md) bằng kết quả mới. Giữ nguồn evidence bài Android đã đăng và giới hạn refresh của cấu hình đang thử. Sáu ca sửa có thể kiểm chứng bằng fixture và thiết bị mà không cần tạo thêm bài công khai.
- [ ] Khi cập nhật PR, sửa câu “Giữ Draft” cho khớp trạng thái thực tế. Gắn mỗi thread với commit sửa và tên test. Sau khi [SP-010/#74](https://github.com/nigPeter2503/Social_Pilot/pull/74) vào main, rà diff riêng SP-011, chạy lại checks bị ảnh hưởng và lấy hai approval trên bản cuối trước squash merge.

### Kết quả đối chiếu từng nhận xét

| Lỗi | Regression sau sửa |
|---|---|
| P1 POST lặp | unavailableDoesNotRepeatApprovedPost; một POST và UNKNOWN |
| Revision phục hồi/tái sử dụng | recoveryKeepsRevisionAndSwapBackAdvancesIt, lostMarkerAndCredentialCreateNewIdentity, existingV3SeedsMarkerWithoutChangingChannel, markerWriteFailurePreservesExistingV3ForRecovery; instrumentation recoveryRetainsRevisionAndLostMarkerChangesChannel |
| REJECTED và duyệt lại | authenticationFailureReloadReconnectAndReviewSendsNewAttempt, approvedPermissionRetryCanBeUsedOnlyOnce, rateDeadlineSurvivesReloadAndPreventsHttp |
| Xác nhận nhầm attempt | confirmationCapturedForAKeepsNewerBUnchanged; xác nhận lặp và thay record |
| URN mâu thuẫn | conflictingSameTypeUrnIsRejected; share và ugcPost |
| Callback bị mất | bothProvidersExchangeSeriallyAndDuplicateIsConsumedOnce, expiryIsCheckedAgainWhenQueuedWorkStarts; instrumentation twoProviderCallbacksThroughIntentsBothConnect |
| Giữ dedupe qua recovery/migration | recoveredChannelCannotBypassPendingPost, legacyAndMalformedRecordsRemainReserved, legacyRejectionCannotHidePublishedAttemptForSameAccountAndContent |

Ca P1 và URN đã được chạy thất bại trước sửa. Các ca ledger, URL/attempt và revision
có tái hiện độc lập trên a3858c7 trong lượt đánh giá review. Callback trước sửa được
xác nhận bằng luồng code, sau sửa có cả JVM và đường Intent trên máy thật. Do đó ô
đối chiếu RED theo từng bước ở mục 7 còn mở, không trình bày mọi test mới đều đã chạy
trên baseline nguyên bản. Source production của lượt sửa đã qua toàn bộ gate nêu trên.

Hai lần chạy instrumentation đầu có lỗi fixture cleanup do ActivityScenario đối
chiếu Intent gốc với Intent callback đã được Activity thay thế. Fixture hiện khôi
phục launch Intent lúc cleanup, giữ assertions callback và chạy đạt 7/7 ở lượt cuối.

Mục PR/review ở bước 7 còn chờ. GitHub đọc lại ngày 15/11 báo #77 Ready for review,
head a3858c7, #74 OPEN/CHANGES_REQUESTED. Bản mô tả PR local đã cập nhật câu trạng
thái và bảng kết quả mới. Owner đã yêu cầu push ngày 15/11. Code/tests ở commit 26b594b, hồ sơ trong
commit đi kèm. Cập nhật nội dung PR và hai reviewer xác nhận bản sửa, cùng xử lý
dependency, là các bước bàn giao còn theo dõi.
