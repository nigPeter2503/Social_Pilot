# SP-018 — Chuẩn bị kiểm chứng Facebook comment, reply và metrics

Ngày: 09/11/2026, Asia/Saigon. Owner: Tô Lãng Nghị (`bob35306`).
Ticket: [#20](https://github.com/nigPeter2503/Social_Pilot/issues/20).
Reviewer dự kiến: Minh (`nigPeter2503`), Thiện (`PhuocThien06`).
Trạng thái: **đang làm; phần offline đã chuẩn bị, phần API thật bị chặn**.
API version: **chưa chốt**. Số phép gọi Facebook thật trong phiên này: **0**.

## 1. Phạm vi và revision

- Nhánh `feat/sp-018-facebook-comments-metrics-spike`, tạo từ `origin/main`
  `0348982c8f295d946815da91eae491bd9645ac94` sau fetch ngày 09/11.
- Issue #20 và đầu vào [SP-010/#12](https://github.com/nigPeter2503/Social_Pilot/issues/12)
  còn OPEN; cả hai chưa có bình luận tại lần đọc REST trong phiên này.
  Chưa thấy branch/PR SP-018 hoặc SP-010 trên remote. Project live chưa xác minh;
  công việc chưa push của nhóm chưa biết.
- [SP-009 PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66) vẫn OPEN,
  head `289e4d1`, chưa merge. Main chỉ có SocialConnector publish nền;
  chưa tích hợp code comment/metrics vào interface chung.
- [SP-020 PR #67](https://github.com/nigPeter2503/Social_Pilot/pull/67) OPEN,
  head `bf89da1`: [contract draft-2 tại revision đã đọc](https://github.com/nigPeter2503/Social_Pilot/blob/bf89da1598ca13fd02e2bd116f4f47a5a07c2555/docs/analytics-data-contract.md).
  Đây là đề xuất cần review, chưa phải Java API đã chốt. Không kéo thay đổi SP-020 vào nhánh này.
- Bộ offline dùng Node tích hợp sẵn, không dependency, không HTTP; chỉ để khảo sát
  hình dạng phản hồi và quy tắc mapping. Ứng dụng/backend vẫn dùng Java.
  Không sửa Android/backend/UI/database/build. Chưa có adapter production.

## 2. Đầu vào thực tế và người mở chặn

| Đầu vào | Tình trạng | Người mở chặn |
|---|---|---|
| Contract operation/capability/error và revision tích hợp | SP-009 chưa merge; cần người dùng contract xác nhận | Minh, SP-009/#11 |
| App/Page, loại token, nơi cấu hình an toàn, hiệu lực và quyền cấp cho Page | Chưa được chỉ vị trí; không thấy tên biến môi trường Facebook/Meta trong phiên shell; không kết luận nơi khác không có | Minh, SP-010/#12 |
| Page/post/comment ID và xác nhận được đọc/reply trên đối tượng thử | Chưa được cung cấp hoặc xác nhận; không gọi thật | Minh, SP-010/#12 |
| Version, quyền, metric/period áp dụng cho app và loại bài | Chưa xác minh đầy đủ do tài liệu web trả 429, chưa có đầu vào app | Minh + Nghị tra lại tài liệu trước khi pin |
| Schema và các trường cần cho UI | SP-020 draft-2 còn review | Đạt schema, Thiện UI |

**Phân biệt SP-009 và SP-010:** SP-009 cung cấp hợp đồng OAuth/capability/lỗi;
SP-010 cung cấp kết quả Facebook OAuth và đầu vào thử thật. OAuth hoặc đăng bài thành
công không chứng minh quyền đọc comment, reply, follower hay insights.

Nội dung nhờ Minh chuẩn bị, **chưa gửi**: chỉ vị trí cấu hình an toàn, app mode/access
level, version app đang dùng, token type/expiry và danh sách quyền đã cấp; Page/post/comment
thử, vai trò/Page task của người cấp token; xác nhận phạm vi được đọc và nội dung reply
thử được duyệt. Không gửi token/client secret trong chat/issue/PR.

## 3. Nguồn chính thức và giới hạn khảo sát

Đã thử đọc ngày 09/11/2026:

| Nguồn | Kết quả / phạm vi sử dụng |
|---|---|
| [Comments reference](https://developers.facebook.com/docs/graph-api/reference/object/comments/), [Pages posts](https://developers.facebook.com/docs/pages-api/posts/) | HTTP 429; chưa xác minh quyền/version trực tiếp |
| [Page reference](https://developers.facebook.com/docs/graph-api/reference/page/), [Insights](https://developers.facebook.com/docs/graph-api/reference/insights/) | Page trả 429; đường dẫn insights v25.0 thử đọc không tải được. Chưa chốt tên metric đang hỗ trợ |
| [Permissions](https://developers.facebook.com/docs/permissions/), [Getting started](https://developers.facebook.com/docs/pages-api/getting-started/) | HTTP 429; danh sách quyền bên dưới là ứng viên phải đối chiếu lại |
| [Results/pagination](https://developers.facebook.com/docs/graph-api/results/), [Errors](https://developers.facebook.com/docs/graph-api/guides/error-handling/) | HTTP 429; quy tắc cursor và mã lỗi trong prototype chỉ là giả thuyết kiểm thử |
| [SDK Meta: Comment](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/comment.py) | Đọc được nguồn chính thức: có field id/message/from/created_time/parent và thao tác GET/POST comments. Chỉ xác nhận cấu trúc SDK, chưa xác nhận app được phép gọi |
| [SDK Meta: PagePost](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/pagepost.py) | Đọc được GET comments/reactions/insights, field shares; dùng để lập request ứng viên, không chốt metric/version/permission |
| [SDK Meta: Page](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/page.py) | Đọc được field followers_count. Field có trong SDK không chứng minh API version/app hiện tại sẽ trả nó |

Không dùng nguồn bên thứ ba để khẳng định metric đang hoạt động hoặc version mới nhất.
Các link SDK trỏ main có thể đổi; khi thử thật cần ghi revision/version đã dùng cùng evidence.

## 4. Ma trận request và bằng chứng cần thu

Mọi endpoint dưới đây là **ứng viên**, dùng prefix `https://graph.facebook.com/{version}`.
`{version}` chưa có giá trị; không thay bằng version nhớ từ trước. Auth bằng header trong
công cụ thử, không đưa token vào URL/fixture/log. Trước gọi thật phải đọc lại quyền,
app access level/Page task và version chính thức; ghi quyền granted thực tế theo từng case.

| Case | Request ứng viên | Quyền ứng viên cần đối chiếu | Expected / bằng chứng cần có | Actual hiện tại |
|---|---|---|---|---|
| FB-01 | GET `/{post-id}/comments?fields=id,message,created_time,from,parent&limit={small-limit}` | Page token; pages_read_engagement, pages_read_user_content theo loại nội dung | Comment đúng bài/Page; created_time, nội dung, tác giả/parent nếu được trả; đối chiếu UI. Thu hẹp field nếu field không hỗ trợ, ghi kết quả riêng | NOT_RUN — thiếu đầu vào SP-010 |
| FB-02 | GET cùng edge/filter/order/fields, thêm `after={cursor}` | Như FB-01 | Ít nhất hai trang thật khi đủ dữ liệu; cursor cuối, ID overlap, dedup và trường thiếu. Thiếu dữ liệu phân trang ghi hạn chế | NOT_RUN |
| FB-03 | POST `/{comment-id}/comments`, body message đã duyệt | pages_manage_engagement và các quyền phụ thuộc phải kiểm tra lại | Gửi một lần trên comment được phép; ID phản hồi; đọc lại reply và đối chiếu trên Page đúng parent, tác giả, nội dung, thời điểm | NOT_RUN — chưa xác nhận quyền ghi |
| FB-04 | GET `/{page-id}?fields=id,followers_count` | Page token; pages_read_engagement ứng viên | Số follower tại thời điểm thu; 0 là số hợp lệ; không dùng fan_count/connections thay thế | NOT_RUN |
| FB-05 | GET `/{post-id}/reactions?summary=true&limit=1` | pages_read_engagement ứng viên | Kiểm tra summary total_count và định nghĩa reaction; không cộng thêm like vào reaction | NOT_RUN |
| FB-06 | GET `/{post-id}/comments?summary=true&limit=1` | Như FB-01 | Kiểm tra summary total_count, filter và phạm vi root/reply; chưa coi là tổng toàn bài khi chưa xác minh nghĩa | NOT_RUN |
| FB-07 | GET `/{post-id}?fields=id,shares` | pages_read_engagement ứng viên | Kiểm tra count, định nghĩa/phạm vi; shares thiếu không đổi thành 0 | NOT_RUN |
| FB-08 | GET `/{post-id}/insights?metric={verified-metric}&period={verified-period}` | read_insights, pages_read_engagement và Page task ứng viên | Chọn từng metric từ docs hiện hành; ghi tên, định nghĩa, unit, period/start/end, breakdown và thời điểm thu | NOT_RUN — metric chưa chốt |
| FB-09 | GET metadata bài/comment với các field ảnh/định danh được docs hiện hành cho phép | Theo field/account/access level | Thử riêng from/name, full_picture hoặc attachment khi cần UI; có thể vắng; không lấy thông tin cá nhân ngoài nhu cầu | NOT_RUN |
| FB-10 | Lỗi thiếu quyền/token và rate limit | Tài khoản/quyền thử đã duyệt hoặc fixture có nhãn | Lỗi khác empty success, 0, unsupported; không cố gây rate limit thật | MO_PHONG — chưa API_THAT |

pages_show_list dùng cho khám phá Page khi luồng SP-010 cần, không tự thêm như quyền
đủ cho mọi phép thử SP-018. Mỗi scope/access level phải ghi trạng thái đã cấp/thất bại
thực tế, không tick chỉ vì có trong danh sách ứng viên.

Không pin các tên impression/reach cũ hay tự thay bằng views. Nếu không xác minh được
metric impression tương đương, giữ IMPRESSION_COUNT = null/UNVERIFIED; chưa tính ER.
Không trộn summary counter hiện tại với insights theo kỳ. Lỗi invalid metric không
tự chứng minh metric bị bỏ; cần đối chiếu tên/version/period/quyền và tài liệu.

## 5. Quy trình chạy thật sau khi mở chặn

1. Ghi revision/app mode/version, vai trò/Page task, trạng thái quyền/expiry đã che;
   xác nhận post thuộc đúng Page và phạm vi đọc/ghi. Chỉ ghi nơi cấu hình, không ghi giá trị token.
2. Chốt bảng request từ docs hiện hành; bắt đầu FB-01, FB-04 và các phép đọc tách biệt.
   Ghi HTTP status, mã/subcode an toàn, actual, collectedAt UTC, version và nguồn.
3. FB-02 giữ cùng query fingerprint và connectionRevision; dùng cursor opaque, không
   follow/lưu paging.next. Khi có next mà thiếu cursor: dừng và điều tra, không báo đã đọc hết.
   Cursor chỉ ở bộ nhớ phiên thử; fixture dùng cursor tổng hợp, không công bố cursor thật.
4. FB-03 chỉ sau khi đã xác nhận comment/nội dung thử. Gửi một lần; ID chỉ là accepted,
   chưa phải đã đối chiếu. Đọc lại reply và kiểm tra trên Page. Timeout/5xx/thiếu ID:
   UNKNOWN, đối soát trước, không gửi lại tự động. Không có quyền thì ghi blocker riêng.
5. Thu metric độc lập: định nghĩa/unit/entity/provider name, kỳ đo, collectedAt/sourceRef;
   response thiếu giữ null + reason; 0 được giữ là 0. Ghi unavailable/unsupported chỉ khi có bằng chứng.
6. Che token/Authorization/cookie/paging URL/cursor, ID nhạy cảm và dữ liệu cá nhân trước
   khi lưu bất kỳ response/ảnh/log nào. Prototype dưới đây **không phải công cụ che bí mật**.
7. Bổ sung từng dòng evidence thật; người nhận xác nhận capability và DTO. Chưa đạt live
   thì giữ tiêu chí mở, kể cả khi PR phần chuẩn bị đã được review.

Mẫu evidence cho từng case:

`caseId | source | revision | appMode/accessLevel | PageTask | apiVersion | endpoint/method |
grantedPermissions | observedAtUTC | providerPeriod/start/end | expected | actual |
HTTP/code/subcode | redactedEvidenceRef | verifiedBy | blocker/owner`

Reply thêm `approvedTestRef | requestTime | replyIdRef | parentIdRef |
PageReconciledAt | outcome | noAutomaticRetry` trong phạm vi chia sẻ phù hợp.

## 6. Bộ offline và mapping đề xuất

- [fixtures.json](api-verification/facebook/fixtures.json): hoàn toàn **MO_PHONG**, ID,
  tên, nội dung, thời gian và cursor tổng hợp; apiVersion null. Không có dữ liệu thật.
- [response-prototype.cjs](api-verification/facebook/response-prototype.cjs): parser khảo sát
  offline; chỉ nhận provenance MO_PHONG, không mạng/credential/Room/UI; không dùng để ingest
  dữ liệu thật. Mã này không được đăng ký vào SocialConnector hoặc dùng làm Java API tích hợp.
- [verify-fixtures.cjs](api-verification/facebook/verify-fixtures.cjs): kiểm tra tình huống
  overlap, trang cuối/rỗng/lỗi, scope/revision/query, thiếu trường, timezone sai, null/0,
  số đếm không hợp lệ và kết quả reply chưa rõ. Không chứng minh parser Java/HTTP production.

Projection comment giữ channel/post/comment/parent, createdAt/syncedAt, source/sourceRef,
content state và replyCapability UNVERIFIED. Thiếu parent không suy thành ROOT;
thiếu message không suy DELETED. Merge chỉ cùng channel/post/revision/query; khóa chống
trùng platform/channel/comment; lần quan sát mới hơn thắng, không suy providerUpdatedAt.
Đây chưa có transaction, tombstone hoặc bảo vệ race với disconnect của app.

Metric wrapper giữ definition/entity/measurementKind/collectedAt/source và value/reason;
chưa có parser insights hoặc bộ tính ER. Error mapping 190/10/200/4/17/32/613 là heuristic
cho fixture cần xác minh; HTTP 403 hoặc code 100 đơn lẻ giữ UNVERIFIED.
Không chuyển các heuristic này thành capability SUPPORTED/UNSUPPORTED đã kiểm chứng.

## 7. Kiểm tra và bàn giao

Tại repo root:

```powershell
node docs/api-verification/facebook/verify-fixtures.cjs
node --check docs/api-verification/facebook/response-prototype.cjs
node --check docs/api-verification/facebook/verify-fixtures.cjs
git diff --check
```

Kết quả thực thi: xem mục kết quả bên dưới sau khi chạy. Không chạy Android/backend build
vì không sửa code ứng dụng. Java PATH/SDK36 còn vấn đề ghi trong plan; khi thêm Java phải
kiểm tra lại môi trường và chạy assembleDebug/testDebugUnitTest/lintDebug theo CONTRIBUTING.

Chưa gửi bàn giao, tạo PR, đăng review hay cập nhật Project. Minh nhận bảng capability
sau kiểm chứng; Thiện/Đạt nhận DTO/metric definition và phản hồi đã che theo SP-020 được chốt.
Phần còn thiếu: toàn bộ FB-01–09 live; FB-10 thật khi phù hợp; docs/version/quyền hiện hành;
parser Java/HTTP integration nếu được chọn; hai human approvals, checks, merge và tracker.

### Kết quả thực thi local

- Node v24.19.0: **55 checks PASS**, exit 0, toàn bộ MO_PHONG; **0 API_THAT**.
- `node --check` cho cả hai file CJS: PASS, exit 0.
- Bốn file mới và plan: UTF-8/LF/newline/whitespace PASS; ba link local trong hồ sơ
  resolve thành công; kiểm tra whitespace Git bao gồm cả file untracked PASS.
  Lượt QA đầu không chạy được do Python execution alias; dùng Node sẵn có để kiểm tra.
  Lượt Git no-index đầu có exit 1 do file mới và cảnh báo autocrlf; chạy lại với
  core.autocrlf=false chỉ cho lệnh kiểm tra, phân biệt exit khác biệt với lỗi whitespace,
  không sửa cấu hình Git. Không coi fixture PASS là bằng chứng quyền Facebook.
- Fetch/REST lần đầu trong sandbox lỗi DNS; chạy lại với quyền mạng thành công.
  Không coi kết quả giả rỗng từ lần REST lỗi là bằng chứng: đã đọc lại và xác nhận
  chính xác #20 và #12 có 0 bình luận.
- Chưa chạy build Android/backend, HTTP/Java test hoặc gửi reply; chưa đủ đóng #20.
