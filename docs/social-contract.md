# Hợp đồng Social v1 — SP-009

Ngày triển khai: 07/11/2026 (Asia/Bangkok). Người dùng đã duyệt bản nháp để triển khai.
Ngày 09/11/2026, Minh đã xác nhận bản sửa hai blocking và kết quả kiểm tra local
(30 test đạt, Android build/lint và backend bootJar thành công).
Đây là xác nhận của owner; hai approval reviewer trên bản sửa chưa được xác minh,
chưa nghiệm thu API thật. N1 ở §7 chưa có quyết định chính sách cụ thể.

- Ticket: [SP-009 / #11](https://github.com/nigPeter2503/Social_Pilot/issues/11).
- Owner: Ngô Gia Minh. Reviewer: Nguyễn Đức Đạt, Tô Lãng Nghị.
- Thiện xác nhận version/approval; Nghị xác nhận capability comment/metrics;
  Đạt xác nhận định danh, bí mật, lỗi và ownership.
- Truy vết: INFRA, F1.B1, F1.B3, F5.B1; liên quan F1.B4, F4.B1, F5.B3, F5.B5.
  Việc liên quan không đồng nghĩa các yêu cầu này đã nghiệm thu.

## 1. Cách đọc và phạm vi

DTO là đối tượng chứa dữ liệu trao đổi. Interface khai báo thao tác có thể gọi.
Hợp đồng bao gồm cả hai và các quy tắc mà phần triển khai phải tuân thủ.

Ví dụ: giao diện đưa kênh và phiên bản đã duyệt; phần đăng trả kết quả có ID/URL.
DTO không tự gọi Facebook, kiểm tra quyền, lưu token hoặc chống đăng trùng.

SP-009 có DTO, interface, validation cấu trúc và ca kiểm thử hợp đồng.
OAuth endpoints, adapter thật, kho token, transaction Room, Worker và UI thuộc task tiếp theo.
Tại baseline SP-009 endpoint OAuth trả 501 OAUTH_NOT_CONFIGURED. Nhánh SP-010 hiện triển khai adapter Facebook và callback/handoff trên contract này; trạng thái kiểm chứng nằm trong [bản ghi SP-010](drafts/sp-010-implementation-status.md).

## 2. Vị trí và quy ước

- Android: [domain/contract](../android/app/src/main/java/com/socialpilot/app/domain/contract/),
  DTO nghiệp vụ trong social/, DTO OAuth trong oauth/OAuthContract.java.
- Backend: [contract/OAuthContract.java](../backend/src/main/java/com/socialpilot/oauth/contract/OAuthContract.java).
- Ví dụ wire dùng chung: [oauth-examples.json](contracts/oauth-examples.json). Toàn bộ là dữ liệu giả.
- Hai Gradle build độc lập; hai bản OAuthContract giữ cùng tên trường và giá trị wire.
  Khi đổi hợp đồng, sửa và chạy test cả hai phía trong cùng PR.
- ID là chuỗi không rỗng; connectionRevision là số dương.
- Wire timestamp dùng UTC ISO 8601, chính xác dạng yyyy-MM-dd'T'HH:mm:ss'Z'.
  Timestamp capability cũng theo định dạng này; adapter chịu trách nhiệm nguồn thời gian.
- DTO nghiệp vụ bất biến, được tạo qua constructor/factory; không deserialize trực tiếp bằng Gson.
- DTO OAuth Android phải gọi validate() sau deserialize bởi Gson có thể bỏ qua constructor.
  Backend dùng Jackson constructor có validation và mapper do Spring quản lý, bật
  `spring.jackson.parser.strict-duplicate-detection` trong application.yml để từ chối
  khóa JSON lặp trước khi dữ liệu ghi đè giá trị đã validate. Không tự tạo ObjectMapper
  mặc định để nhận payload OAuth; constructor validation một mình không đủ.
  Regression test kiểm tra mapper Spring và JSON converter đăng ký trong MVC.
  Validation cấu trúc không thay xác thực phiên.
- null biểu thị thiếu dữ liệu theo quy tắc bên dưới. Không bịa ID, URL hay thời điểm.
- Không log DTO OAuth đã serialize, HTTP body xác thực, token hoặc secret.
  toString() DTO OAuth luôn che toàn bộ nội dung; điều này không thay cấu hình che log mạng.

## 3. Kênh và capability

ChannelInfo:

| Trường | Ý nghĩa |
|---|---|
| channelId | ID nội bộ SocialPilot |
| platform | FACEBOOK / LINKEDIN |
| externalAccountId | ID Page, cá nhân hoặc tổ chức trên provider |
| accountType | PAGE / PERSON / ORGANIZATION; adapter kiểm chứng tổ hợp thực tế |
| displayName | Tên hiển thị, không dùng làm khóa |
| connectionStatus | CONNECTED / REAUTH_REQUIRED / DISCONNECTED |
| connectionRevision | Revision kết nối; thay khi ngắt hoặc thay kết nối |
| tokenReference | Nhãn kho bí mật; bắt buộc khi còn kết nối, null khi DISCONNECTED |

tokenReference không phải token thật. Token hết hạn có thể còn trong kho để refresh;
trạng thái REAUTH_REQUIRED và lỗi AUTH_REQUIRED phải được xử lý, không giả định kết nối còn dùng được.
Revision phải tăng/bất khả tái sử dụng cho cùng channelId, kể cả khi khởi động lại ứng dụng.

ChannelCapability gồm operation, availability, reasonCode và verifiedAt.

| operation | Ý nghĩa |
|---|---|
| PUBLISH_TEXT / PUBLISH_IMAGE | Đăng chữ / ảnh |
| SET_ALT_TEXT | Truyền alt text lên nền tảng |
| READ_COMMENTS / REPLY_COMMENT | Đọc / trả lời bình luận |
| READ_POST_METRICS / READ_FOLLOWER_COUNT | Đọc số liệu bài / follower |

| availability | Quy tắc |
|---|---|
| AVAILABLE | Có bằng chứng tại verifiedAt |
| PERMISSION_REQUIRED | Thiếu quyền, có reasonCode và thời điểm kiểm chứng |
| UNSUPPORTED | Đã xác định không hỗ trợ trường hợp này, có lý do và thời điểm |
| UNVERIFIED | Chưa kiểm chứng; reasonCode bắt buộc, verifiedAt có thể null |

Không suy quyền comment/metrics từ đăng thành công, không coi UNVERIFIED là AVAILABLE.
Các trạng thái trừ AVAILABLE yêu cầu reasonCode. Mỗi lần gọi vẫn xử lý quyền bị thu hồi;
capability là ảnh chụp khả năng, không phải bảo đảm quyền vĩnh viễn.
Mọi verifiedAt khác null đều phải đúng định dạng UTC ở §2 và là ngày lịch hợp lệ,
kể cả khi UNVERIFIED. Chỉ UNVERIFIED được phép có verifiedAt null.

## 4. Yêu cầu và kết quả đăng

PublishRequest gồm requestId, channelId, connectionRevision, approvedVersionId.
Một request cho một kênh; đăng hai kênh tạo hai request độc lập.
Nội dung/ảnh lấy từ đúng phiên bản bất biến, không lấy bản mới nhất.
Kiểm tra approval lúc enqueue và ngay trước gửi; sửa nội dung/ảnh làm mất hiệu lực duyệt.
Constructor chỉ kiểm tra ID hợp lệ, không xác nhận approval trong database.

PublishResult luôn giữ requestId/channelId/connectionRevision của request.

| outcome | postId / postUrl | error và cách xử lý |
|---|---|---|
| SUCCESS | Cả hai bắt buộc; URL HTTPS tuyệt đối | null; lưu và hiển thị đã đăng |
| REJECTED | Cả hai null | Bắt buộc; biết yêu cầu chưa đăng hoặc bị từ chối |
| UNKNOWN | Cả hai null | PUBLISH_RESULT_UNKNOWN / RECONCILE; không tự gửi lại |
| PUBLISHED_URL_PENDING | Có ID, URL null | null; bổ sung URL, không đăng lại |

PUBLISHED_URL_PENDING không đáp ứng đầu ra ID/URL đầy đủ. Kiểm tra URL trong DTO chỉ kiểm tra
cấu trúc HTTPS, không chứng minh URL mở đúng bài; adapter/bằng chứng API phải kiểm chứng điều đó.
Factory ngăn SUCCESS thiếu bằng chứng và ngăn UNKNOWN được đóng gói thành REJECTED.

### Ba ví dụ bắt buộc (giả lập)

Đầu vào chung:

```json
{"requestId":"r1","channelId":"c1","connectionRevision":1,"approvedVersionId":"v1"}
```

Thành công:

```json
{"requestId":"r1","channelId":"c1","connectionRevision":1,"outcome":"SUCCESS","postId":"p1","postUrl":"https://example.com/posts/p1","error":null}
```

Thiếu quyền:

```json
{"requestId":"r1","channelId":"c1","connectionRevision":1,"outcome":"REJECTED","postId":null,"postUrl":null,"error":{"code":"PERMISSION_REQUIRED","nextAction":"RESOLVE_PERMISSION"}}
```

Timeout sau gửi:

```json
{"requestId":"r1","channelId":"c1","connectionRevision":1,"outcome":"UNKNOWN","postId":null,"postUrl":null,"error":{"code":"PUBLISH_RESULT_UNKNOWN","nextAction":"RECONCILE"}}
```

SocialError:

| code | nextAction thường dùng |
|---|---|
| APPROVAL_REQUIRED | APPROVE_VERSION |
| CHANNEL_DISCONNECTED / AUTH_REQUIRED | RECONNECT |
| PERMISSION_REQUIRED | RESOLVE_PERMISSION |
| UNSUPPORTED_OPERATION | NONE |
| INVALID_CONTENT | EDIT_CONTENT |
| RATE_LIMITED | WAIT |
| NETWORK_ERROR | RETRY_WHEN_SAFE, chỉ khi chắc chắn chưa đăng |
| PUBLISH_RESULT_UNKNOWN | RECONCILE, bắt buộc |

nextAction không tự kích hoạt hành động. Queue phải xét outcome, lỗi và bằng chứng gửi.
Đặc biệt lỗi mạng sau khi có thể đã gửi phải dùng UNKNOWN thay vì retry mù quáng.

## 5. Interface, callback và hàng đợi

SocialConnector có channel(), capability(operation), publish(request, callback).
Mỗi instance đại diện một kênh/revision; request khác kênh/revision phải bị từ chối trước gửi.
publish không chặn main thread, trả Cancelable; callback chạy nền và consumer chuyển sang UI thread.
Callback kết thúc tối đa một lần khi tác vụ chưa bị hủy; sau hủy có thể không callback.
Đối soát về sau là bước riêng cập nhật bản ghi bền vững, không gọi lại callback kết thúc.

PublishQueue nhận PublishRequest thay cho hai chuỗi ID cũ. enqueue chạy ngoài main thread,
phải lưu transaction trước khi trả về; lỗi lưu/validation phải báo cho bên gọi và không xếp hàng.
Lặp cùng requestId/payload là idempotent; cùng ID khác payload phải từ chối.
Khóa channelId + approvedVersionId ngăn request ID mới lách chống đăng trùng khi đã có
yêu cầu đang hoạt động hoặc đã đăng. Cơ chế khóa/Room chưa được triển khai ở SP-009.
requestId không mặc định là idempotency key của Facebook/LinkedIn.

cancelForConnection(channelId, revision) chỉ hủy revision được chỉ định; không ảnh hưởng kết nối mới.
Hủy phải truyền đến HTTP Call và chặn callback muộn. Hủy không có nghĩa provider đã rollback.
Nếu đã gửi, queue giữ trạng thái cần đối soát ngay cả khi callback UI bị bỏ qua.

## 6. OAuth Android ↔ backend

```text
Android tạo verifier ngẫu nhiên và challenge
  → backend nhận OAuthStartRequest, tạo state và phiên TTL
  ← OAuthStartResponse (sessionId, authorizationUrl, expiresAt)
Android mở trình duyệt hệ thống
  → provider cấp quyền → callback backend
Backend kiểm tra state/phiên/redirect, đổi code với provider
  → redirect app chỉ mang mã bàn giao và sessionId
Android đối chiếu sessionId đang chờ
  → HTTPS đổi mã + verifier qua OAuthHandoffExchangeRequest
  ← OAuthExchangeResponse
Android lưu token vào kho bí mật, tạo tokenReference cho ChannelInfo
```

### Wire DTO

| DTO | Trường |
|---|---|
| OAuthStartRequest | provider, redirectId, sessionChallenge |
| OAuthStartResponse | sessionId, authorizationUrl, expiresAt |
| OAuthHandoffExchangeRequest | sessionId, handoffCode, sessionVerifier |
| OAuthExchangeResponse | sessionId, provider, externalAccountId, accountType, displayName, accessToken, refreshToken, tokenType, expiresAt |
| OAuthFailure | code |

provider dùng FACEBOOK/LINKEDIN. redirectId tra cấu hình cho phép ở backend, không phải URL tùy ý.
authorizationUrl phải HTTPS; adapter phải thêm kiểm tra host provider được cấu hình.
expiresAt bắt buộc ở phiên; ở token có thể null khi provider không cung cấp. Không coi null là không hết hạn.
refreshToken có thể null: không được cấp refresh không đồng nghĩa kết nối lại là refresh thành công.
Token response nhạy cảm là ngoại lệ có chủ đích của luồng xác thực; không đưa vào DTO nghiệp vụ.
Các endpoint/path chưa được định nghĩa ở task này để tránh giả vờ backend đã phục vụ chúng.

### Ràng buộc phiên và TTL

- Android tạo sessionVerifier bằng nguồn ngẫu nhiên mật mã, 43–128 ký tự unreserved;
  không derive từ sessionId, thời gian hay ID thiết bị.
- sessionChallenge = base64url(SHA-256(ASCII(sessionVerifier))), không padding, 43 ký tự.
  Backend giữ challenge trong phiên; verifier chỉ gửi ở body HTTPS khi đổi mã bàn giao.
- Backend so sánh challenge tái tính với phiên bằng so sánh an toàn; sessionId một mình không đủ.
  Cặp này ràng buộc bàn giao app/backend, KHÔNG thay state hoặc PKCE với provider.
- State ngẫu nhiên, một lần, gắn phiên/provider/redirect. Sai state/phiên không được đổi code provider.
- PKCE provider dùng verifier riêng khi được hỗ trợ; không tái dùng verifier bàn giao.
- Mã bàn giao ngẫu nhiên, dùng một lần, TTL 60 giây tính từ lúc phát hành.
  Tại now >= expiresAt phải từ chối. Đồng hồ server là nguồn quyết định.
- Kiểm tra hạn/phiên/verifier và đánh dấu mã đã dùng phải nguyên tử; chỉ một yêu cầu đồng thời thắng.
- Nếu mất response sau khi đã đổi mã thành công, không cho đổi mã lần hai; bắt đầu phiên mới.
- Không token/refresh token/client secret trong redirect. Mã bàn giao cũng phải che khỏi log.
- Token provider tạm thời chỉ sống tới khi đổi mã/hết hạn; backend không lưu token dài hạn.
- Restart làm mất phiên bộ nhớ thì yêu cầu kết nối lại. Nhiều instance cần kho phiên dùng chung.
- Validation DTO chỉ kiểm tra hình dạng; session store, hash, state, replay và TTL enforcement
  phải được triển khai/kiểm thử trong SP-010 rồi tái dùng ở SP-011.

OAuthFailure phân biệt INVALID_STATE, INVALID_SESSION, INVALID_REDIRECT, HANDOFF_EXPIRED,
HANDOFF_ALREADY_USED, ACCESS_DENIED, AUTH_REQUIRED, PROVIDER_ERROR.
Không đưa response thô provider hoặc bí mật vào lỗi. Mapping HTTP được chốt khi thêm endpoint.

## 7. Ngắt kênh và ownership

Thứ tự: vô hiệu revision → chặn công việc mới → yêu cầu hủy → dọn dữ liệu →
bỏ kết quả revision cũ. Việc kiểm tra revision và ghi dữ liệu phải đồng bộ/transaction
để callback đã vượt qua kiểm tra không ghi lại dữ liệu sau khi xóa.

| Dữ liệu | Xử lý | Owner |
|---|---|---|
| Token | Xóa kho bí mật/tham chiếu | Minh phối hợp phần lưu bí mật |
| Job chưa gửi | Hủy | Minh |
| Publish SUCCESS/PUBLISHED_URL_PENDING/UNKNOWN | Đề xuất giữ tombstone tối thiểu tối đa 30 ngày; UNKNOWN không retry, chờ N1 duyệt | Minh |
| Comment/snapshot riêng kênh | Xóa cục bộ, chặn callback ghi lại | Nghị |
| Tác vụ AI kênh | Hủy Future + HTTP, chặn callback | Đạt |
| Bản nháp/nguồn chung | Giữ, gỡ liên kết kênh theo ownership | Thiện |
| Kênh khác | Giữ nguyên | Mỗi owner |

**Đề xuất N1 — CHỜ DUYỆT, chưa phải chính sách:** Khi ngắt kênh, vô hiệu revision
ngay, chặn và xóa job chưa gửi; xóa token/tham chiếu bí mật, OAuth state, comment,
snapshot, cache và chi tiết lịch sử publish đã có kết quả cuối. Giữ bản nháp/phiên bản
chung, nhưng gỡ liên kết kênh. Không xóa bài đã đăng trên provider.

Để tránh gửi lại bài đã đăng, đề xuất giữ tombstone cục bộ tối đa 30 ngày tính từ lúc
ngắt kênh cho các request SUCCESS, PUBLISHED_URL_PENDING, đang gửi hoặc UNKNOWN;
không giữ request REJECTED hoặc đã xác nhận chắc chắn không tạo bài. Giữ tombstone
SUCCESS đến hết TTL dù đã đối soát; xóa sớm UNKNOWN/PUBLISHED_URL_PENDING chỉ khi
đối soát xác nhận không tạo bài. Người dùng vẫn có thể yêu cầu xóa ngay, nhưng phải
cảnh báo rằng thao tác đó bỏ mất bảo vệ dedupe cho request tương ứng. Tombstone chỉ gồm requestId, channelId nội bộ,
approvedVersionId, revision, outcome và postId nếu đã biết; không gồm token, nội dung,
media, comment, snapshot, response thô hay thông tin đăng nhập provider. Phải báo rõ
phần giữ tạm này trong luồng ngắt kênh. Mốc 30 ngày là đề xuất để review, chưa được duyệt.

Reconnect cùng channelId tạo revision mới, không khôi phục job cũ và không tự retry.
Nếu còn tombstone SUCCESS, chặn enqueue cùng approvedVersionId; PUBLISHED_URL_PENDING
và UNKNOWN cần đối soát tường minh, tuyệt đối không tự retry. Reconnect sang tài khoản
khác tạo channelId mới và không nhận tombstone cũ. Sau khi tombstone hết hạn hoặc bị
xóa ngay, không còn căn cứ tự động đối soát/chống trùng cho request đó; yêu cầu người
dùng kiểm tra bài trên provider và xác nhận trước một lần gửi mới. Nếu Đạt/Nghị xác
nhận F1.B4 không cho phép giữ tombstone sau ngắt kênh, phương án thay thế là xóa cả
tombstone ngay và giữ cảnh báo/xác nhận người dùng như trên; không trình bày chế độ đó
như tự động bảo đảm không đăng trùng.

**N1 — còn mở, theo dõi tại SP-009 / issue #11:** Minh chịu trách nhiệm tổng hợp
quyết định cùng Đạt và Nghị trước khi triển khai lưu/xóa hồ sơ trong adapter/queue.
Phương án tombstone ở trên cần được cả ba xác nhận cụ thể theo F1.B4 và F5.B3; TTL
hữu hạn hoặc bỏ token không tự tạo ngoại lệ cho yêu cầu xóa dữ liệu. Chưa cho phép
triển khai lưu hồ sơ sau ngắt kênh như chính sách đã duyệt. Nếu PR #66 đóng issue #11 khi merge trước khi
quyết định được ghi nhận, chuyển N1 sang ticket còn mở, giữ Minh làm owner và dẫn
ticket mới từ mục này. Chưa ghi nhận quyết định chính sách hoặc xác nhận reviewer;
trạng thái issue/project trực tuyến cần được kiểm tra riêng.

## 8. Ma trận kiểm chứng và bàn giao

| Ca | SP-009 | Cần tiếp ở adapter/tích hợp |
|---|---|---|
| SUCCESS thiếu ID/URL | Test factory từ chối | Đối chiếu URL bài thật |
| Thiếu quyền/token hết hạn | Test mã lỗi và hướng xử lý | Phản hồi provider thật |
| Timeout chưa rõ | Test UNKNOWN chỉ RECONCILE | Mất phản hồi sau khi provider nhận |
| Sai state/phiên, mã hết hạn/dùng lại | Định nghĩa lỗi, test wire/validation | MockMvc/session store ở SP-010 |
| Hủy/callback muộn | Quy tắc revision và ownership | HTTP cancellation + transaction/race test |
| Wire OAuth | Cùng fixture test Android Gson/backend Jackson | HTTPS/callback thiết bị thật |

Chạy tại android: .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug.
Chạy tại backend: .\gradlew.bat test bootJar.
Không gọi các test DTO là test chống replay/state/TTL hoạt động; chưa có session implementation.

Đạt nhận phiên/bí mật/lỗi, Thiện nhận approvedVersionId/PublishResult, Nghị nhận channel/capability/lỗi.
Chờ xác nhận của ba bên, hai human approvals và PR merge trước khi đánh dấu ticket Hoàn tất.
Chỉ dẫn bàn giao và kết quả kiểm tra nằm trong [hồ sơ SP-009](sp-009-verification.md).
