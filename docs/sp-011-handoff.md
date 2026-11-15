# SP-011 — Tổng hợp nghiệm thu và bàn giao

Cập nhật: 15/11/2026, Asia/Bangkok. Owner: Ngô Gia Minh (`nigPeter2503`).
Issue [#13](https://github.com/nigPeter2503/Social_Pilot/issues/13), PR
[#77](https://github.com/nigPeter2503/Social_Pilot/pull/77).
Reviewer theo brief: Đạt (`dat532006`), Nghị (`bob35306`).

## Bổ sung review head 908316f ngày 15/11

Hai P2 bổ sung đã được commit ở a61c71a theo ủy quyền push ngày 15/11. State trả về sau xác nhận lấy kết quả và target của
bài vừa xác nhận. Con trỏ bài mới nhất giữ nguyên. Migration v2 còn sót giữ mốc
revision hiện có, tăng revision khi phục hồi và ghi đích/marker/xóa nguồn cùng một
commit. Khi commit thất bại, khôi phục snapshot trước thao tác ghi.

96 JVM tests PASS, build/lint/instrumentation APK đạt. 8 instrumentation tests PASS
trên SM-G990E / Android 16. Lint 0 errors/6 warnings, thêm ApplySharedPref ở rollback
đồng bộ. Ba regression mới đã thất bại trước sửa. Xem
[report đợt hai](evidence/sp-011-second-review-2026-11-15.json). Code/tests của đợt này ở a61c71a, hồ sơ trong commit đi kèm.

## Cập nhật sửa review ngày 15/11/2026

Đã triển khai local sáu nhận xét của Đạt ở head a3858c7 theo
[contract đã được owner duyệt](drafts/sp-011-review-fix-contract.md).
93 JVM tests và 7 instrumentation tests đạt trên SM-G990E / Android 16.
assembleDebug/lintDebug đạt, lint 0 errors và 5 warnings. Xem
[kết quả, hash reports và source đã thử](evidence/sp-011-review-fixes-2026-11-15.json).

Request body one-shot chặn HTTP follow-up. Ledger giữ khóa theo tài khoản/nội dung,
đọc lại REJECTED với mã lỗi và nextAction, kiểm tra requestId cũ cùng Retry-After
trước khi đặt chỗ lần duyệt mới. Record đã đăng/UNKNOWN giữ khóa qua phục hồi ID.
Xác nhận URL gắn khóa attempt/requestId/Post ID tại lúc mở dialog. Callback OAuth
xếp hàng theo sessionId, kiểm tra lại TTL trước exchange. Revision marker được mã
hóa và ghi nhất quán với credential; v2/v3 được nâng an toàn, mất cả mốc cấp ID mới.
Cờ reauth production kiểm tra cả channelId và revision.

Thử nghiệm mới dùng fixtures. Bằng chứng bài thật đã có được giữ theo hồ sơ dưới.
Owner đã yêu cầu push đợt sửa review ngày 15/11. Code/tests nằm trong commit
26b594b, hồ sơ trong commit đi kèm. Title/description mới được giữ trong bản soạn PR.
GitHub đọc lại báo #77 Ready for review, #74 OPEN/CHANGES_REQUESTED. Các phần lịch sử
bên dưới giữ ngày/trạng thái lúc lập để đối chiếu. Trạng thái code hiện tại theo
phần cập nhật này. Backend code/dependency giữ nguyên nên dùng kết quả 57 tests đã
ghi nhận trước đó. Các bước review/merge và giới hạn refresh vẫn đang được theo dõi.

## Kết luận và phạm vi

Phần triển khai spike OAuth LinkedIn PERSON và đăng chữ đã có bằng chứng chạy trên
điện thoại thật. Hồ sơ này tổng hợp bằng chứng hiện có, không yêu cầu chụp lại hoặc
đăng bài mới. Phần refresh giữ giới hạn/đạt một phần của F1.B3; không đánh dấu toàn
bộ F1/F5 được nghiệm thu. Chưa đóng ticket, cập nhật tracker hoặc xác nhận merge.

Theo trao đổi với owner ngày 15/11, không yêu cầu thêm phép thử thu hồi quyền thật
để tiếp tục bàn giao. Xử lý token mất hiệu lực được kiểm tra bằng tests mô phỏng;
thu hồi token trên LinkedIn thật vẫn ghi NOT TESTED. Đây là giới hạn kiểm chứng,
không phải kết quả PASS thay cho tiêu chí hết hạn/thu hồi trong issue.

| Tiêu chí SP-011 | Kết quả | Nguồn |
|---|---|---|
| Loại tài khoản, product, scope, endpoint/version | Đã tổng hợp bên dưới | Postman, cấu hình app và test request |
| OAuth đúng danh tính trên app | PASS trên thiết bị, owner xác nhận | Ảnh connected |
| Một bài thật có ID/URL qua app | PASS, URL OWNER_CONFIRMED | Ảnh SUCCESS và link owner cung cấp |
| Callback bảo vệ phiên, kiểm tra lỗi adapter | Automated tests PASS; phiên cũ sau restart HTTP 400 INVALID_STATE | Báo cáo test, JSON và ảnh terminal |
| Refresh khi được cấp | BLOCKED/đạt một phần: app thử không nhận refresh token | Metadata token và chính sách provider |
| Hết hạn/401 | Mô phỏng tự động, tách khỏi thu hồi thật | Tests kho token và connector |
| Bàn giao metadata cho SP-019 | Nội dung đã chuẩn bị trong tài liệu này | Không khẳng định đã gửi cho Nghị |

## Bảng quyền và cấu hình đã kiểm chứng

| Mục | Giá trị / giới hạn |
|---|---|
| App | SocialPilot - Student Project |
| Tài khoản | PERSON; Ngô Gia Minh; ID `1vPSSpAcDv` |
| Author URN | `urn:li:person:1vPSSpAcDv` |
| Product | Share on LinkedIn — Default Tier; Sign In with LinkedIn using OpenID Connect — Standard Tier |
| Scope token quan sát được | `openid profile w_member_social` |
| Scope portal | Có thêm `email`; không coi đây là scope thực của token đã thử |
| Quyền đã chứng minh | UserInfo và đăng chữ cá nhân |
| Chưa chứng minh | ORGANIZATION/Page, comment, metrics, follower, ảnh/alt text |
| OAuth authorize | `https://www.linkedin.com/oauth/v2/authorization` |
| OAuth token | `https://www.linkedin.com/oauth/v2/accessToken` |
| API định danh | `GET https://api.linkedin.com/v2/userinfo` |
| API đăng | `POST https://api.linkedin.com/rest/posts` |
| Version app thử | `202609`, cấu hình build của APK thử; connector gửi header LinkedIn-Version, unit test kiểm tra header |
| Protocol | `X-Restli-Protocol-Version: 2.0.0` |
| Giới hạn version evidence | Request Headers Postman chưa có ảnh; không dùng version app để điền ngược cho Postman |
| Callback backend | `/api/oauth/linkedin/callback`; callback Postman riêng `/v1/browser-callback` trên oauth.pstmn.io |
| Môi trường app thật | Backend local qua Cloudflare Tunnel; Samsung SM-G990E, Android 16 / API 36 |
| Thời điểm | Postman response 13/11/2026 09:12:31 UTC; hồ sơ thiết bị lập 14/11, owner xác nhận phục hồi 15/11; không suy ngày chụp từ đồng hồ ảnh |

`202609` được đối chiếu trong [bảng version LinkedIn](https://learn.microsoft.com/en-us/linkedin/marketing/integrations/migrations?view=li-lms-2026-09).
App–Page verification và privacy policy chưa hoàn chỉnh theo ảnh portal ban đầu;
đó là giới hạn cấu hình đã ghi nhận, không phủ nhận phép thử đăng cá nhân thành công.

## Vòng đời token và xử lý mất hiệu lực

Kết luận cho cấu hình app cơ bản đang thử: **không nhận được refresh token**.
Manage Tokens có `expires_in = 5183999` giây, gần 60 ngày, không có trường refresh
trong thông tin owner cung cấp. Đây là TTL lúc cấp, không phải thời gian còn lại
ngày 15/11. Không tự tính ngày hết hạn từ timestamp metadata Postman.

LinkedIn giới hạn programmatic refresh cho đối tác MDP được phê duyệt theo
[tài liệu refresh token](https://learn.microsoft.com/en-us/linkedin/shared/authentication/programmatic-refresh-tokens).
Quyền này thuộc ứng dụng/chương trình API, không phụ thuộc tài khoản cá nhân Basic
hay Premium. Bộ evidence không phải raw token response đầy đủ; kết luận giới hạn
ở app/product và kết quả đang thử, không khẳng định mọi app LinkedIn đều thiếu refresh.

- Backend nhận `expires_in`, chuyển thành `expiresAt`; refresh token có thể null.
  Chưa có luồng refresh grant tự động được nghiệm thu. Test parse refresh field,
  nếu có, không chứng minh refresh grant hoạt động.
- `EncryptedOAuthStore.channel()` trả REAUTH_REQUIRED khi expiry đã qua;
  `tokenFor()` không trả token đã hết hạn, sai channel/revision hoặc cần reauth.
  Test mới lưu token giả có expiry quá khứ, reload, kiểm tra LinkedIn cần reconnect,
  Facebook không bị ảnh hưởng, reconnect tăng revision và vô hiệu revision cũ.
- Connector gặp HTTP 401: ghi cờ reauth đúng revision, trả AUTH_REQUIRED/RECONNECT,
  lưu kết quả từ chối và không tự POST lại. HTTP 403 là PERMISSION_REQUIRED,
  không đồng nhất với token hết hạn.
- Reconnect thật sau restart đã thành công theo owner; đây là OAuth mới, không phải refresh.
- Không chờ 60 ngày hoặc sửa đồng hồ thiết bị. Hết hạn thật và thu hồi quyền trên
  provider thật chưa thử; không yêu cầu thêm thao tác thủ công trong đợt bàn giao này.

## Bài thử và nguồn evidence

| Phép thử | ID / URL | Nguồn |
|---|---|---|
| Postman | `urn:li:share:7526935012005531648`; [activity URL](https://www.linkedin.com/feed/update/urn:li:activity:7526935012521377792/) | [Hồ sơ Postman](evidence/sp-011-postman-verification.md) |
| Android | `urn:li:share:7527386113993412608`; [link owner cung cấp](https://lnkd.in/p/gaC6fpu8) | [Hồ sơ thiết bị](evidence/sp-011-device-2026-11-14.md), [ảnh SUCCESS](evidence/sp-011-device-2026-11-14/linkedin-published-owner-verified.jpg) |
| Restart backend | Phiên cũ HTTP 400 INVALID_STATE; kết nối mới thành công theo owner | [JSON](evidence/sp-011-backend-restart-result.json), [ảnh terminal](evidence/sp-011-device-2026-11-14/backend-restart-pass.png) |

URL bài Android đầy đủ nằm trong ảnh SUCCESS. Link rút gọn là link chia sẻ do owner
cung cấp; harness yêu cầu URL bài đầy đủ `/posts/` hoặc `/feed/update/`, không tự
resolve short link. SUCCESS có provenance OWNER_CONFIRMED, không phải provider
readback. Hai ID Postman/Android là hai bài khác nhau, không thay bằng chứng cho nhau.

Owner xác nhận giữ connected và SUCCESS/Post ID/URL sau force-stop/mở lại. Hai
provider độc lập, migration v2/v3 và cờ reauth có instrumentation với dữ liệu giả;
không coi đó là bằng chứng hai tài khoản Facebook/LinkedIn thật cùng kết nối.

## Kiểm tra và ranh giới hồi quy

Kết quả/lệnh và hash báo cáo nằm trong [bản tổng hợp test](evidence/sp-011-validation-summary.json).
Backend và instrumentation giữ kết quả của lần chạy đã có; không trình bày như đã
chạy lại ngày 15/11. Android JVM/build/lint được chạy lại sau bổ sung test token.
Không dùng credentials thật trong tests và không gọi provider/đăng bài mới.

Cold start giữa OAuth và hai provider thật cùng kết nối có trong ma trận hồi quy
của contract mở rộng, chưa kiểm chứng trực tiếp cho SP-011. Chúng không phải hai
tính năng mới cần triển khai để chứng minh LinkedIn đăng cá nhân. Bằng chứng reopen
sau connected không thay cold start giữa OAuth. Phần này được bàn giao minh bạch
cho review, không tự đổi mọi ca chưa chạy thành điều kiện chặn API spike gốc.

## Bàn giao cho Đạt, Nghị và người tích hợp

- Đạt: review engine dùng chung, provider binding, state/handoff một lần, migration
  bảo toàn v2 Facebook, channel/revision và cờ reauth; route/DTO cũ được giữ.
- Nghị/SP-019: dùng PERSON/author URN và các ID/URL ở trên; quyền chỉ đã chứng minh
  UserInfo/đăng chữ. Comment, metrics, follower phải kiểm chứng riêng, không suy
  quyền từ w_member_social. Không đặt access token vào hồ sơ; lấy qua token
  reference của kho bảo mật theo đúng channel/revision, cấp lại bằng OAuth khi cần.
- Người tích hợp: `LINKEDIN_OAUTH_ENABLED`, `LINKEDIN_CLIENT_ID`,
  `LINKEDIN_CLIENT_SECRET`, `LINKEDIN_OAUTH_REDIRECT_URI` ở backend;
  `socialpilotOauthApiBaseUrl`, `socialpilotLinkedinApiVersion` khi build Android.
  Xem [development](development.md#linkedin-oauth-và-đăng-chữ-thử-nghiệm-sp-011).
- Harness chỉ một account/provider, đăng chữ, session backend trong memory một
  instance. Không thay editor, queue, approval production hoặc My Channels đầy đủ;
  không đổi Room và không nghiệm thu inbox/analytics.

## Trạng thái bàn giao Git và quy trình

Baseline đã push: `53435d8`. Theo ủy quyền owner ngày 15/11, các sửa code/tests
đã commit local `9df46d3`; hồ sơ và bản soạn PR được gom trong commit docs đi kèm.
Chưa push hoặc sửa PR trên GitHub. Các reports ghi nhận phép thử của code hiện
được commit ở `9df46d3`, không gán toàn bộ kết quả cho baseline `53435d8`.

Dependency SP-010/#74 và hai human approvals phải được kiểm tra tại thời điểm
tích hợp; trạng thái cũ trong tài liệu thiết kế không phải truy vấn GitHub hiện tại.
Không gửi thông báo hay xác nhận người nhận đã review trong lượt lập hồ sơ này.

Phần còn lại để đóng ticket theo quy trình: owner duyệt bản soạn PR và giới hạn đã
nêu, cho phép push/cập nhật PR, tích hợp dependency phù hợp, có hai human approvals
và checks áp dụng đạt, squash merge rồi cập nhật tracker. Chưa thực hiện các thao
tác đó trong lượt này ngoài commit local đã được ủy quyền. Refresh vẫn ghi giới hạn
F1.B3, không hạ thành PASS/N/A.
