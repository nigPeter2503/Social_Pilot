# SP-009 — Kiểm chứng và bàn giao hợp đồng

## Bản sửa sẵn sàng commit — 09/11/2026

Owner: Ngô Gia Minh; sửa tiếp trên `feat/sp-009-social-contract`, base `289e4d1`,
theo yêu cầu kiểm tra và sửa hai blocking của PR #66. Trạng thái cục bộ: đã sửa,
regression test và full Gradle local đạt; còn chờ CI và review. Chưa commit/push bản sửa.
GitHub không truy cập được trong lượt này; chưa cập nhật/xác minh Project hoặc approval.
Các ghi nhận ngày 07/11 bên dưới là lịch sử, không phải trạng thái PR hiện tại.

Minh đã xác nhận bản sửa và kết quả kiểm tra local, yêu cầu đồng bộ tài liệu.
Hai blocking đã có regression test và full Gradle gate đạt. Đủ điều kiện commit
bản sửa SP-009; commit không yêu cầu có sẵn hai approval hoặc CI trên commit mới.
Không đưa docs/drafts/ (SP-010/SP-011) vào commit này. Xác nhận của owner không
thay approval của Đạt/Nghị hoặc quyết định cụ thể về retention/reconnect của N1.

### Bằng chứng chạy trước và sau sửa

Môi trường: JDK 21.0.7, Spring Boot 3.5.16, Jackson 2.21.4; JUnit Jupiter 5.12.2
và JUnit 4.13.2. Biên dịch lại source bằng javac; chạy JUnit Platform Launcher
cho backend và JUnitCore cho Android contract. Dependency runtime backend lấy
từ BOOT-INF/lib của bootJar local đã có, dependency test từ cache Gradle;
không sử dụng class ứng dụng cũ trong bootJar. Spring context đọc application.yml
hiện tại. Đây là kiểm tra trực tiếp JVM, không thay thế full Gradle gate.

| Bộ test | Trước sửa | Sau sửa |
|---|---|---|
| Backend OAuthContractTest: 4 test cũ | 4 đạt | 4 đạt |
| OAuthJsonBoundaryTest: 7 ca khóa lặp trên 5 DTO | 7 thất bại vì không ném lỗi | 7 đạt |
| Android PublishContractTest + OAuthContractTest: 11 test cũ | 11 đạt | 11 đạt |
| ChannelCapabilityTest: 3 test mới | 2 đạt, 1 thất bại vì chấp nhận not-a-date | 3 đạt |
| Tổng test được chạy | 17 đạt, 8 thất bại / 25 | 25 đạt, 0 thất bại |

- Backend: từ chối provider sai, redirectId rỗng, challenge ngắn, URL HTTP,
  verifier ngắn, access token rỗng và error code sai khi chúng xuất hiện ở khóa lặp.
  Cả mapper Spring và JSON converter trong RequestMappingHandlerAdapter đều từ chối;
  payload hợp lệ vẫn đọc được. Test không thêm endpoint OAuth giả vào production.
- Capability: sau sửa kiểm tra 7 timestamp sai trên mỗi availability (28 tổ hợp),
  UTC hợp lệ và ngày nhuận trên mọi availability (8 tổ hợp), cùng quy tắc null
  trên 4 availability. Test cũ vẫn kiểm tra reasonCode/metadata và wire fixture.
- Bản sửa bật strict duplicate detection ở cấu hình ứng dụng; ObjectMapper tự tạo
  mặc định không được xem là boundary hợp lệ. Không đổi wire fields hoặc dependency.
- git diff --check: đạt.

### Full Gradle local đã đạt và phần còn chờ

Đã thử wrapper backend `test bootJar` và Android
`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` với `--offline --console=plain`.
Cache mặc định bị Access is denied ở wrapper zip lock. Với cache tạm có quyền ghi,
cả hai build dừng vì `Could not connect to the Gradle daemon`; thử thêm
`--no-daemon -Dorg.gradle.jvmargs=` vẫn gặp lỗi kết nối single-use daemon.
Đây là lỗi môi trường chạy agent ở lượt đầu, không phải kết quả gate cuối cùng.

