# SP-018 — Kiểm chứng Facebook comment, reply và metrics

Ngày cập nhật 12/11/2026 (Asia/Saigon). Owner Tô Lãng Nghị (`bob35306`).
[Issue #20](https://github.com/nigPeter2503/Social_Pilot/issues/20) · [PR #75](https://github.com/nigPeter2503/Social_Pilot/pull/75) · [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703) · [Review của Thiện](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5864307971).

**Hồ sơ và fixture sẵn sàng để review phạm vi Draft. SP-018 có 0 lượt gọi Graph API thật. Issue #20 còn mở.** Mọi version thực dùng, mã phản hồi và bằng chứng live của SP-018 hiện chưa có.

## 1. Phạm vi và revision

- Nhánh `feat/sp-018-facebook-comments-metrics-spike` khớp head review `943e89a` trước khi nhập `origin/main` tại `e51dfc3375e389ed39b7ae18d44a8c7525d88cc1`. Đã kiểm tra revision main này là ancestor của HEAD. Xem head hiện tại và diff trong [PR #75](https://github.com/nigPeter2503/Social_Pilot/pull/75).
- SP-009 đã merge qua [PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66). Dùng [Social v1](social-contract.md) cho operation, capability và lỗi.
- [SP-010/#12](https://github.com/nigPeter2503/Social_Pilot/issues/12) và [PR #74](https://github.com/nigPeter2503/Social_Pilot/pull/74) đang mở. Head #74 khi đối chiếu là `cd03134aab44e9448bebc3e5783822e699c6394c`. Xem [hồ sơ SP-010](https://github.com/nigPeter2503/Social_Pilot/blob/cd03134aab44e9448bebc3e5783822e699c6394c/docs/drafts/sp-010-implementation-status.md).
- [SP-020/#22](https://github.com/nigPeter2503/Social_Pilot/issues/22) đã đóng sau khi [PR #67](https://github.com/nigPeter2503/Social_Pilot/pull/67) được squash merge vào `main` tại `e51dfc3`. Đã đối chiếu [hợp đồng analytics](analytics-data-contract.md) và hồ sơ/fixture SP-020 trên main khớp với head PR `cdc633a85a99167e391b2e238f64378c085152a6`.
- Lượt này cập nhật hồ sơ Facebook và rà bộ Node offline. Đề xuất bổ sung contract dùng chung đi qua SP-020. Inbox/sync production, OAuth, Android UI, Room và LinkedIn nằm ngoài phạm vi PR.

## 2. Đầu vào SP-010 và điều kiện chạy thật

- Minh xác nhận **Social Management MD** (App ID `1650819950076669`), **Development mode**, tài khoản thử có vai trò **Admin**. Page thử **Demo Page MD 24-31** có ID `1427548503779474`. Post ID `1427548503779474_122109132231532657`, tạo lúc `2026-11-11T06:58:10+0000` (UTC). Nội dung theo Minh là “Bài đăng kiểm thử SocialPilot - 11/11/2026”. [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703) · [Permalink bài thử](https://www.facebook.com/122109132717532657/posts/122109132231532657).
- SP-010 đã POST bài trong Graph API Explorer bằng **v27.0**, GET đọc lại và xác nhận permalink đúng Page/bài. [Cấu hình backend ở head #74](https://github.com/nigPeter2503/Social_Pilot/blob/cd03134aab44e9448bebc3e5783822e699c6394c/backend/src/main/resources/application.properties) mặc định `v27.0`, ghi đè qua `FACEBOOK_GRAPH_API_VERSION`. Kết quả POST/GET là bằng chứng về đối tượng thử. Version của từng request SP-018 sẽ lấy từ URL đã thực gọi và ghi trong evidence.
- **Bằng chứng lịch sử của User token** theo ảnh Debugger của SP-010 gồm `public_profile`, `pages_show_list`, `business_management`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_posts`. `GET /me/accounts?fields=id,name,tasks,access_token` đã trả đúng Page với các task `CREATE_CONTENT`, `MANAGE`, `MODERATE`, `MESSAGING`, `ANALYZE`, `ADVERTISE`, `MANAGE_LEADS`. [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703).

| Đầu vào còn thiếu | Người mở chặn và bước tiếp theo |
|---|---|
| Cách truy cập Page token thử và phạm vi đọc được duyệt | Minh và Nghị thống nhất trực tiếp qua kênh riêng đã được nhóm duyệt. Minh có thể cấp quyền truy cập kho bí mật được duyệt hoặc thực thi từ phiên Explorer của mình và bàn giao evidence đã che. Kho/kênh cụ thể còn chờ Minh xác nhận. |
| Hồ sơ Page token hiện tại | Minh + Nghị kiểm tra loại PAGE, validity, expiry và data-access expiry nếu có, App ID, Page ID/token target, app mode/access level, vai trò và Page tasks hiện tại. Đối chiếu quyền thực cấp cho từng request trên đúng Page. |
| Quyền riêng cho reply và insights | Minh kiểm tra riêng `pages_manage_engagement` cho FB-03 và `read_insights` cho FB-08. SP-010 chưa ghi nhận hai quyền này. Xác nhận task tương ứng và các quyền phụ thuộc với tài liệu Meta cho version thực dùng. |
| Đầu vào ghi và ca thiếu quyền | Minh xác nhận comment ID, nội dung reply và quyền ghi hiện tại trước FB-03. Với FB-10, Minh duyệt credential thử giới hạn và thao tác đọc phù hợp. |

## 3. Đối chiếu nguồn Meta ngày 12/11/2026

| Nguồn | Kết quả đối chiếu |
|---|---|
| [Comments](https://developers.facebook.com/docs/graph-api/reference/object/comments/), [Page](https://developers.facebook.com/docs/graph-api/reference/page/), [Pages posts](https://developers.facebook.com/docs/pages-api/posts/) | HTTP 429 khi tải tài liệu. Cần đối chiếu lại edge, field, filter và điều kiện truy cập trong phiên Meta của Minh. |
| [Permissions](https://developers.facebook.com/docs/permissions/), [Graph API changelog](https://developers.facebook.com/docs/graph-api/changelog/), [Insights](https://developers.facebook.com/docs/graph-api/reference/insights/), [Changelog v25.0](https://developers.facebook.com/docs/graph-api/changelog/version25.0/) | HTTP 429 khi tải tài liệu. Trang [metric đã bỏ của Pages API](https://developers.facebook.com/documentation/pages-api/platforminsights/page/deprecated-metrics/) cũng chưa tải được. Chưa xác nhận quyền, metric/period theo loại bài hoặc version hiện hành từ tài liệu này. |
| SDK chính thức của Meta ([Comment](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/comment.py), [PagePost](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/pagepost.py), [Page](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/page.py)) | Đọc được khai báo GET/POST `/comments`, GET `/reactions`, GET `/insights` với tham số `metric` và `period`, cùng các trường `shares`, `full_picture`, `attachment` và `followers_count`. Đây là căn cứ cho request ứng viên. Quyền thực cấp, metric/period hợp lệ và hành vi trên bài thử cần bằng chứng riêng. |

Các HTTP 429 trên là khi mở **trang tài liệu**. FB-10 dùng fixture để kiểm tra rate limit.

## 4. Ma trận FB-01–10

Các request dưới đây là ứng viên dưới `https://graph.facebook.com/{version}`. Tất cả ca live đang **NOT_RUN**, vì vậy version thực dùng, HTTP/code/subcode và evidence live đều chưa có. Cột offline chỉ ghi phạm vi fixture đã có. Ghi quyền granted theo từng ca, kiểm tra trên đúng Page và `connectionRevision`.

| Ca | Request / quyền ứng viên | Expected | Kết quả và bước tiếp theo |
|---|---|---|---|
| FB-01 | GET `/{post-id}/comments?fields=id,message,created_time,from,parent&limit={limit}`. Page token, `pages_read_engagement` và `pages_read_user_content` theo loại nội dung. | Đọc comment và đối chiếu đúng Page/bài. Ghi riêng các trường vắng và phân biệt trang rỗng hợp lệ với lỗi quyền. | **NOT_RUN**. Minh xác nhận phạm vi đọc và Page token, Nghị đối chiếu Page/bài và chạy. **MO_PHONG** có projection comment và trường vắng. |
| FB-02 | GET cùng edge/filter/order/fields, thêm `after={cursor}`. Quyền như FB-01. | Khi đủ comment, lấy ít nhất hai trang thật, ghi điều kiện dừng và số ID trùng/duy nhất. Nếu chỉ có một trang, ghi số comment quan sát và giới hạn kiểm chứng. | **NOT_RUN**. Sau FB-01, Nghị kiểm tra bài có đủ comment cho hai trang. Minh xác nhận bài thử phù hợp nếu dữ liệu thiếu. **MO_PHONG** có overlap, dedup và trang cuối. |
| FB-03 | POST `/{comment-id}/comments` với `message` đã duyệt. Kiểm tra `pages_manage_engagement` riêng và task/quyền phụ thuộc. | Gửi đúng một lần, nhận reply ID và đọc lại reply trên đúng Page/comment cha. Timeout hoặc kết quả mơ hồ → `UNKNOWN` và đối soát trước mọi quyết định gửi tiếp. | **NOT_RUN**. Minh cần xác nhận comment ID, nội dung và quyền ghi, rồi Nghị mới gửi. **MO_PHONG** có accepted cần đối soát, denied và unknown. |
| FB-04 | GET `/{page-id}?fields=id,followers_count`. Page token, `pages_read_engagement` ứng viên. | Đọc follower của đúng Page tại thời điểm thu. Giữ 0 nếu được trả. | **NOT_RUN**. Minh xác nhận token/quyền đọc Page, Nghị chạy riêng. **MO_PHONG** giữ follower 0. |
| FB-05 | GET `/{post-id}/reactions?summary=true&limit=1`. `pages_read_engagement` ứng viên. | Đọc `summary.total_count` và xác minh loại reaction được đếm, gồm cách tính like. | **NOT_RUN**. Minh xác nhận quyền đọc, Nghị đối chiếu summary và loại reaction. **MO_PHONG** chỉ có hình dạng phản hồi summary. |
| FB-06 | GET `/{post-id}/comments?summary=true&limit=1`. Quyền như FB-01. | Xác minh `summary.total_count`, filter và phạm vi root/reply, rồi mới map định nghĩa số comment. | **NOT_RUN**. Minh xác nhận quyền đọc, Nghị đối chiếu filter và phạm vi đếm với Page. **MO_PHONG** chỉ có hình dạng summary với 0. |
| FB-07 | GET `/{post-id}?fields=id,shares`. `pages_read_engagement` ứng viên. | Xác minh `shares.count` và phạm vi đếm. Khi field vắng trong response thành công, ghi `PROVIDER_OMITTED`. | **NOT_RUN**. Minh xác nhận quyền đọc, Nghị chạy độc lập và kiểm tra field. **MO_PHONG** kiểm tra share vắng cho null. |
| FB-08 | GET `/{post-id}/insights?metric={metric}&period={period}` riêng từng metric. Kiểm tra `read_insights` riêng, `pages_read_engagement` và Page task phân tích theo tài liệu. | Xác nhận tên, định nghĩa, đơn vị, entity, period/start/end, breakdown và tương thích với loại bài chữ. Ghi riêng từng kết quả. | **NOT_RUN**. Minh xác minh quyền và tài liệu Meta hiện hành. Nghị chốt cặp metric/period rồi mới gọi. Các tên views/viewers còn là ứng viên, chưa xác nhận impression tương đương. |
| FB-09 | GET `/{post-id}` với metadata cần cho UI, và GET `/{comment-id}` khi có comment thử. Quyền theo field, loại đối tượng và access level. | Thử `id`, `created_time`, `permalink_url`, `full_picture` của bài và `id`, `parent`, `from`, `attachment` của comment khi cần. Ghi field vắng và giới hạn riêng của bài chữ. | **NOT_RUN**. Thiện xác nhận trường tối thiểu, Minh xác nhận quyền, Nghị kiểm tra từng nhóm field. **MO_PHONG** có ID/parent/tác giả nullable ở projection comment. |
| FB-10 | Lặp một phép đọc phù hợp bằng credential thử giới hạn đã được Minh duyệt. Rate limit dùng fixture. | Lỗi thiếu quyền khác empty success và 0. Ghi HTTP/code/subcode. | **NOT_RUN** cho thiếu quyền live. Minh duyệt credential/ca, Nghị chạy và đối chiếu. **MO_PHONG** kiểm tra lỗi quyền, token hết hạn và rate limit (HTTP 429/code 4), giữ 403 chưa phân loại ở `UNVERIFIED`. |

### Cách ghi bằng chứng khi chạy

1. Xác minh Page token và phạm vi từng ca trước lượt gọi. Dùng header xác thực trong công cụ thử. Chạy các phép đọc độc lập để lỗi ở một field/metric vẫn có kết quả riêng.
2. FB-02 giữ nguyên scope/revision, post và query fingerprint. Cursor opaque chỉ giữ trong phiên thử. Nếu có `paging.next` nhưng thiếu cursor, ghi chưa xác nhận điểm kết thúc. Ghi số trang, số ID overlap, số duy nhất và tín hiệu trang cuối. Dùng tín hiệu kết thúc của edge đã xác minh.
3. FB-03 gửi một lần sau xác nhận của Minh. Response có reply ID là accepted cần đối soát. Lưu reply ID thử trong evidence đã che hoặc tham chiếu riêng, kiểm lại nội dung, tác giả Page và comment cha trên đúng Page. Nếu `UNKNOWN`, đối soát trước và ghi trạng thái, không tự retry.
4. Ghi mỗi request, kể cả preflight và GET đối soát, với `caseId`, `API_THAT`, revision, môi trường/app mode/access level, người thử, thời điểm UTC, scope/connectionRevision, Page tasks, quyền thực cấp, version, endpoint/method, expected, actual, HTTP/code/subcode và link evidence đã che. Với metrics ghi thêm kỳ provider và các trường ở mục 6. Với reply ghi xác nhận đầu vào, thời điểm gửi, tham chiếu reply/parent và thời điểm đối soát.
5. Che token, secret, cookie, Authorization, paging URL/cursor thật và dữ liệu cá nhân khỏi tài liệu, ảnh và log chia sẻ. Nếu cần tham chiếu ID nhạy cảm để đối soát, dùng mã tham chiếu đã che trong hồ sơ.

## 5. Capability Social v1

| Operation | Tình trạng trên kênh/revision thử | Bằng chứng cần có |
|---|---|---|
| `READ_COMMENTS` | `UNVERIFIED` | FB-01 đọc đúng Page/bài. FB-02 ghi riêng coverage phân trang. |
| `REPLY_COMMENT` | `UNVERIFIED` | FB-03 có reply ID và đối soát trên đúng Page/comment cha. |
| `READ_FOLLOWER_COUNT` | `UNVERIFIED` | FB-04 có `followers_count` hợp lệ, gồm 0, và thời điểm thu. |
| `READ_POST_METRICS` | `UNVERIFIED` | FB-05–08 có kết quả đọc hợp lệ trên đúng bài. Ghi trạng thái từng metric riêng. |

Cả bốn thao tác đang chờ Page token và bằng chứng SP-018, nên `verifiedAt` để trống. Chuyển trạng thái theo bằng chứng của đúng thao tác, kênh và revision. Theo Social v1, `AVAILABLE` cần `verifiedAt`, `PERMISSION_REQUIRED` và `UNSUPPORTED` cần `reasonCode` cùng `verifiedAt`. Với `UNVERIFIED`, ghi `reasonCode` giải thích đầu vào còn thiếu. Lỗi credential giới hạn ở FB-10 mô tả lần thử của credential đó. Một metric thiếu trong response thành công dùng `PROVIDER_OMITTED` và giữ capability hiện có.

## 6. Bàn giao mapping Facebook cho SP-020

Thiện [xác nhận các trường UI và tài liệu + fixture đủ cho W1 ở draft-2](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5847811439). [Minh](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5861020207) và [Thiện](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5862180313) đã approve head `cdc633a` của contract trước squash merge. Bảng dưới áp dụng kiểu dữ liệu đã merge. Tên provider, định nghĩa đếm, phạm vi/kỳ thực tế của Facebook và cách hiển thị cụ thể còn chờ SP-018 live và Thiện xác nhận. Nếu cần lưu nhiều kỳ của cùng metric trong một đợt, Đạt xác nhận khóa sample, thời gian và thứ tự tích hợp qua SP-020/SP-012 trước khi đổi schema.

| Ca / metric key | Định nghĩa và đơn vị đề xuất | Kind / entity | Kỳ và mốc quan sát |
|---|---|---|---|
| FB-04 / `FOLLOWER_COUNT` | `followers_count`, số tài khoản theo dõi Page, `COUNT`. | `GAUGE` / `CHANNEL` | Không có kỳ cho số đo điểm. Nếu provider thiếu mốc đo, `observedAt = collectedAt` và basis `COLLECTION`. |
| FB-05 / `REACTION_COUNT` | `reactions.summary.total_count`, số reaction theo phạm vi edge/filter đã xác minh, `COUNT`. Tính like một lần theo định nghĩa nguồn. | `CUMULATIVE` / `POST` khi summary xác nhận tổng hiện tại | Không gán kỳ. Dùng mốc provider nếu có, nếu vắng dùng basis `COLLECTION`. |
| FB-06 / `COMMENT_COUNT` | `comments.summary.total_count`, số comment theo filter và phạm vi root/reply đã xác minh, `COUNT`. Đổi definition nếu phạm vi thay đổi. | `CUMULATIVE` / `POST` khi summary xác nhận tổng hiện tại | Không gán kỳ. Dùng mốc provider nếu có, nếu vắng dùng basis `COLLECTION`. |
| FB-07 / `SHARE_COUNT` | `shares.count`, số lượt chia sẻ bài theo định nghĩa provider, `COUNT`. | `CUMULATIVE` / `POST` khi field xác nhận tổng hiện tại | Không gán kỳ. Dùng mốc provider nếu có, nếu vắng dùng basis `COLLECTION`. |
| FB-08 / `IMPRESSION_COUNT` | Số impression theo định nghĩa provider, `COUNT`. Chưa có metric tương đương được xác minh. Hiện `null / UNVERIFIED`. | `POST`, chờ chốt `PERIOD` hoặc `CUMULATIVE` theo định nghĩa nguồn | Chờ period/start/end và mốc provider. |
| FB-08 / metric views hoặc unique viewers | Số lượt xem hoặc số tài khoản xem duy nhất, mỗi loại có definition riêng, `COUNT`. `post_media_view` và `post_total_media_view_unique` là tên ứng viên cần xác minh, chưa có mapping được duyệt. | `POST`. Tổng lifetime nếu được xác nhận là `CUMULATIVE`. Khoảng hữu hạn được provider xác nhận là `PERIOD`. | Giữ nhãn kỳ provider như trả về. Với `PERIOD` cần start/end thật; chỉ nhận basis `PROVIDER` khi có mốc provider. Nếu thiếu ranh giới thì giữ lý do thiếu, không dựng kỳ. |

Mỗi `MetricSample` mang `scope = {platform: FACEBOOK, channelId, connectionRevision}`, `entityType/entityId`, `definitionId`, `collectionId`, `observedAt/observedAtBasis`, `collectedAt` UTC tại lúc nhận, `providerPeriodStart/End` khi có, `providerApiVersion`, `source` và `sourceRef`. `MetricDefinition` giữ `metricKey`, `providerMetricName`, `unit`, `measurementKind` và định nghĩa đếm. Ghi nhãn period gốc và breakdown trong evidence. Việc mang nhãn này sang DTO, thêm metric views/viewers hoặc khóa lưu nhiều kỳ là đề xuất qua SP-020, chờ Thiện và Đạt ở phần liên quan.

Giữ `0` với `missingReason = null`. Giá trị null có lý do theo mục 3 của contract. Khi lần thu mới lỗi, giữ giá trị cache và timestamp của mẫu tốt, đồng thời trả freshness (`isStale`, `lastSuccessAt`), `lastAttemptAt` và lỗi/trạng thái offline của lần mới nhất. Giữ provenance từng sample. Với `PERIOD`, giữ khoảng UTC nửa mở `[start,end)` theo provider. Chỉ hiển thị tuần đủ khi có kỳ đúng hoặc cặp mốc thực đáp ứng coverage trong SP-020. Đo tại giờ thu chỉ cho mốc quan sát. ER theo impression chỉ tính khi ba thành phần engagement và mẫu số impression cùng định nghĩa/phạm vi/khoảng đo tương thích và mẫu số dương. Views/viewers giữ định nghĩa riêng.

Projection comment bàn giao giữ scope, post, comment và parent riêng. Thiếu parent giữ `UNKNOWN`, thiếu message giữ `OMITTED`. FB-09 xác định metadata/ảnh nullable trước khi Thiện chốt UI. Đạt còn cần xác nhận thiết kế lưu trữ nếu mapping kỳ đo ảnh hưởng schema.

## 7. Kiểm tra offline và bàn giao

- [Fixture](api-verification/facebook/fixtures.json) dùng ID, nội dung và cursor tổng hợp, `apiVersion = null`, provenance `MO_PHONG`.
- [Prototype](api-verification/facebook/response-prototype.cjs) giữ channel/post/comment/parent, không trộn trang giữa scope/revision/query, giữ 0 khác null và phân biệt lỗi với trang rỗng. Heuristic mã lỗi ở đây phục vụ fixture. Wrapper follower `POINT_IN_TIME` trong mẫu offline tương ứng khái niệm `GAUGE` khi bàn giao contract. Chưa có parser insights hoặc tính ER.
- [Kiểm tra fixture](api-verification/facebook/verify-fixtures.cjs) chạy trên Windows, Node v24.19.0, ngày 12/11/2026.

| Lệnh đã chạy | Kết quả |
|---|---|
| `node docs/api-verification/facebook/verify-fixtures.cjs` | 55/55 PASS, `MO_PHONG`, 0 lượt gọi live |
| `node --check docs/api-verification/facebook/response-prototype.cjs` | PASS |
| `node --check docs/api-verification/facebook/verify-fixtures.cjs` | PASS |
| `git diff --check` và kiểm tra diff so với `origin/main` | PASS |

Android/backend build nằm ngoài thay đổi tài liệu và fixture. Lượt review này cần Minh thống nhất kênh cung cấp quyền thử và đầu vào FB-03/FB-10, Thiện xác nhận mapping UI Facebook và Đạt xác nhận nếu cần đổi schema. Điều kiện nghiệm thu [#20](https://github.com/nigPeter2503/Social_Pilot/issues/20) vẫn chờ bằng chứng live.
