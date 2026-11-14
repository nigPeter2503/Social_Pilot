# SP-011 — Contract tích hợp LinkedIn và trình tự triển khai

Ngày soạn: 13/11/2026, Asia/Bangkok. **Bản thiết kế trước triển khai.**
Cập nhật 14/11: code local và gate đã có; xem [hồ sơ triển khai](../sp-011-implementation.md) cho trạng thái hiện tại. Các bảng baseline bên dưới được giữ theo ngày soạn.
Minh đã đồng ý hướng tái sử dụng SP-010 và yêu cầu soạn contract trước khi code.
Điều này không thay hai human approvals hoặc xác nhận hợp đồng của bên sử dụng.

## 1. Phạm vi, trách nhiệm và baseline

| Mục | Nội dung |
|---|---|
| Ticket | [SP-011 / #13](https://github.com/nigPeter2503/Social_Pilot/issues/13) |
| Yêu cầu | F1.B1, F1.B3, F5.B1, F5.B5; không nghiệm thu toàn bộ F1/F5 bằng spike |
| Owner theo brief | Ngô Gia Minh — `nigPeter2503`; giữ nguyên đóng góp thành viên |
| Hai reviewer theo brief | Đạt — `dat532006`; Nghị — `bob35306` |
| Phối hợp | Đạt: phiên/kho bí mật; Nghị: author/capability và SP-019; Thiện: callback/UI/phiên bản duyệt |
| Deadline | Mốc gốc hết ngày làm việc 4 của W1 đã qua; chưa có hạn thay thế được xác nhận |
| Ước lượng | Brief gốc 8 giờ; cần ước lượng lại phần tách provider và bảo toàn kho token trước nhận triển khai; chưa coi 8 giờ là đủ |
| Tracker | Chưa đọc lại trạng thái GitHub trong lượt soạn; không lấy Todo cũ làm trạng thái hiện tại |
| Checkout đang mở | `feat/sp-009-social-contract`, HEAD `d098195`; có bản nháp/output chưa theo dõi, phải giữ nguyên |
| Nền đã kiểm tra | Worktree `D:/MobileProject/SocialPilot_Starter/SocialPilot-sp010`, HEAD được worktree list báo `d1d55a0` |
| Nhánh triển khai | Dự kiến `feat/sp-011-linkedin-oauth-spike`, worktree riêng, một PR về `main` |

**Trong phạm vi:** dùng chung quản lý phiên/handoff, LinkedIn OAuth PERSON,
lưu đồng thời Facebook/LinkedIn, callback Android, phép đăng chữ tối thiểu,
kiểm kê refresh và bàn giao SP-019. Các bước dưới là các phần của SP-011,
không phải ticket mới và không tạo thêm mã SP.

**Ngoài phạm vi:** queue production, Room schema mới, toàn bộ My Channels,
comment/analytics, đăng tổ chức khi chưa có quyền, AI, nâng dependency và quyết định
retention N1. Giữ 44 yêu cầu cam kết; giới hạn spike không hạ tiêu chí dự án.

Nguồn: [CONTRIBUTING](../../CONTRIBUTING.md), [brief](../TaskBrief/W1/tv1-minh-taskbrief.md#sp-011),
[social contract](../social-contract.md), [kiến trúc](../architecture.md),
[ma trận yêu cầu](../requirements.md), [development](../development.md),
[bản nháp ban đầu](sp-011-linkedin-oauth-draft.md).
Các nhận định skeleton trong tài liệu SP-009 chỉ đúng checkout SP-009;
không dùng chúng để phủ nhận code SP-010 ở worktree riêng.

## 2. Bằng chứng đầu vào và giới hạn

| Phép thử | Bằng chứng do Minh cung cấp trong chat | Kết luận |
|---|---|---|
| Product | Share on LinkedIn; Sign In with LinkedIn using OpenID Connect | Chưa có bằng chứng quyền ORGANIZATION |
| Scope token | `openid profile w_member_social` | Đăng cá nhân là hướng spike hiện tại; chưa thay quyết định downstream F6/F7 |
| UserInfo | Ảnh `GET /v2/userinfo` trả 200, đúng tên Minh | OAuth qua Postman đạt; chưa phải callback SocialPilot |
| Đăng chữ | Ảnh `POST /rest/posts` trả 201 | Post ID `urn:li:share:7526935012005531648` |
| URL | Minh xác nhận mở đúng bài | [Bài thật](https://www.linkedin.com/feed/update/urn:li:activity:7526935012521377792/) |
| Thời hạn | `expires_in = 5183999` giây | Gần 60 ngày lúc cấp, không phải thời gian còn lại |
| Refresh | Không thấy `refresh_token` trong Manage Tokens | Chưa kiểm chứng entitlement/raw token response; không báo refresh PASS |
| API version | Trước đó hướng dẫn thử `202609`, ảnh chưa hiển thị header request | Cần thu header thực gửi và kiểm tra version còn hỗ trợ khi chạy lại |

Ảnh/chat là bằng chứng owner cung cấp, chưa phải hồ sơ đã được reviewer nghiệm thu.
Không sao chép secret/token/code vào tài liệu. Không POST lại bài cũ chỉ để chụp ảnh.
Giữ cả share URN và activity URL; hai loại ID khác nhau không bị coi là cùng một chuỗi.

## 3. Ranh giới tái sử dụng

| Hiện có ở SP-010 | Đề xuất cho SP-011 |
|---|---|
| `FacebookOAuthSessionService`: phiên/state, khóa store, TTL, handoff | Tách engine chung; giữ thuật toán hạn/consume và các test hồi quy |
| `FacebookGraphClient`, URL builder và properties | Giữ adapter Facebook; thêm adapter LinkedIn độc lập |
| `OAuthController` chỉ chấp nhận Facebook | Định tuyến hai provider, giữ nguyên đường dẫn Facebook |
| `OAuthApi`, `FacebookOAuthCoordinator` cố định Facebook | Tham số hóa provider; wrapper Facebook chuyển tiếp nếu giúp tương thích |
| `EncryptedOAuthStore`: AES-GCM/Keystore, record v2 | Giữ cơ chế mã hóa; thêm phân vùng provider và nâng record có kiểm thử |
| MainActivity/MainViewModel đã nhận deep link | Chọn đúng phiên/provider từ pending store; thêm trạng thái LinkedIn VI/EN |
| DTO OAuth và SocialConnector của SP-009 | Giữ wire fields/chữ ký hiện có; không tạo DTO LinkedIn song song |

Tên class mới dưới đây là **đề xuất**, không khẳng định đã tồn tại.
Backend: `flow/OAuthSessionService`, `provider/OAuthProviderAdapter`,
`provider/ProviderIdentity`, `linkedin/LinkedInOAuthProperties`,
`linkedin/LinkedInOAuthClient`. Android: `core/OAuthCoordinator`,
`data/remote/linkedin/LinkedInPostsApi`, `data/repository/social/LinkedInSocialConnector`.

## 4. Contract backend và adapter

### 4.1 HTTP giữa Android và backend

| Method/path | Đầu vào | Đầu ra |
|---|---|---|
| `POST /api/oauth/{provider}/start` | `OAuthStartRequest` | 200 `OAuthStartResponse` |
| `GET /api/oauth/{provider}/callback` | `code`, `state`, `error` do provider trả | 303 về app với sessionId + handoffCode hoặc lỗi hợp lệ |
| `POST /api/oauth/{provider}/exchange` | `OAuthHandoffExchangeRequest` | 200 `OAuthExchangeResponse` |

Path dùng `facebook`/`linkedin`; wire DTO dùng `FACEBOOK`/`LINKEDIN`.
Kiểm tra provider trong path khớp body ở start và khớp phiên ở callback/exchange.
Provider lạ hoặc mismatch bị từ chối trước I/O provider; không âm thầm chọn Facebook.
Giữ `/facebook/start`, `/facebook/callback`, `/facebook/exchange` hiện có.
Không thêm endpoint đăng bài vào backend; Android gọi Posts API trực tiếp.

Wire DTO giữ nguyên §6 [social contract](../social-contract.md):

- Start: `provider`, `redirectId`, `sessionChallenge`.
- Start response: `sessionId`, `authorizationUrl`, `expiresAt`.
- Exchange request: `sessionId`, `handoffCode`, `sessionVerifier`.
- Exchange response: `sessionId`, `provider`, `externalAccountId`, `accountType`,
  `displayName`, `accessToken`, `refreshToken`, `tokenType`, `expiresAt`.
- Failure: `code`. Nếu cần thêm wire fields, dừng thay đổi đó để chốt cả hai bên,
  sửa fixture và test đồng thời; không tự mở rộng trong adapter.

### 4.2 Interface nội bộ provider, đề xuất

```java
interface OAuthProviderAdapter {
    String provider();
    URI authorizationUri(String state);
    ProviderIdentity exchangeCode(String code);
}
```

`ProviderIdentity` chứa provider, externalAccountId, accountType, displayName,
accessToken, refreshToken nullable, tokenType và expiresAt nullable.
Đây là dữ liệu nội bộ nhạy cảm, không phải response nghiệp vụ/log.
Callback URI lấy từ cấu hình tin cậy, không nhận từ query của client.
Nếu provider PKCE được xác minh hỗ trợ và bật, mở rộng interface bằng context
riêng cho provider PKCE; không tái sử dụng verifier handoff hoặc giả định đã có hỗ trợ.

Facebook adapter bọc hành vi Graph hiện tại. LinkedIn adapter dùng authorization
code flow đã thử qua Postman, POST form tới token endpoint rồi GET UserInfo bằng
access token; kiểm tra response bắt buộc, scope khi provider trả, lỗi mạng và timeout.
Không dùng ID token chưa xác minh chữ ký làm nguồn danh tính.
LinkedIn `externalAccountId` lấy nguyên `sub`, author dạng `urn:li:person:<sub>`;
giữ kiểm chứng mapping bằng bài thật, không suy ID từ tên/profile URL.

Tính expiry từ mốc bắt đầu request đổi code + `expires_in` hợp lệ, UTC chính xác
đến giây; không dùng timestamp Postman cho phiên mới, không hard-code 60 ngày.
Không có expiry thì giữ null và không coi là vô hạn. Không có refresh thì giữ null.

### 4.3 Bảo vệ phiên và lỗi

- Giữ TTL session 10 phút và handoff 60 giây của SP-010; `now >= expiresAt` từ chối.
- Phiên chứa provider, redirectId, challenge và hạn; state ngẫu nhiên dùng một lần.
- Engine chọn adapter từ registry tin cậy. Callback sai provider không consume state
  hoặc gọi provider; exchange sai provider không consume handoff.
- Cùng store lock bảo vệ kiểm tra hạn/phiên/verifier và consume; provider I/O ngoài lock.
- Handoff chỉ chứa digest trong index, dùng một lần; restart mất phiên thì kết nối lại.
  Giữ mô hình một backend instance của spike; chưa tuyên bố hỗ trợ multi-instance.
- Redirect Android chỉ chứa sessionId + handoffCode hoặc code lỗi, không có token.
  `redirectId = android-dev` tra cố định `socialpilot://oauth/callback`.
- Callback/start/exchange và lỗi có `Cache-Control: no-store`; callback giữ
  `Pragma: no-cache`, `Referrer-Policy: no-referrer` như SP-010.
- Mã lỗi hiện có: INVALID_STATE/400, INVALID_SESSION/401, INVALID_REDIRECT/400,
  HANDOFF_EXPIRED/410, HANDOFF_ALREADY_USED/409, ACCESS_DENIED/403,
  AUTH_REQUIRED/503 khi provider chưa cấu hình, PROVIDER_ERROR/502.
  Payload/DTO sai trả 400, không lộ exception hay credential.
- Thiếu provider config không làm sập provider còn lại: thêm cờ enable rõ ràng;
  provider bật nhưng config sai phải fail-fast, provider tắt trả AUTH_REQUIRED.
  Điều chỉnh test khởi động và health để không báo mọi provider đã sẵn sàng từ Facebook.

## 5. Contract Android và bảo toàn kết nối

### 5.1 Coordinator và callback

Coordinator nhận provider được cấu hình; chỉ chấp nhận cặp FACEBOOK/PAGE và
LINKEDIN/PERSON trong spike. Validate DTO sau Gson deserialize và đối chiếu
response sessionId/provider/accountType với pending trước lưu.
Authorization URL phải HTTPS và đúng host/path đăng nhập của adapter, không chỉ HTTPS.

Pending mỗi provider chứa sessionId, verifier, thời hạn phiên và provider, được mã hóa.
Giới hạn một pending đang hoạt động cho mỗi provider; bắt đầu lại chỉ thay pending
của chính provider đó. Callback tìm đúng pending bằng sessionId, không tin provider
do deep link tùy ý cung cấp; không tìm thấy hoặc trùng sessionId thì từ chối.
Busy/cancellation theo provider/phiên. Callback muộn không ghi đè phiên/kết nối mới.
Mất response exchange hoặc pending hết hạn thì bắt đầu lại, không phát lại mã đã consume.
Cold start khôi phục pending đã mã hóa; dữ liệu hỏng thì yêu cầu kết nối lại.
UI có loading/lỗi/offline/connected/reauth-required VI/EN; mạng ngoài main thread.

### 5.2 Kho token v3, đề xuất

- Giữ Keystore alias và AES-GCM của SP-010. Khóa record/AAD mới phân vùng theo provider:
  `pending_v3_FACEBOOK`, `pending_v3_LINKEDIN`, `connected_v3_FACEBOOK`,
  `connected_v3_LINKEDIN`. API kho phải yêu cầu provider, không có getter token mơ hồ.
- Mỗi connected record giữ credentials + identity + metadata cùng một lần ghi;
  kiểm tra provider record khớp key/AAD. Spike giữ một kết nối hoạt động mỗi provider;
  chưa phải quản lý nhiều account cùng nền tảng.
- Liên kết channelId/tokenReference với provider và externalAccountId. Cùng account
  reconnect tăng revision bền vững; account khác cần xác nhận đổi, channelId mới.
  Không kế thừa job/capability của account cũ hoặc reset revision khi app restart.
- Nâng `connected_v2` hợp lệ bằng đọc/giải mã AAD cũ, validate FACEBOOK/PAGE,
  mã hóa lại AAD mới và ghi v3 thành công trước khi bỏ v2. Không copy ciphertext.
  Khi v2 và v3 cùng tồn tại: v3 hợp lệ thắng, không ghi đè bằng bản cũ.
- Nâng pending v2 hợp lệ thành pending Facebook, giữ sessionId/verifier; nếu không
  xác định được hạn cũ thì chỉ cho một lần xử lý qua backend còn chịu TTL, không
  tự kéo dài phiên. Không có dữ liệu thì yêu cầu bắt đầu lại.
- Migration idempotent và kiểm tra lại sau restart. Encryption/commit thất bại
  không được công bố CONNECTED hoặc đọc lại credentials cũ như bản vừa lưu thành công.
  Đặc biệt xử lý SharedPreferences commit(false) vẫn thay bộ nhớ như test SP-010.
  Không xóa v2 trước khi v3 an toàn; dữ liệu v2 hỏng không ảnh hưởng LinkedIn/Room.
- Hủy pending/xóa credentials một provider không đụng provider còn lại, bản nháp
  hoặc dữ liệu module khác. Không áp dụng retention/tombstone N1 chưa được duyệt.
- Token hết hạn hoặc provider báo mất xác thực chuyển REAUTH_REQUIRED, giữ bản nháp,
  không tự POST lại. Không sửa refreshToken thành accessToken hay coi reauthorize là refresh.

## 6. Contract phép đăng chữ và refresh

LinkedInSocialConnector tuân SocialConnector/PublishRequest/PublishResult hiện có.
Lấy nội dung từ phiên bản bất biến đã duyệt qua nguồn snapshot nghiệp vụ, không
nhét text mới vào PublishRequest. Khi editor chưa sẵn sàng, dùng harness spike với
snapshot cố định được Minh duyệt, ghi rõ chưa thay F4/queue production.

- Android POST `https://api.linkedin.com/rest/posts`, bearer token đúng kết nối,
  JSON, `X-Restli-Protocol-Version: 2.0.0`, version kiểm tra còn hỗ trợ trước test.
- Validate approval, channelId/revision, accountType và token ngay trước gửi.
  Cancelable hủy HTTP và chặn callback muộn; không coi hủy là rollback provider.
- 201 + x-restli-id: lưu ID; chưa xác minh URL thì PUBLISHED_URL_PENDING.
  Chỉ SUCCESS khi có URL đã kiểm chứng đúng author/nội dung; không giả định share
  ID và activity ID giống nhau, không đòi quyền đọc chưa được cấp để bịa bằng chứng.
- Timeout/mất response sau có thể đã gửi: UNKNOWN/RECONCILE, không tự retry.
  401/thiếu quyền/429 xử lý riêng theo SocialError hiện có và căn cứ response.
- Spike không tuyên bố chống trùng bền vững đã hoàn tất khi queue/Room chưa có;
  harness khóa gửi lại và giữ hồ sơ cần đối soát cho đến khi người thử kiểm tra bài.
- PUBLISH_TEXT chỉ AVAILABLE với bằng chứng theo kết nối/app; comment, ảnh,
  alt text, metrics/follower vẫn UNVERIFIED nếu chưa thử; bàn giao Nghị kiểm tra SP-019.
- Refresh hiện ghi chưa kiểm chứng, blocker cần xác minh entitlement. Nếu raw
  response xác nhận không cấp refresh, ghi kết quả đó; không gửi refresh grant thiếu token.
  Nếu có quyền/token, chốt đường refresh backend giữ secret và test riêng trước mở dùng.
  Không thêm endpoint refresh giả hoặc báo F1.B3 PASS chỉ vì reconnect chạy được.

## 7. Các bước triển khai và điều kiện chuyển bước

| Bước | Việc thực hiện | Đầu ra / điều kiện chuyển bước |
|---|---|---|
| 0. Chốt nền | Đọc lại Issue #13, PR SP-010 và main; xác định revision đã review/merge; cập nhật nhận task theo ủy quyền | Không sửa checkout SP-009/SP-010 của người khác; baseline và tracker có bằng chứng |
| 1. Tạo nhánh riêng | Tạo worktree/nhánh SP-011 từ main mới nhất khi dependency SP-010 đã vào main | Nếu SP-010 chưa merge: ghi dependency; chuẩn bị draft trên baseline riêng chỉ khi được thống nhất, chưa coi merge-ready; không copy toàn bộ code SP-010 vào PR mới |
| 2. Tách engine backend | Registry adapter, ProviderIdentity, phiên gắn provider, route hai provider; bọc Facebook adapter | Test cũ Facebook tiếp tục đúng; mismatch/replay/TTL/race không gọi nhầm provider |
| 3. Thêm LinkedIn OAuth | Properties/enable, URL builder, token exchange, UserInfo, expiry, lỗi an toàn | Test provider giả có response đúng/sai/timeout; chưa tự báo OAuth thật PASS |
| 4. Nâng kho Android | Record v3, migration v2, provider-specific pending/connected, identity/revision | Test reload/migration/failed commit/corruption; kết nối Facebook còn nguyên khi thêm LinkedIn |
| 5. Nối Android | API có provider, coordinator dùng chung, dispatch callback, UI VI/EN | Test phiên cũ/mới, cold start, offline, callback sai; hai trạng thái kết nối độc lập |
| 6. Cấu hình và OAuth thật | Chạy backend + tunnel; đăng ký callback LinkedIn; build app với base URL HTTPS | Browser → backend → Android đúng PERSON, lưu an toàn; không dùng kết quả Postman thay end-to-end |
| 7. Đăng chữ tích hợp | Connector/harness snapshot đã duyệt, ID/URL, hủy/lỗi chưa rõ | Một bài thật do Minh cho phép, ID/URL đối chiếu; giữ riêng bằng chứng Postman trước đó |
| 8. Refresh và bàn giao | Kiểm tra entitlement/response đã che bí mật; reconnect, expiry; bàn giao Nghị | Tách PASS/FAIL/BLOCKED/NOT TESTED; refresh thiếu quyền vẫn là giới hạn F1.B3 |
| 9. Gate và review | Chạy checks hai build, tự review diff, cập nhật docs/issue/PR | Hai human approvals, checks đạt, xử lý nhận xét, squash merge và cập nhật Project theo CONTRIBUTING |

Thứ tự phụ thuộc: 0 → 1 → 2 → 3; 4 dùng hợp đồng bước 2; 5 cần 3 + 4;
6 cần 5 + HTTPS/credentials; 7 cần 6 + snapshot duyệt; 8 nhận dữ liệu từ 6/7;
9 cần toàn bộ bằng chứng áp dụng. Đây không phải chỉ thị giao subagent hoặc đổi owner.
Nếu kích thước vượt cửa sổ 1–2 ngày, Minh chia đầu ra độc lập thành ticket có mã đã
tra cứu trước khi mở rộng PR; không tự cấp mã mới hoặc bỏ phần kiểm chứng còn thiếu.

## 8. Cấu hình và kiểm chứng

Tên cấu hình đề xuất, chỉ có hiệu lực sau khi code binding:
`LINKEDIN_OAUTH_ENABLED`, `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET`,
`LINKEDIN_OAUTH_REDIRECT_URI`, `LINKEDIN_API_VERSION`; Facebook có enable flag
tương ứng để chạy độc lập. Android tiếp tục `SOCIALPILOT_OAUTH_API_BASE_URL` của SP-010.
Spring không tự đọc .env; mẫu chỉ dùng placeholder. Không commit credentials.

Tunnel có thể dùng chung với Facebook vì callback khác path. Đăng ký URL LinkedIn
`https://<host>/api/oauth/linkedin/callback` chính xác; không dùng callback Postman
cho luồng ứng dụng. URL tunnel đổi thì cập nhật provider config và Android base URL,
restart/rebuild theo phần thay đổi; bỏ phiên cũ rồi bắt đầu lại.

| Nhóm test | Ca cần có |
|---|---|
| Engine | Sai provider path/body/phiên; callback nhầm provider; state replay; TTL 60 giây và 10 phút đúng boundary; một winner trong hai exchange; hết hạn trong provider I/O; restart |
| Adapter | Token/UserInfo thiếu field, scope không đủ khi có phản hồi scope, 4xx/5xx/non-JSON/timeout; expiry; refresh null; không lộ secret trong lỗi |
| Kho token | Nâng v2 Facebook, v3 đã có, chạy migration lại, failed encrypt/commit/restart, hai provider cùng tồn tại, ciphertext đổi chỗ, clear đúng provider |
| Android | Response sessionId/provider sai, cold start, callback lặp/cũ, bắt đầu lại phiên, mất mạng, hủy; VI/EN; Facebook regression |
| Publish | Sai channel/revision/approval từ chối trước gửi; 201 ID chưa URL; UNKNOWN không retry; auth/permission/rate limit; cancel chặn callback muộn |
| Thật | OAuth Android hai provider độc lập; giữ Facebook khi thêm LinkedIn; bài LinkedIn đúng author/URL; expiry/revocation nếu có phép thử thực tế, tách mô phỏng |

Các test trên là yêu cầu tương lai, **chưa chạy cho SP-011**.
Gates khi sửa code, dùng wrapper riêng:

```powershell
# Trong backend/
.\gradlew.bat test bootJar --console=plain
# Trong android/
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Thêm kiểm thử thiết bị cho Keystore/migration/callback/vòng đời. Ghi rõ API level,
revision, ca thật/mô phỏng và hạn chế; kết quả SP-010 cũ không chứng minh SP-011 mới.
Lượt soạn tài liệu chỉ kiểm tra nội dung/liên kết/whitespace; không yêu cầu build app.

## 9. Điểm phối hợp và điều kiện hoàn tất

- Minh giữ quyền tích hợp phần chung; phối hợp Đạt trước sửa kho bảo mật và engine.
  Nghị kiểm tra PERSON có đủ đường tiếp F6/F7; chưa đủ thì ghi blocker, không đổi phạm vi.
- Thiện xác nhận snapshot duyệt và callback/UI. Không tự đổi Room hoặc đưa nghiệp vụ vào View.
- Trước code chốt: baseline SP-010 thực tế đã merge hay chưa, hạn/ước lượng mới,
  lựa chọn PERSON cho spike, môi trường tunnel và cách nâng kho v2. Đây là điểm
  phối hợp trong ticket, không khẳng định reviewer đã xác nhận bản nháp này.
- Trước đăng bài tích hợp mới: Minh chọn nội dung, tài khoản, đối tượng xem và thời
  gian giữ bài; việc đã đăng Postman không tự cho phép phát lại bài đó.
- Bàn giao Nghị: accountType, author URN, Post ID/URL, product/tier, scope/version,
  token reference, expiry, nơi lấy credentials riêng và quyền chưa kiểm chứng.
- Hồ sơ SP-011 ghi revision, ngày giờ/múi giờ, thiết bị, endpoint/version, kết quả,
  ảnh đã che bí mật và việc còn thiếu. Refresh chưa đạt giữ BLOCKED/đạt một phần
  theo tiêu chí, không tự đóng bằng N/A. Không đánh dấu toàn bộ yêu cầu F đạt.
- Chỉ kết thúc ticket theo CONTRIBUTING: tiêu chí đạt, hai approval thật trên bản
  cuối, checks đạt, merge và Project cập nhật. Bản contract này chưa tạo/sửa Issue,
  nhánh/PR, gửi thông báo hoặc thay đổi trạng thái bất kỳ dịch vụ bên ngoài nào.

Nguồn provider đã đối chiếu trong phiên hướng dẫn (cần kiểm tra lại khi thực thi):
[LinkedIn OAuth](https://learn.microsoft.com/en-us/linkedin/shared/authentication/authorization-code-flow),
[OpenID Connect](https://learn.microsoft.com/en-us/linkedin/consumer/integrations/self-serve/sign-in-with-linkedin-v2),
[Posts API](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/posts-api?view=li-lms-2026-09),
[refresh token](https://learn.microsoft.com/en-us/linkedin/shared/authentication/programmatic-refresh-tokens).
