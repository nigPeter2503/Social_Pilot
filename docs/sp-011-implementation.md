> Cập nhật 17/11/2026: bản sửa provenance và tích hợp main nằm trong [báo cáo hiện hành](sp-011-final-integration.md). Các head, kết quả test và trạng thái dependency dưới đây là lịch sử ngày 15/11.

# SP-011 — Triển khai OAuth LinkedIn và đăng chữ thử nghiệm

Ngày lập: 14/11/2026, Asia/Bangkok. Owner: Minh (`nigPeter2503`).
Ticket: [#13](https://github.com/nigPeter2503/Social_Pilot/issues/13).
Reviewer theo brief: Đạt (`dat532006`), Nghị (`bob35306`).
Yêu cầu: F1.B1, F1.B3, F5.B1, F5.B5.

## Trạng thái và dependency

Đối chiếu trước push ngày 15/11/2026 tại worktree
D:/MobileProject/SocialPilot_Starter/SocialPilot/build/sp011.
Nhánh feat/sp-011-linkedin-oauth-spike có head đã push 97e9dad, PR
[#77](https://github.com/nigPeter2503/Social_Pilot/pull/77) Ready for review.
Sau audit, owner đã yêu cầu push. Source credential recovery được kiểm chứng và
commit ở a69b8db; hồ sơ audit trong commit tài liệu đi kèm. Bằng chứng hiện tại
và kết quả audit ở [báo cáo trước push](sp-011-pre-push-audit.md).

OAuth PERSON và bài thử qua app đã có evidence thật. Bản local đạt 98 JVM tests,
8 tests thiết bị, 57 backend tests được chạy lại trong audit. Build/lint đạt,
lint 0 errors/6 warnings. Các lần chạy cũ được giữ trong report theo revision.

Issue #13 OPEN, owner Minh, Project In Progress. PR SP-010
[#74](https://github.com/nigPeter2503/Social_Pilot/pull/74) vẫn OPEN/CHANGES_REQUESTED
ở d1d55a0. Chưa có hai human approvals. Main 7def84d có thêm SP-016, mô phỏng merge
cùng các sửa local không có conflict tại lúc kiểm tra. Cần kiểm tra lại diff và
các gates bị ảnh hưởng sau khi SP-010 được tích hợp.

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

Kết quả mới nhất ở [audit trước push](sp-011-pre-push-audit.md) và
[report JSON](evidence/sp-011-pre-push-audit-2026-11-15.json).
Các tests JVM, backend và instrumentation đã thực thi lại trong audit.
Build APK, bootJar và lint đạt. API version build thiết bị là 202609, base URL
HTTPS tunnel theo cấu hình đã dùng khi thử.

- [Postman](evidence/sp-011-postman-verification.md) chứng minh phép gọi API riêng.
- [Thiết bị và bài Android](evidence/sp-011-device-2026-11-14.md) giữ evidence chủ sở hữu cung cấp.
- [Report sửa review đầu](evidence/sp-011-review-fixes-2026-11-15.json) ghi snapshot 93/7.
- [Report review thứ hai](evidence/sp-011-second-review-2026-11-15.json) ghi snapshot 96/8.
- [Report credential recovery](evidence/sp-011-credential-recovery-2026-11-15.json) ghi snapshot 98/8 trước audit.

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
[SP-011 handoff](sp-011-handoff.md). Head đã push là 97e9dad. Bản credential recovery được commit ở a69b8db theo ủy quyền push sau audit.