Ngày 09/11/2026, Minh chạy lại hai lệnh dưới đây trong terminal và cung cấp log
BUILD SUCCESSFUL. Agent đã đọc lại XML test-results được tạo khoảng 14:39–14:40
(Asia/Bangkok) để đối chiếu số test và lỗi:

| Gate | Kết quả |
|---|---|
| Backend test bootJar | BUILD SUCCESSFUL, 6 giây; 13 test, 0 failures/errors |
| Android assembleDebug testDebugUnitTest lintDebug | BUILD SUCCESSFUL, 45 giây; 17 test, 0 failures/errors |
| Tổng test Gradle | 30 đạt, gồm 25 test contract và 5 test baseline |

Nguồn kiểm chứng: log terminal do Minh cung cấp; backend/build/test-results/test/TEST-*.xml
và android/app/build/test-results/testDebugUnitTest/TEST-*.xml đọc trực tiếp.
Kết quả này áp dụng working tree đã sửa hai blocking, không lấy từ build ngày 07/11.
Build có cảnh báo JVM class sharing và Gradle deprecated features; không gây thất bại
trong hai lượt chạy. Chưa xác minh CI trên GitHub.

```powershell
# Tại backend/
.\gradlew.bat test bootJar --console=plain
# Tại android/
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Chưa kiểm chứng API thật, thiết bị, state/replay/TTL enforcement hoặc 278 phép kiểm tra
riêng của reviewer. N1 vẫn mở: Minh tổng hợp với Đạt/Nghị, theo dõi trong SP-009/#11
và social-contract §7; chưa có quyết định hay cập nhật issue trực tuyến.
Có thể gửi bản sửa để review lại; chỉ merge khi CI áp dụng đạt, xử lý nhận xét bắt buộc
và đủ hai human approvals trên phiên bản cần merge.

## Hồ sơ triển khai ban đầu — 07/11/2026

Toàn bộ phần còn lại là lịch sử ngày 07/11. Các câu “chưa tạo PR”, trạng thái Project,
số test và xác nhận còn chờ bên dưới phải được đọc theo ngày đó; trạng thái cập nhật
và bằng chứng bản sửa nằm ở phần ngày 09/11 phía trên.

Ngày: 07/11/2026, Asia/Bangkok.

- Owner: Ngô Gia Minh. Thực hiện soạn code/test bằng Codex theo bản nháp người dùng đã duyệt.
- Ticket: [SP-009 / #11](https://github.com/nigPeter2503/Social_Pilot/issues/11).
- Branch: feat/sp-009-social-contract, tạo từ origin/main
  0348982c8f295d946815da91eae491bd9645ac94.
- Phạm vi bằng chứng: working tree SP-009 trong lượt triển khai này, chưa có commit/PR mới.
- Project #5: đã chuyển Todo → In Progress và đọc lại qua GitHub để xác nhận.
  Giữ In Progress; chưa đánh dấu Hoàn tất.

## Thay đổi và ảnh hưởng

[Hợp đồng social v1](social-contract.md) chuyển bản nháp thành DTO/interface Java và ví dụ wire.
PublishRequest, PublishResult, ChannelInfo, ChannelCapability và SocialError nằm ở Android.
OAuthContract có ở Android/backend, cùng kiểm tra một bộ JSON giả trong docs/contracts/.

SocialConnector thay canPublish()/publish(String, Result) bằng channel(), capability(operation)
và publish(PublishRequest, Result). Callback thống nhất onResult(PublishResult).
PublishQueue nhận PublishRequest và bổ sung cancelForConnection(channelId, revision).
Đã tìm nơi sử dụng: baseline chưa có implementation/call site cần chuyển đổi.
Các adapter mới phải theo chữ ký này; không tự tạo một phiên bản DTO khác.

Không đổi schema Room, phiên bản thư viện, UI hoặc endpoint đang hoạt động.
Chưa có migration, không yêu cầu thiết bị để kiểm tra riêng DTO thuần Java.

## Kết quả kiểm tra thực tế

Môi trường: Windows, JDK 21 tại C:/Program Files/Java/jdk-21, Gradle Wrapper 8.13,
Android compile SDK 36 theo build hiện hành. JAVA_HOME được đặt riêng cho tiến trình kiểm tra.

| Thư mục | Lệnh | Kết quả |
|---|---|---|
| android | .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain | BUILD SUCCESSFUL |
| backend | .\gradlew.bat test bootJar --console=plain | BUILD SUCCESSFUL |
| root | git diff --check | Đạt |

Android: 14 test đạt (3 baseline + 7 publish contract + 4 OAuth contract).
Backend: 6 test đạt (2 baseline controller + 4 OAuth contract).
Tổng: 20 test, 0 failures, 0 errors; 15 test mới.
Lint: 0 lỗi, 14 cảnh báo. Build cũng báo deprecated Gradle features; không nâng stack trong SP-009.

Báo cáo sinh trên máy (không commit):

- android/app/build/reports/tests/testDebugUnitTest/index.html
- android/app/build/reports/lint-results-debug.html
- backend/build/reports/tests/test/index.html

Lượt chạy đầu trong môi trường hạn chế không xuất log tiến triển và đã được dừng;
kết quả trên là lượt chạy lại hoàn tất với JDK 21 được chỉ định rõ.

## Bằng chứng hành vi

- Thành công thiếu ID/URL hoặc URL không phải HTTPS bị factory từ chối.
- UNKNOWN bắt buộc RECONCILE; không thể dùng lỗi này tạo kết quả REJECTED.
- Thiếu quyền và token hết hạn có hướng xử lý riêng.
- Bài có ID nhưng chưa có URL giữ PUBLISHED_URL_PENDING.
- Request thiếu version hoặc revision không hợp lệ bị từ chối.
- Channel DISCONNECTED không được giữ tokenReference.
- Capability AVAILABLE thiếu thời điểm kiểm chứng bị từ chối.
- Cùng năm ví dụ wire OAuth round-trip không mất trường ở Gson và Jackson.
- Payload thiếu ràng buộc phiên, URL không HTTPS, ngày không hợp lệ bị từ chối.
- toString của DTO nhạy cảm che dữ liệu. JSON fixture chứa giá trị giả có nhãn.

## Giới hạn và xác nhận còn chờ

Các test OAuth mới kiểm tra DTO/serialization, không kiểm chứng state, replay, TTL enforcement
hoặc đối soát thật. Chưa triển khai session store, adapter provider, kho token, queue,
hủy HTTP/callback race, OAuth browser trên thiết bị hoặc đăng bài thật.
StatusController vẫn trả 501 OAUTH_NOT_CONFIGURED.

- Đạt: review phiên, secret reference, lỗi, ownership và chính sách hồ sơ đối soát.
- Nghị: xác nhận capability đủ cho comment/metrics và dữ liệu cần xóa khi ngắt.
- Thiện: xác nhận approvedVersionId, hiệu lực duyệt và cách lấy snapshot nội dung.
- Cả hai reviewer chính: xác nhận challenge/verifier bàn giao app/backend và TTL 60 giây.
- SP-010: MockMvc/session tests cho state sai, phiên sai, hết hạn, replay đồng thời;
  HTTPS/callback thiết bị và bài đăng thật. SP-011 tái dùng cơ chế đã kiểm chứng.

Chưa gửi thông báo review, chưa push hoặc mở PR; không ghi nhận approval thay thành viên.
Khi tạo PR, dùng tiêu đề:

```text
feat(social): chốt hợp đồng OAuth và capability [SP-009]
```

PR liên kết Closes #11, yêu cầu Đạt (dat532006), Nghị (bob35306) review và
tham chiếu hợp đồng/báo cáo này. Chỉ Hoàn tất sau đủ xác nhận, checks, hai human approvals,
merge và cập nhật Project theo CONTRIBUTING.
