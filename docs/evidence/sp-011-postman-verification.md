# SP-011 — Bằng chứng kiểm chứng LinkedIn bằng Postman

Ngày lập hồ sơ: 14/11/2026, Asia/Bangkok.
Nguồn: ảnh chụp do Ngô Gia Minh cung cấp và xác nhận kết quả trong chat.
Trạng thái: **Bằng chứng API thật qua Postman; chưa nghiệm thu tích hợp SocialPilot.**

## 1. Thông tin truy vết

| Mục | Giá trị |
|---|---|
| Ticket | [SP-011 / Issue #13](https://github.com/nigPeter2503/Social_Pilot/issues/13) |
| Owner theo brief | Ngô Gia Minh — `nigPeter2503` |
| Reviewer theo brief | Nguyễn Đức Đạt — `dat532006`; Tô Lãng Nghị — `bob35306` |
| Yêu cầu liên quan | F1.B1, F1.B3, F5.B1, F5.B5 |
| Công cụ | Postman Web, LinkedIn Developer Portal |
| App | SocialPilot - Student Project |
| Loại tài khoản thử | Cá nhân — `PERSON` |
| Author | `urn:li:person:1vPSSpAcDv` |
| Revision ứng dụng | Không áp dụng cho request Postman; chưa có revision SP-011 tích hợp được kiểm chứng trong hồ sơ này |
| Provider endpoint | `/v2/userinfo`, `/rest/posts` |
| Nguồn thời gian đăng | Response header Date: `Fri, 13 Nov 2026 09:12:31 GMT`, tương đương **13/11/2026 16:12:31 UTC+07:00** |
| Giới hạn thời gian | Date là thời gian response do server trả, không tự xác định thời điểm duyệt hoặc chụp từng ảnh |

Tài liệu này không cập nhật trạng thái tracker, tạo PR hoặc xác nhận approval của reviewer.
Nguyên tắc nghiệm thu theo [CONTRIBUTING](../../CONTRIBUTING.md),
[brief SP-011](../TaskBrief/W1/tv1-minh-taskbrief.md#sp-011) và
[social contract](../social-contract.md).

## 2. Danh mục ảnh

Các ảnh giữ nguyên kích thước và nội dung quan sát được, ngoại trừ các vùng giá trị
xác thực/phiên được che bằng khối màu kín. Không điền thêm giá trị vào ảnh.
Hash SHA-256 của từng file bàn giao nằm trong [manifest](sp-011-postman-2026-11-14/manifest.json).
Tên thư mục là ngày lập bộ evidence, không phải khẳng định mọi ảnh chụp ngày đó.

| Ảnh | Nội dung | Phạm vi chứng minh |
|---|---|---|
| [01 — App Settings](sp-011-postman-2026-11-14/01-app-settings.png) | Tên app, Page liên kết, logo, trạng thái verification và privacy policy | Cấu hình tại thời điểm chụp |
| [02 — Products](sp-011-postman-2026-11-14/02-products.png) | Hai product và tier; portal hiển thị ngày tạo app 12/11/2026 | App đã thêm product; không thay quyền thực trên token |
| [03 — Scopes/callback](sp-011-postman-2026-11-14/03-scopes-callback.png) | TTL mặc định, redirect Postman và scope app | Cấu hình OAuth tại portal |
| [04 — Auth Settings](sp-011-postman-2026-11-14/04-auth-settings.png) | Client ID, secret ở trạng thái ẩn, TTL và callback | Đối chiếu app/callback |
| [05 — Postman OAuth](sp-011-postman-2026-11-14/05-postman-oauth.png) | Authorization Code, endpoint, callback, scope, client authentication | Cấu hình phép thử; URL dài bị rút gọn trong ô giao diện |
| [06 — Token metadata](sp-011-postman-2026-11-14/06-token-metadata.png) | Token type, expires_in và scope | Metadata token thực nhận |
| [07 — Flow metadata](sp-011-postman-2026-11-14/07-token-flow-metadata.png) | access_token_url, grant_type, client_authentication, timestamp | Metadata Postman; không phải raw token response |
| [08 — Token metadata bổ sung](sp-011-postman-2026-11-14/08-token-metadata-additional.png) | Cùng nội dung quan sát như ảnh 06 | Không tính thành phép thử độc lập |
| [09 — Publish body/response](sp-011-postman-2026-11-14/09-publish-body-response.png) | POST /rest/posts, body và 201 Created | Payload bài thử và response đã lưu |
| [10 — UserInfo](sp-011-postman-2026-11-14/10-userinfo.png) | GET /v2/userinfo, 200 OK, tên và sub | Danh tính tài khoản được truy xuất bằng API |
| [11 — Publish response headers](sp-011-postman-2026-11-14/11-publish-response-headers.png) | Date, Location, x-restli-id, protocol version và 201 Created | Kết quả tạo bài, ID và thời gian response |

## 3. App, product và quyền

| Hạng mục | Quan sát | Kết luận giới hạn |
|---|---|---|
| Page liên kết | SocialPilot- Student Project | Có liên kết Page trong Settings |
| App–Page verification | Portal ghi app chưa được verified với company; còn nút Verify | Chưa xác minh app–Page theo ảnh; không ghi đã verified |
| Privacy policy | Portal ghi chưa có URL | Cần bổ sung cấu hình; chưa có ảnh trạng thái sau bổ sung |
| Share on LinkedIn | Default Tier, trong Added products | Product đăng cá nhân đã được thêm |
| Sign In with LinkedIn using OpenID Connect | Standard Tier, trong Added products | Product định danh đã được thêm |
| Scope app | openid, profile, w_member_social, email | Scope portal có thể yêu cầu; không đồng nghĩa token hiện tại được cấp email |
| Scope token | `openid profile w_member_social` | Đây là scope thực hiển thị trong Manage Tokens |
| Đăng tổ chức | Không thấy w_organization_social trong bộ scope cung cấp | Chưa có bằng chứng quyền đăng ORGANIZATION/Page |

Chỉ suy ra khả năng đã được từng phép gọi API chứng minh, không suy quyền đọc
comment, analytics hay follower từ w_member_social hoặc đăng bài thành công.

## 4. OAuth và metadata token

- Grant type: Authorization Code; metadata `grant_type = authorization_code`.
- Callback đã đăng ký: `https://oauth.pstmn.io/v1/browser-callback`.
- Auth URL cấu hình trong hướng dẫn: `https://www.linkedin.com/oauth/v2/authorization`.
  Ảnh 05 chỉ hiển thị một phần URL do chiều rộng ô.
- Access Token URL hiện đầy đủ ở ảnh 07:
  `https://www.linkedin.com/oauth/v2/accessToken`.
- Client authentication: Send client credentials in body; metadata `body`.
- Token type: Bearer.
- `expires_in = 5183999` giây, tương đương 59 ngày 23 giờ 59 phút 59 giây lúc cấp.
  Đây không phải thời gian còn lại tại lúc lập báo cáo.
- Portal hiển thị TTL mặc định `5184000` giây; không dùng giá trị mặc định thay
  trường expiry thực nhận trong từng lần OAuth.
- `timestamp = 1794560315058` là metadata Postman; không dùng nó làm expiry do provider cấp.
- Theo Minh quan sát Manage Tokens: không thấy refresh_token hoặc refresh expiry.
  Bộ ảnh không phải toàn bộ raw token response; chưa xác minh entitlement refresh.
  Programmatic refresh **NOT TESTED**, không đánh dấu PASS hoặc tự coi N/A.

OAuth thành công trong hồ sơ này là browser ↔ LinkedIn ↔ Postman.
Chưa phải Android ↔ backend ↔ LinkedIn; không chứng minh session store, state,
handoff TTL, replay hoặc Keystore của ứng dụng hoạt động.

## 5. UserInfo — phép thử API thật

```http
GET https://api.linkedin.com/v2/userinfo
```

Ảnh 10 cho thấy `200 OK`, response gồm `name`, `sub`, `locale`, `given_name`,
`family_name`. Tên hiển thị là Ngô Gia Minh; `sub = 1vPSSpAcDv`.
Không có email trong response được cung cấp và token không xin scope email.

**Kết quả: PASS cho phép thử UserInfo qua Postman.**
Đây là dữ liệu định danh từ API, không phải bằng chứng xác minh danh tính pháp lý.

## 6. Đăng bài chữ — phép thử API thật

```http
POST https://api.linkedin.com/rest/posts
```

Các trường body quan sát được ở ảnh 09:

```json
{
  "author": "urn:li:person:1vPSSpAcDv",
  "commentary": "Bài đăng thử nghiệm API của đồ án SocialPilot. Nhóm đang kiểm chứng khả năng kết nối và đăng nội dung lên LinkedIn.",
  "visibility": "PUBLIC",
  "distribution": {
    "feedDistribution": "MAIN_FEED",
    "targetEntities": [],
    "thirdPartyDistributionChannels": []
  },
  "lifecycleState": "PUBLISHED",
  "isReshareDisabledByAuthor": false
}
```

Response ảnh 11:

| Trường | Giá trị |
|---|---|
| HTTP | `201 Created` |
| Date | `Fri, 13 Nov 2026 09:12:31 GMT` |
| Content-Length | `0` — response body rỗng không tự được coi là lỗi |
| x-restli-id | `urn:li:share:7526935012005531648` |
| Location | `/posts/urn%3Ali%3Ashare%3A7526935012005531648` |
| x-restli-protocol-version | `2.0.0` — đây là response header |

Minh cung cấp [URL bài thật](https://www.linkedin.com/feed/update/urn:li:activity:7526935012521377792/)
và xác nhận kết quả đăng đã ổn trong chat. Share URN và activity URL được giữ
riêng, không tự suy chúng có cùng phần ID.

**Kết quả: PASS tạo bài qua API; URL được owner cung cấp và xác nhận.**
Chưa có screenshot bài trên giao diện LinkedIn trong bộ ảnh này.
Chưa có ảnh request Headers thể hiện LinkedIn-Version thực gửi; giá trị `202609`
trong hướng dẫn trước chỉ là cấu hình đề xuất, chưa ghi thành version đã xác minh.
Response protocol version không thay bằng chứng request LinkedIn-Version.

Chưa có xác nhận riêng về thời điểm duyệt nội dung trước Send; không dùng việc
đăng thành công để kết luận F4.B1 hoặc approval production đã được nghiệm thu.

## 7. Tổng hợp kết quả và phần còn thiếu

| Hạng mục | Trạng thái | Căn cứ / việc tiếp theo |
|---|---|---|
| Product, scope app, callback Postman | OBSERVED | Ảnh 02–05 |
| OAuth + UserInfo qua Postman | PASS | Ảnh 06, 07, 10 |
| Đăng chữ cá nhân | PASS | Ảnh 09, 11; ID thật |
| URL bài gốc | OWNER CONFIRMED | Link trong chat; bổ sung screenshot nền tảng |
| API version thực gửi | NOT VERIFIED | Bổ sung request Headers; không cần POST lại bài chỉ để chụp ảnh |
| App–Page verification/privacy policy | INCOMPLETE | Ảnh 01; bổ sung rồi thu trạng thái mới |
| Programmatic refresh | NOT TESTED | Chưa có bằng chứng token/entitlement và refresh grant thành công |
| Kết nối lại | NOT TESTED | Chưa có hồ sơ phép thử reconnect riêng |
| OAuth backend/Android, lưu hai kết nối | NOT TESTED | Thực hiện sau triển khai SP-011 |
| State/replay/TTL/callback vòng đời | NOT TESTED cho SP-011 | Test tự động và thiết bị; không lấy Postman làm thay thế |
| Ảnh/alt text/comment/metrics/follower | NOT TESTED trong hồ sơ | Kiểm kê riêng; bàn giao Nghị/SP-019 |

F1.B3 chưa đủ bằng chứng nghiệm thu đầy đủ; F1.B1/F5.B1 chưa đủ luồng tích hợp
hai nền tảng trong ứng dụng. F5.B5 có ID và URL cho bài Postman này, không tự
chứng minh toàn bộ chức năng link bài trong app. Không đánh dấu SP-011 hoàn tất
chỉ bằng hồ sơ Postman.

## 8. Bàn giao và kiểm tra tài liệu

- Bàn giao Nghị accountType PERSON, author URN, share URN, activity URL, scope,
  TTL và danh sách quyền chưa kiểm chứng. Credentials bàn giao qua kênh riêng
  theo quy trình dự án, không nằm trong tài liệu/manifest.
- Bổ sung: screenshot bài, request Headers, xác nhận duyệt nội dung thực tế,
  kết quả verification/privacy policy, entitlement refresh nếu có.
- Phép thử tiếp theo theo [contract tích hợp SP-011](../drafts/sp-011-integration-contract.md).
  Bản này chỉ ghi nhận đầu vào, không sửa code hoặc đưa ra chính sách mới.
- Không gọi lại API hoặc tạo bài mới trong lượt lập hồ sơ này.
- Không chạy Gradle vì chỉ bổ sung tài liệu và ảnh; kiểm tra liên kết tương đối,
  hash ảnh, vùng che và nội dung hồ sơ trước bàn giao.
