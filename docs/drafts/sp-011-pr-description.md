# feat(social): verify LinkedIn OAuth and text publishing on device [SP-011]

Refs #13. Yêu cầu liên quan: F1.B1, F1.B3, F5.B1, F5.B5.

## Kết quả và phạm vi

SocialPilot kết nối tài khoản LinkedIn cá nhân qua backend OAuth dùng chung với
Facebook, lưu kết nối độc lập bằng Android Keystore và đăng bài chữ sau khi người
dùng duyệt. Luồng đã được thử trên Samsung SM-G990E / Android 16: đúng tài khoản,
đăng bài thật, lưu Post ID/URL và giữ kết quả sau force-stop/mở lại theo owner.

- Backend dùng provider adapter, state/handoff ràng buộc phiên và dùng một lần;
  LinkedIn exchange authorization code rồi gọi UserInfo, giữ client secret ở server.
  Parser nhận scope phân tách bằng dấu phẩy/khoảng trắng và vẫn từ chối thiếu quyền.
- Android giữ riêng pending/connected Facebook và LinkedIn; migration v2 Facebook
  sang v3 chỉ xóa nguồn sau ghi thành công. DTO hiện có giữ nguyên, không đổi Room.
- Harness yêu cầu duyệt bài công khai, lưu attempt trước khi gửi và không tự retry
  khi kết quả chưa rõ. Bấm lại cùng snapshot giữ ID/kết quả đã biết, không POST lại.
- HTTP 201 có ID giữ PUBLISHED_URL_PENDING. Người dùng mở bài, dán URL đầy đủ
  /posts hoặc /feed/update và xác nhận đúng bài để lưu SUCCESS/OWNER_CONFIRMED.
  Có thông báo lỗi/thành công VI/EN; xác nhận lại cùng URL có tính idempotent.
- Token hết hạn bị chặn trước gửi; HTTP 401 chuyển AUTH_REQUIRED/RECONNECT,
  HTTP 403 giữ PERMISSION_REQUIRED. Reconnect tăng connection revision.

Đây là spike PERSON/đăng chữ, chưa thay editor, queue hoặc approval production;
không triển khai mọi API LinkedIn hoặc nghiệm thu toàn bộ F1/F5.

## Kiểm chứng

| Phạm vi | Kết quả |
|---|---|
| Backend `test bootJar` | 57 tests PASS, 0 failures/errors/skipped; theo lần chạy đã ghi nhận |
| Android `assembleDebug testDebugUnitTest lintDebug` | Chạy lại 15/11: 74 JVM tests PASS, 0 failures/errors/skipped; lint 0 errors, 5 warnings |
| Instrumentation trên SM-G990E / Android 16 | 5 tests PASS: Keystore, dữ liệu hỏng, cách ly provider/pending, migration v2/v3, cờ reauth; fixtures giả |
| OAuth và đăng bài thật qua app | Ảnh connected/SUCCESS; Post ID `urn:li:share:7527386113993412608`; URL do owner mở và xác nhận |
| Restart backend | Phiên cũ HTTP 400 INVALID_STATE; OAuth mới thành công theo owner ngày 15/11 |
| Tài liệu | Liên kết tương đối, hash evidence và `git diff --check` đạt |

Tests tự động dùng credentials giả. Bổ sung ca expiry quá khứ qua reload/reconnect,
HTTP 401 không tự gửi lại, scope delimiter, publish lặp và URL idempotency.
Không dùng tests mô phỏng thay bằng chứng provider thật.

API version app thử: `202609`. Scope token quan sát được:
`openid profile w_member_social`. Bài Postman và bài Android có ID/evidence riêng.
URL SUCCESS có provenance OWNER_CONFIRMED, không phải API tự đọc lại bài.

## Refresh và giới hạn còn lại

App/product đang thử không nhận refresh token; TTL metadata `5183999` giây,
gần 60 ngày. Programmatic refresh không được nghiệm thu và giữ BLOCKED/đạt một
phần của F1.B3; kết nối lại là OAuth mới, không coi là refresh PASS.

Owner không yêu cầu thêm phép thử thu hồi quyền thật trong đợt bàn giao này.
Hết hạn/thu hồi trên provider thật, cold start giữa OAuth và hai tài khoản thật
Facebook–LinkedIn cùng kết nối chưa được kiểm chứng trực tiếp cho SP-011.
Xử lý expiry/401 và cách ly provider đã có tests mô phỏng/Keystore tương ứng.
Không suy quyền comment, metrics hoặc follower từ việc đăng bài thành công.

## Dependency và review

Phụ thuộc SP-010 PR #74; nhánh đã merge dependency cục bộ từ baseline `7582fb3`.
Cần đối chiếu trạng thái #74/main, cập nhật và rà diff riêng SP-011 trước merge;
không dùng PR này để đưa dependency chưa được duyệt vào main.

Giữ Draft trong lúc chờ tích hợp dependency và review. Hai reviewer theo brief:
Đạt (`dat532006`), Nghị (`bob35306`); chưa xác nhận đủ hai human approvals.
Không đóng Issue #13 hoặc merge chỉ dựa trên kết quả spike này.

## Tài liệu và bàn giao

- [Tổng hợp quyền, token, ID/URL và bàn giao](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/sp-011-handoff.md).
- [Báo cáo triển khai](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/sp-011-implementation.md), [hướng dẫn chạy](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/development.md#linkedin-oauth-và-đăng-chữ-thử-nghiệm-sp-011).
- [Evidence thiết bị và ảnh UI](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/evidence/sp-011-device-2026-11-14.md), [evidence Postman](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/evidence/sp-011-postman-verification.md).
- [Kết quả tests và hash evidence](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/evidence/sp-011-validation-summary.json).

Đạt review engine/kho bảo mật/migration; Nghị nhận PERSON/author URN và metadata
quyền để tiếp tục SP-019. Không đưa token/secret vào mô tả hoặc tài liệu.
