# feat(social): verify LinkedIn OAuth and text publishing on device [SP-011]

Refs #13. Yêu cầu liên quan F1.B1, F1.B3, F5.B1, F5.B5.

Bản sửa credential recovery được kiểm chứng ở commit a69b8db. Hồ sơ audit và
kết quả 98 JVM / 8 device / 57 backend tests nằm trong commit tài liệu đi kèm.

## Kết quả

SocialPilot kết nối tài khoản LinkedIn PERSON qua backend OAuth dùng chung với
Facebook và đăng bài chữ sau khi người dùng duyệt. Phép thử trên SM-G990E / Android
16 đã nhận đúng tài khoản, tạo bài thật và lưu Post ID/URL sau mở lại theo owner.

Backend giữ client secret, ràng buộc provider/session/state và handoff dùng một lần.
Android lưu pending/connected riêng theo provider bằng Keystore. Scope token thực
quan sát được là openid profile w_member_social. API version app thử là 202609.

## Hành vi sau sửa review

- Request body one-shot chặn HTTP follow-up cho POST. Ca 503 với Retry-After: 0
  giữ một request và UNKNOWN/RECONCILE.
- Ledger đặt chỗ trước gửi theo tài khoản và nội dung đã duyệt. Giữ mã lỗi và
  nextAction qua reload. Duyệt lại 401 cần OAuth mới, 403/429 cần xác nhận xử lý
  quyền/chờ đủ và tôn trọng Retry-After. UNKNOWN/đã đăng tiếp tục giữ khóa qua
  reconnect và phục hồi channelId.
- Dialog xác nhận giữ attempt key/requestId/Post ID. Xác nhận A trả đúng State và
  target A, giữ nguyên bản ghi cùng con trỏ B. URN cùng loại mâu thuẫn bị từ chối.
- Revision marker lưu riêng theo tài khoản. Mất/hỏng v3 khi còn marker yêu cầu
  OAuth mới và loại nguồn token v2 cũ. Sau OAuth mới, giữ channelId và tăng revision.
  Nâng cấp v2 lần đầu vẫn có đường migration hợp lệ. Ghi credential/marker/dọn
  nguồn cũ nguyên tử và khôi phục snapshot khi commit thất bại.
- Callback OAuth xếp hàng theo sessionId, kiểm tra lại pending/TTL trước exchange.
  Callback lặp chỉ exchange một lần. Đã thử qua Activity Intent trên máy thật.

## Kiểm chứng trước push ngày 15/11/2026

| Phạm vi | Kết quả |
|---|---|
| Android assembleDebug/testDebugUnitTest/lintDebug | PASS, 98 JVM tests, 0 failures/errors/skipped; lint 0 errors/6 warnings |
| connectedDebugAndroidTest trên SM-G990E / Android 16 | PASS, 8 tests, 0 failures/errors/skipped; fake records/API và Keystore thật |
| Backend test/bootJar | PASS, chạy lại 57 tests, 0 failures/errors/skipped |
| Conflict | Mô phỏng merge main 7def84d cùng các sửa local, không có conflict |
| Evidence/tài liệu | 11 ảnh Postman và 3 ảnh owner khớp hash; liên kết local và diff --check đạt |

Lint thêm ApplySharedPref tại rollback ghi đồng bộ có chủ đích. Các regression
credential recovery đã chạy RED trước sửa. Report audit ghi revision, lệnh,
phạm vi và hash source đã kiểm tra.

Bài Android có Post ID urn:li:share:7527386113993412608 và
[link chia sẻ do owner cung cấp](https://lnkd.in/p/gaC6fpu8).
SUCCESS dùng provenance OWNER_CONFIRMED sau khi owner mở/đối chiếu URL.
Bài Postman có ID riêng trong hồ sơ. Harness giới hạn một account/provider và đăng
chữ; editor, queue và approval production tiếp tục theo các task tương ứng.

## Giới hạn và dependency

App/product đang thử không nhận refresh token. Metadata TTL là 5183999 giây,
gần 60 ngày. Programmatic refresh giữ BLOCKED/đạt một phần của F1.B3. Reconnect
được kiểm chứng như OAuth mới. Thu hồi/hết hạn trên provider thật và cold start
giữa OAuth chưa có phép thử thật riêng cho bản này. Hai provider thật cùng kết
nối chưa được kiểm chứng trực tiếp; fixtures kiểm tra cách ly và callback.
Comment/metrics/follower được bàn giao để SP-019 kiểm chứng quyền riêng.

PR đang Ready for review. Dependency SP-010/#74 vẫn OPEN/CHANGES_REQUESTED tại lúc
audit. Nhánh đã tích hợp dependency cục bộ từ mốc 15875c4, cần rà lại diff riêng
SP-011 sau khi #74 vào main. Main có thêm một commit SP-016, mô phỏng merge sạch.
Hai reviewer Đạt (dat532006) và Nghị (bob35306) cần xác nhận bản sửa cuối trước merge.
Issue #13 tiếp tục In Progress trong khi hoàn tất các điều kiện nghiệm thu/review.

## Hồ sơ bàn giao

- [Audit trước push](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/sp-011-pre-push-audit.md).
- [Kết quả audit và hash source/report](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/evidence/sp-011-pre-push-audit-2026-11-15.json).
- [Quyền, token, ID/URL và bàn giao](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/sp-011-handoff.md).
- [Báo cáo triển khai](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/sp-011-implementation.md).
- [Evidence thiết bị](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/evidence/sp-011-device-2026-11-14.md) và [Postman](https://github.com/nigPeter2503/Social_Pilot/blob/feat/sp-011-linkedin-oauth-spike/docs/evidence/sp-011-postman-verification.md).

Đạt review engine/kho bảo mật/migration. Nghị nhận PERSON/author URN cùng metadata
quyền để tiếp tục SP-019. DTO OAuth và schema Room giữ hợp đồng hiện tại.
