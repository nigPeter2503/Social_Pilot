# SP-011 — Triển khai OAuth LinkedIn và đăng chữ thử nghiệm

Ngày lập: 14/11/2026, Asia/Bangkok. Owner: Minh (`nigPeter2503`).
Ticket: [#13](https://github.com/nigPeter2503/Social_Pilot/issues/13).
Reviewer theo brief: Đạt (`dat532006`), Nghị (`bob35306`).
Yêu cầu: F1.B1, F1.B3, F5.B1, F5.B5.

## Trạng thái và dependency

Đã triển khai code và chạy gate local; **chưa nghiệm thu end-to-end hoặc hoàn tất ticket**.
Issue #13 đã được đọc trực tiếp: OPEN, assignee Minh, Project In Progress.
SP-010 PR [#74](https://github.com/nigPeter2503/Social_Pilot/pull/74) tại head
`d1d55a0` vẫn OPEN/CHANGES_REQUESTED, chưa merge tại lúc đọc.
Không có human approval mới được xác nhận trong lượt này.

Worktree SP-011: `D:/MobileProject/SocialPilot_Starter/SocialPilot/build/sp011`.
Nhánh: `feat/sp-011-linkedin-oauth-spike`, tạo từ origin/main `7582fb3`, tích hợp
nhánh dependency SP-010 cục bộ bằng merge để giữ lịch sử. Các checkout SP-009 và
SP-010 hiện có được giữ nguyên. PR SP-011 là draft phụ thuộc #74, chưa merge-ready;
phải cập nhật từ main và rà diff riêng SP-011 sau khi #74 được tích hợp/squash.
Không merge SP-011 để đưa dependency chưa được duyệt vào main.

## Thay đổi

- Backend OAuthSessionService dùng registry OAuthProviderAdapter; phiên gắn provider,
  state dùng một lần, session 10 phút, handoff 60 giây và consume nguyên tử.
  Sai provider không consume state/handoff; giữ route Facebook hiện có.
- Facebook adapter bọc Graph client cũ. LinkedIn adapter dùng authorization code,
  POST form token endpoint và UserInfo; giữ secret ở backend, từ chối redirect HTTP
  tự động, có connect/read timeout, trả PERSON và expiry từ expires_in nếu có.
- Wire DTO SP-009 giữ nguyên. Cấu hình enable mỗi provider độc lập; bật nhưng sai
  config bị fail-fast, provider tắt không cản provider còn lại. Health trả trạng thái riêng.
- Android dùng coordinator chung, kiểm tra host/path authorization URL và ràng buộc
  sessionId/provider/accountType khi exchange. Pending được mã hóa theo provider;
  callback chọn pending bằng sessionId, không tin nhãn provider từ deep link.
- Kho AES-GCM/Keystore nâng record v2 Facebook lên v3, mã hóa lại theo AAD provider;
  chỉ xóa v2 sau khi v3 ghi thành công. Facebook/LinkedIn có bản ghi độc lập.
  Token chỉ được trả cho đúng channel/revision và chưa cần reauth. Kho production
  dùng chung một instance; getter cũ tiếp tục chỉ Facebook, không chọn token cuối cùng.
- Reconnect cùng account tăng revision. Thay account âm thầm bị chặn; API kho có
  overload yêu cầu caller xác nhận rõ, luồng UI spike hiện chưa hỗ trợ đổi account.
- LinkedInSocialConnector nhận snapshot bất biến đã duyệt và attempt ledger;
  POST text ngoài main thread. Hủy HTTP chặn callback muộn, kết quả chưa rõ được giữ
  UNKNOWN/RECONCILE. 201 chỉ có ID giữ PUBLISHED_URL_PENDING.
- Harness Android có dialog duyệt bài công khai; hash text làm approvedVersionId
  của riêng phép thử. Attempt được ghi trước gửi; cùng snapshot/kênh không POST lại,
  kể cả app restart. Đây chưa thay editor, PublishQueue hoặc chống trùng production.
- Người dùng tự mở bài LinkedIn, dán link và xác nhận đúng tác giả/nội dung. Validator
  chỉ chấp nhận URL bài HTTPS của LinkedIn cho kết quả đang có post ID. SUCCESS ghi
  `OWNER_CONFIRMED`; không trình bày như API tự đọc và xác minh bài.
- UI VI/EN, trạng thái hai kết nối, đọc summary mã hóa và chuẩn bị publish ngoài UI
  thread; chặn connect/publish đồng thời trong harness. ViewModel hủy publish khi clear.

Vị trí chính:

- [Engine](../backend/src/main/java/com/socialpilot/oauth/flow/OAuthSessionService.java)
- [LinkedIn OAuth](../backend/src/main/java/com/socialpilot/oauth/linkedin/LinkedInOAuthClient.java)
- [Coordinator](../android/app/src/main/java/com/socialpilot/app/core/OAuthCoordinator.java)
- [Kho bảo mật](../android/app/src/main/java/com/socialpilot/app/data/security/EncryptedOAuthStore.java)
- [Connector](../android/app/src/main/java/com/socialpilot/app/data/repository/social/LinkedInSocialConnector.java)
- [Harness](../android/app/src/main/java/com/socialpilot/app/data/repository/social/LinkedInSpikePublisher.java)
- [Hướng dẫn chạy](development.md#linkedin-oauth-và-đăng-chữ-thử-nghiệm-sp-011)
- [Bằng chứng Postman](evidence/sp-011-postman-verification.md)

## Kiểm tra local đã chạy

Môi trường: Windows, Oracle JDK 21.0.7, Gradle Wrapper 8.13, SDK 36.
Credentials trong automated tests đều là dữ liệu giả; MockRestServiceServer/MockWebServer
không gọi provider thật. Hai wrapper chạy với `--offline --console=plain`.

| Gate | Kết quả |
|---|---|
| Backend `test bootJar` | PASS — 54 tests, 0 failures/errors/skipped |
| Android `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | PASS — 69 JVM tests, 0 failures/errors/skipped; lint 0 errors, 5 warnings |
| Android `:app:assembleDebugAndroidTest` | PASS — build APK instrumentation, không có nghĩa đã chạy trên thiết bị |
| Diff và liên kết | PASS — diff --check, liên kết tương đối và hash PNG trong manifest |

Test backend gồm hồi quy SP-010, chéo provider không consume phiên, LinkedIn token/
UserInfo, scope/expiry sai, lỗi không chứa credentials, cấu hình LinkedIn không cần
Facebook và route MockMvc. Test Android gồm kho v2/v3, failed encryption/commit/
restart, hai provider/ciphertext swap, revision/reauth, provider URL/response binding,
approval/revision trước POST, 201 thiếu ID, 401/403/429/5xx, timeout, duplicate/cancel,
và không cho UNKNOWN trở thành SUCCESS chỉ bằng việc dán URL.

Đã sửa lỗi test kỳ vọng thay account âm thầm bằng lời gọi xác nhận phù hợp, giữ ca
failed-commit và bảo toàn account/token cũ. Lỗi lint đường dẫn SDK local đã được
sửa trong local.properties không theo dõi Git; không thêm baseline để bỏ qua lỗi.
Cảnh báo hiện tại: ApplySharedPref (commit đồng bộ có chủ đích), UnusedResources,
Autofill; Gradle deprecated features và JVM class-sharing warning không làm gate lỗi.

Artifact build local (không commit):
- `android/app/build/outputs/apk/debug/app-debug.apk` — SHA-256 `92ba05d8e39cae4dffb996a3da4dc5c737dea16d959a97a89ab3f73169c6a0b3`
- `backend/build/libs/socialpilot-backend-0.1.0.jar` — SHA-256 `1cd24f5f84a9ff36b4313eecbdf9cc600f60315cf2c66541a16e3e5f5b61486f`

APK được build với base URL mặc định và version trống nếu chưa cung cấp cấu hình;
đây là artifact kiểm tra build, **không phải bản đã trỏ tunnel của nhóm**. Build lại
với base URL/version trước cài để thử thật. Không đính token/secret trong artifact cấu hình.

## Phần chưa kiểm chứng và bàn giao tiếp

- ADB `devices` không có thiết bị; emulator `-list-avds` không có AVD. Chưa chạy
  instrumentation/Keystore thật, migration thật, screenshot UI hoặc callback process-death
  cho SP-011. Các bằng chứng SP-010 trước đó không thay lần chạy SP-011.
- Chưa cấu hình credentials/tunnel thật trong worktree này; chưa thực hiện OAuth,
  đăng bài hoặc xác minh URL qua app trong lượt triển khai. Bài Postman cũ được giữ
  làm đầu vào, không đăng lại và không tính thành tích hợp app đã PASS.
- Programmatic refresh, expiry/revocation thật chưa kiểm chứng; refreshToken có thể
  null và reauthorize không tính là refresh. F1.B3 giữ giới hạn/đạt một phần theo evidence.
- Snapshot text của harness không thay approval version của F4 production. Không
  đổi Room, không quyết định retention N1, không triển khai inbox/analytics/ảnh.
- Một account/provider trong spike, backend một instance và session trong memory.
  Đổi domain tunnel phải cập nhật callback provider, backend config và Android base URL.
- Đạt/Nghị cần review phần chung, hai human approvals trên bản cuối; SP-010 phải
  được tích hợp, CI áp dụng đạt và live evidence bổ sung trước kết luận hoàn tất.

Trình tự người thử: cấu hình theo development → build/install → kết nối LinkedIn
→ kiểm tra Facebook vẫn còn → thử cold start/restart backend → duyệt một nội dung
mới đã thống nhất → đăng đúng một lần → mở bài và xác nhận URL → thu evidence.
Khi UNKNOWN, kiểm tra bài trước mọi quyết định gửi mới; không xóa ledger để vượt khóa.
