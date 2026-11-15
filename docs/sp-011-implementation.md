# SP-011 — Triển khai OAuth LinkedIn và đăng chữ thử nghiệm

Ngày lập: 14/11/2026, Asia/Bangkok. Owner: Minh (`nigPeter2503`).
Ticket: [#13](https://github.com/nigPeter2503/Social_Pilot/issues/13).
Reviewer theo brief: Đạt (`dat532006`), Nghị (`bob35306`).
Yêu cầu: F1.B1, F1.B3, F5.B1, F5.B5.

## Trạng thái và dependency

Cập nhật 15/11/2026: OAuth LinkedIn PERSON và đăng bài qua app đã thành công trên
thiết bị thật; owner đã xác nhận giữ kết nối/kết quả sau mở lại. Hồ sơ hiện tại ở
[bản tổng hợp nghiệm thu và bàn giao](sp-011-handoff.md), liên kết evidence Postman,
thiết bị và các giới hạn refresh. Đây là hoàn thiện local để review, chưa đóng ticket.

Worktree: `D:/MobileProject/SocialPilot_Starter/SocialPilot/build/sp011`.
Nhánh `feat/sp-011-linkedin-oauth-spike`; baseline đã push `53435d8`,
Draft PR [#77](https://github.com/nigPeter2503/Social_Pilot/pull/77).
Bản sửa code/tests đã commit local `9df46d3`; hồ sơ được gom trong commit docs
đi kèm theo ủy quyền ngày 15/11. Chưa push hoặc cập nhật PR.

Nhánh tạo từ origin/main `7582fb3`, tích hợp SP-010 cục bộ bằng merge. SP-010 PR
[#74](https://github.com/nigPeter2503/Social_Pilot/pull/74) được ghi nhận trước đây
OPEN/CHANGES_REQUESTED tại `d1d55a0`; chưa truy vấn lại GitHub ngày 15/11.
Cần đối chiếu dependency/main và diff riêng SP-011 trước merge. Hai human approvals
chưa được xác nhận; giữ nguyên checkout SP-009/SP-010 của người khác.

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

## Kiểm tra và evidence hiện tại

Môi trường Windows, JDK 21, hai Gradle Wrapper độc lập. Tests dùng credentials giả.
Kết quả và hash XML được chốt trong
[bản tổng hợp test](evidence/sp-011-validation-summary.json).

- Backend: 57 tests PASS, bootJar PASS theo lần chạy đã ghi nhận trước đó.
- Android: chạy lại assembleDebug/testDebugUnitTest/lintDebug ngày 15/11 sau bổ sung
  test token hết hạn và siết assertions HTTP 401: **74 JVM tests PASS**,
  assembleDebug/lintDebug PASS, lint 0 errors/5 warnings.
- Thiết bị: 5 instrumentation tests PASS trên SM-G990E / Android 16 theo XML đã có;
  Keystore, migration và cách ly provider dùng dữ liệu giả.
- API thật: [Postman](evidence/sp-011-postman-verification.md) và
  [Android/thiết bị](evidence/sp-011-device-2026-11-14.md). Hai bài có ID riêng.

Không dùng hash artifact của bản build trước khi trỏ tunnel để mô tả APK đã thử.
Build cho thiết bị dùng `socialpilotOauthApiBaseUrl` trỏ tunnel đã cung cấp và
`socialpilotLinkedinApiVersion=202609`; URL tunnel là cấu hình thử tạm thời.

## Bản sửa sau baseline và giới hạn bàn giao

- Parser scope backend nhận dấu phẩy/khoảng trắng, vẫn từ chối thiếu quyền.
- Bấm publish lặp giữ Post ID/kết quả đã biết; không tự POST lại cùng snapshot.
- Xác nhận URL đầy đủ /posts hoặc /feed/update có feedback và tính idempotent;
  short link cần mở để lấy URL đầy đủ. URL có provenance OWNER_CONFIRMED.
- Test expiry quá khứ sau reload chặn token cũ, giữ Facebook, reconnect tăng revision;
  test 401 yêu cầu RECONNECT, lưu kết quả và không gửi lại POST.
- Không nhận refresh token trong phép thử app cơ bản này; chưa có refresh grant
  được nghiệm thu. Reconnect không thay refresh; F1.B3 giữ giới hạn/đạt một phần.
- Thu hồi quyền/hết hạn thật chưa thử; không yêu cầu owner thu thêm evidence thủ
  công trong đợt bàn giao này. Tests mô phỏng không thay PASS của provider thật.
- Cold start giữa OAuth và hai provider thật cùng kết nối chưa thử trực tiếp;
  là phần hồi quy của contract mở rộng, không trình bày như tính năng mới của spike.
- Snapshot harness không thay editor/queue/approval production; một account/provider,
  backend một instance/session trong memory. Không đổi Room, không triển khai mọi API.

Đầu mối bàn giao, bảng quyền/version và trình tự kết thúc quy trình nằm trong
[SP-011 handoff](sp-011-handoff.md). Code/tests có commit local `9df46d3`; chưa push, cập nhật PR hoặc merge.
