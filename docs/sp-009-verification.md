# SP-009 — Kiểm chứng và bàn giao hợp đồng

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
