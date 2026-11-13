# SP-018 — Kiểm chứng Facebook comment, reply và metrics

Ngày cập nhật 13/11/2026 (Asia/Saigon). Owner Tô Lãng Nghị (`bob35306`).
[Issue #20](https://github.com/nigPeter2503/Social_Pilot/issues/20) · [PR #75](https://github.com/nigPeter2503/Social_Pilot/pull/75) · [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703) · [Review của Thiện](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5864307971).

**Đã hoàn tất lượt kiểm chứng live FB-01–10 trên v27.0.** FB-03 đã gửi và đối soát reply sau khi Minh duyệt theo xác nhận của Nghị. FB-08 đọc được hai metric lifetime. FB-10 đạt với cặp Page token cùng app, Page và phạm vi các quyền còn lại. FB-07 ghi `PROVIDER_OMITTED`. PR #75 đang Draft với `Refs #20`. Issue #20 còn mở.

## 1. Phạm vi và revision

- Revision cơ sở của lượt chuẩn bị là `7b148fb1fae849eeeb23355866b535babe4e1597` trên `feat/sp-018-facebook-comments-metrics-spike`, khớp head ghi trong [PR #75](https://github.com/nigPeter2503/Social_Pilot/pull/75). Ghi SHA thực tế riêng cho lượt chạy.
- SP-009 đã merge qua [PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66). Dùng [Social v1](social-contract.md) cho operation, capability và lỗi.
- [SP-010/#12](https://github.com/nigPeter2503/Social_Pilot/issues/12) và [PR #74](https://github.com/nigPeter2503/Social_Pilot/pull/74) đang mở. Head #74 khi đối chiếu là `cd03134aab44e9448bebc3e5783822e699c6394c`. Xem [hồ sơ SP-010](https://github.com/nigPeter2503/Social_Pilot/blob/cd03134aab44e9448bebc3e5783822e699c6394c/docs/drafts/sp-010-implementation-status.md).
- [SP-020/#22](https://github.com/nigPeter2503/Social_Pilot/issues/22) đã đóng sau khi [PR #67](https://github.com/nigPeter2503/Social_Pilot/pull/67) được squash merge vào `main` tại `e51dfc3`. Đã đối chiếu [hợp đồng analytics](analytics-data-contract.md) và hồ sơ/fixture SP-020 trên main khớp với head PR `cdc633a85a99167e391b2e238f64378c085152a6`.
- Lượt này cập nhật hồ sơ Facebook và rà bộ Node offline. Đề xuất bổ sung contract dùng chung đi qua SP-020. Inbox/sync production, OAuth, Android UI, Room và LinkedIn nằm ngoài phạm vi PR.

## 2. Đầu vào SP-010 và điều kiện chạy thật

- Minh xác nhận **Social Management MD** (App ID `1650819950076669`), **Development mode**, tài khoản thử có vai trò **Admin**. Page thử **Demo Page MD 24-31** có ID `1427548503779474`. Post ID `1427548503779474_122109132231532657`, tạo lúc `2026-11-11T06:58:10+0000` (UTC). Nội dung theo Minh là “Bài đăng kiểm thử SocialPilot - 11/11/2026”. [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703) · [Permalink bài thử](https://www.facebook.com/122109132717532657/posts/122109132231532657). Khi mở lại qua HTTP không đăng nhập, URL trả 200 nhưng phần HTML đọc được chỉ có tiêu đề Facebook. Việc đối chiếu nội dung và Page vẫn dựa trên xác nhận của Minh ở SP-010.
- SP-010 đã POST bài trong Graph API Explorer bằng **v27.0**, GET đọc lại và xác nhận permalink đúng Page/bài. [Cấu hình backend ở head #74](https://github.com/nigPeter2503/Social_Pilot/blob/cd03134aab44e9448bebc3e5783822e699c6394c/backend/src/main/resources/application.properties) mặc định `v27.0`, ghi đè qua `FACEBOOK_GRAPH_API_VERSION`. Kết quả POST/GET là bằng chứng về đối tượng thử. Version của từng request SP-018 sẽ lấy từ URL đã thực gọi và ghi trong evidence.
- **Bằng chứng lịch sử của User token** theo ảnh Debugger của SP-010 gồm `public_profile`, `pages_show_list`, `business_management`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_posts`. `GET /me/accounts?fields=id,name,tasks,access_token` đã trả đúng Page với các task `CREATE_CONTENT`, `MANAGE`, `MODERATE`, `MESSAGING`, `ANALYZE`, `ADVERTISE`, `MANAGE_LEADS`. [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703).

- Theo cập nhật của Nghị ngày 13/11, Minh đã thêm Nghị làm **Developer** của **Social Management MD**. Nghị có thể tự chạy trên Page demo. Lượt preflight bên dưới dùng **Demo Page MD 24-31**, cùng Page đã dùng trong bằng chứng của Minh.
- [Cập nhật của Minh trên PR #75](https://github.com/nigPeter2503/Social_Pilot/pull/75) ghi nhận Page token lịch sử của Demo Page MD 24-31 hợp lệ, Expires “Never”, data-access expiry riêng và `/v27.0/me?fields=id,name` khớp Page. SP-010 đã kiểm chứng đăng bài chữ trên v27.0. Báo cáo của Nghị xác định bài kiểm thử ảnh và alt text dùng cho lượt đọc bên dưới. Link bằng chứng gốc về thao tác đăng ảnh còn chờ bổ sung.

### Chuẩn bị Page, bài và credential đọc

1. Nghị tạo hoặc chọn Page demo do mình quản lý. Trên Page, đăng một bài chữ với nội dung `SP-018 — Bài chữ kiểm chứng Graph API v27.0`. Dùng tài khoản của mình để tạo ba comment cấp cao nhất `SP-018 C01`, `SP-018 C02`, `SP-018 C03` nếu bài chưa đủ dữ liệu. Dùng C01 làm comment cha cho FB-03. Giữ bộ comment ổn định trong lượt phân trang. Có thể dùng bài ảnh riêng để kiểm tra trường ảnh ở FB-09 sau khi ghi kết quả của bài chữ.
2. Trong [Graph API Explorer](https://developers.facebook.com/tools/explorer/), chọn **Social Management MD**, **v27.0** và **Get User Access Token**. Cho phép đúng Page demo trong bước consent. Yêu cầu `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`. Kiểm tra app mode và access level trong App Dashboard. Ghi nhận Page tasks như Meta trả về, gồm các task liên quan đến moderation và insights.
3. Với User token, chạy GET `/me/accounts?fields=id,name,tasks`. Chọn Page demo trong mục **User or Page** để lấy Page token. Nếu cần lấy từ `/me/accounts?fields=id,name,tasks,access_token`, giữ toàn bộ response chứa token trong phiên cục bộ.
4. Với Page token thực dùng, kiểm tra bằng [Access Token Debugger](https://developers.facebook.com/tools/debug/accesstoken/). Ghi loại `PAGE`, validity, expiry/data-access expiry nếu có, app ID, scope và granular target theo Page. Chạy GET `/me?fields=id,name` và đối chiếu đúng Page demo. Lặp bước này khi đổi credential, ghi `connectionRevision` hoặc mã tham chiếu lần cấp thử riêng cho mỗi credential. Ghi rõ nếu Explorer chạy độc lập với backend/Android.
5. Lấy Post ID qua GET `/{page-id}/published_posts?fields=id,message,created_time,permalink_url&limit=5`. Đối chiếu nội dung và permalink với bài trên Page. Nếu Page demo của Nghị chưa dùng được, xác nhận phạm vi với Minh trước khi chuyển sang Page của Minh và chạy lại preflight cho Page đó.

### Kết quả preflight do Nghị báo cáo

- Nghị (`bob35306`) chạy bằng Graph API Explorer, **Social Management MD**, **Development**, vai trò **Developer**, Graph **v27.0**. SHA workspace lúc ghi hồ sơ là `7b148fb1fae849eeeb23355866b535babe4e1597`. Giờ UTC chạy, SHA lúc thử nếu khác, access level từng quyền, expiry, data-access expiry và granular targets của lượt này chưa được cung cấp.
- Page thử `1427548503779474` (**Demo Page MD 24-31**). Tasks thực trả gồm `MANAGE_LEADS`, `MODERATE`, `MESSAGING`, `ANALYZE`, `ADVERTISE`, `CREATE_CONTENT`.
- Debugger ghi nhận cả User token và Page token đang hoạt động và cùng có `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`, `public_profile`. Page token đọc hiện thiếu `pages_manage_engagement` và `read_insights`. Ghi riêng grant và chạy lại preflight khi đổi credential.
- Bài được chọn `1427548503779474_122109617565532657`, “Kiểm thử đăng ảnh và alt text SocialPilot - SP-010 - 12/11/2026.”, tạo lúc `2026-11-12T07:15:30Z` (14:15:30 +07:00). Giờ này là thời điểm tạo bài. Comment ID và số comment cấp cao nhất chờ FB-01. Dùng bài ảnh cho lượt đọc hiện tại và ghi đúng loại bài trong kết quả. Kiểm tra tương thích trên bài chữ riêng khi cần.

| Lượt | Request v27.0 và credential | Kỳ vọng | Kết quả `API_THAT` do Nghị báo cáo |
|---|---|---|---|
| Preflight 1 | GET `/me/accounts?fields=id,name,tasks`, User token | Tìm đúng Page và tasks | Thành công, khớp Page và sáu tasks trên. |
| Preflight 2 | GET `/me?fields=id,name`, Page token | `/me` khớp Page demo | Thành công, xác nhận đúng Page `1427548503779474`. |
| Preflight 3 | GET `/me/posts?fields=id,message,created_time&limit=5`, Page token | Đọc bài của Page và nhận diện bài thử | Thành công, trả 4 bài. Nghị chọn bài ảnh nêu trên. |

Nguồn hiện tại là báo cáo của người chạy trong phiên trao đổi ngày 13/11. HTTP status, Graph code/subcode và đường dẫn tới ảnh hoặc JSON đã che chờ người chạy bổ sung. Giữ token và ID tài khoản cá nhân ngoài hồ sơ chia sẻ.

## 3. Đối chiếu nguồn Meta ngày 12/11/2026

Lượt mở lại qua kết nối trực tiếp trả HTTP 200 và đọc được nội dung tài liệu bên dưới. Changelog và ví dụ request dùng `v27.0`. Dự kiến dùng `v27.0` cho SP-018 sau khi xác minh Page token và quyền trên app thử. Version thực dùng sẽ ghi theo từng request.

| Nguồn | Kết quả đối chiếu |
|---|---|
| [Comments](https://developers.facebook.com/docs/graph-api/reference/object/comments/) | GET `/{object-id}/comments` trên bài dùng Page token. Meta ghi tác vụ `MODERATE` để đọc ID comment của Page Post. Với `summary=true`, `filter=toplevel` đếm comment cấp cao nhất, còn `filter=stream` gồm cả reply khi filter áp dụng. Summary có thể khác số comment thực trả. POST lên comment cần Page token, `MODERATE` và `pages_manage_engagement`. Kiểm tra `can_comment` trên comment thử trước khi ghi. |
| [Explorer](https://developers.facebook.com/docs/graph-api/guides/explorer/), [Debug Token](https://developers.facebook.com/docs/graph-api/reference/debug_token/) | Explorer cho phép chọn app theo vai trò. Debugger xác nhận loại credential, validity và grant thực tế. Đối chiếu granular target nếu có và dùng `/me` để xác nhận Page. |
| [Page](https://developers.facebook.com/docs/graph-api/reference/page/), [Page Post](https://developers.facebook.com/docs/graph-api/reference/page-post/), [Pages posts](https://developers.facebook.com/documentation/pages-api/posts) | Đã xác nhận định nghĩa `followers_count`, `shares.count`, `full_picture` và `permalink_url` trong reference. Còn chờ kiểm tra các trường được trả cho đúng Page/bài thử. |
| [Permissions](https://developers.facebook.com/documentation/development/permissions) | `pages_read_engagement` áp dụng cho nội dung do Page đăng và metadata, `pages_read_user_content` cho nội dung người dùng đăng trên Page. `pages_manage_engagement` phụ thuộc `pages_read_user_content` và `pages_show_list`. `read_insights` phụ thuộc `pages_read_engagement` và `pages_show_list`. |
| [Insights](https://developers.facebook.com/docs/graph-api/reference/insights/) | Đọc insights cần Page token, `read_insights`, `pages_read_engagement` và `ANALYZE`. Hai metric Post Media Views được tài liệu liệt kê với `period=lifetime`. `post_media_view` đếm số lần phát hoặc hiển thị, có breakdown `is_from_ads` và `is_from_followers`. `post_total_media_view_unique` đếm người xem duy nhất. |
| [Graph API changelog](https://developers.facebook.com/docs/graph-api/changelog/), [Changelog v25.0](https://developers.facebook.com/documentation/development/graph-api/changelog/version25.0), [Metric đã bỏ của Pages API](https://developers.facebook.com/documentation/pages-api/platforminsights/page/deprecated-metrics/) | Changelog hiển thị `v27.0`. Meta liệt kê việc ngừng hỗ trợ `post_impressions` và `post_impressions_unique`. Hai metric media views có định nghĩa riêng. Giữ `IMPRESSION_COUNT` chưa có nguồn tương đương và ER theo impression chưa tính. |
| SDK chính thức của Meta ([Comment](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/comment.py), [PagePost](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/pagepost.py), [Page](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/page.py)) | Đọc được khai báo GET/POST `/comments`, GET `/reactions`, GET `/insights` với tham số `metric` và `period`, cùng các trường `shares`, `full_picture`, `attachment` và `followers_count`. Đây là căn cứ cho request ứng viên. Quyền thực cấp, metric/period hợp lệ và hành vi trên bài thử cần bằng chứng riêng. |

Lượt trước trình duyệt tra cứu tài liệu Meta trả HTTP 429. Phần kiểm tra rate limit của FB-10 dùng fixture.

## 4. Ma trận FB-01–10

Trong Explorer chọn `v27.0` và method theo bảng, rồi dán phần đường dẫn sau version. Bài ảnh của lượt đọc, reply và insights có `{page-id}` = `1427548503779474`, `{post-id}` = `1427548503779474_122109617565532657` và `{comment-id}` từ FB-01. Dùng credential đã xác minh cho lượt gọi bổ sung.

| Ca | Request / quyền ứng viên | Expected | Kết quả và bước tiếp theo |
|---|---|---|---|
| FB-01 | GET `/{post-id}/comments?fields=id,message,created_time,from,parent&filter=toplevel&limit=2`. Page token, task moderation để đọc ID, `pages_read_engagement` và `pages_read_user_content` theo loại nội dung. | Đối chiếu comment với bài thử. Ghi riêng trang rỗng, field vắng và lỗi quyền. | **PASS** theo JSON người chạy cung cấp. Có 1 comment, trường `parent` vắng. [Bằng chứng FB-01](#fb-01--đọc-comment). |
| FB-02 | GET `/{post-id}/comments?fields=id,message,created_time&filter=toplevel&limit=2`, thêm `after={cursor}` ở trang 2. Quyền như FB-01. | Lấy ít nhất hai trang từ cùng query. Ghi số ID trùng/duy nhất và điều kiện dừng. | **PASS**. Bài ảnh, hai trang, 4 ID duy nhất, 0 trùng. [Bằng chứng FB-02](#fb-02--phân-trang). |
| FB-03 | GET `/{comment-id}?fields=id,can_comment,message,parent,from`, POST `/{comment-id}/comments`, GET reply. Page token, `MODERATE`, `pages_manage_engagement` và quyền phụ thuộc ở mục 3. | Gửi một lần sau khi duyệt, nhận reply ID và đọc lại đúng Page, comment cha và nội dung. | **PASS**. Nghị xác nhận Minh duyệt trước POST. Reply đã đối soát và Debugger xác nhận grant. |
| FB-04 | GET `/{page-id}?fields=id,followers_count`. Page token, `pages_read_engagement` ứng viên. | Đọc follower của đúng Page tại thời điểm thu, gồm cả 0. | **PASS** theo JSON người chạy cung cấp. Page khớp và `followers_count = 1`. [Bằng chứng FB-04](#fb-04--đọc-follower). |
| FB-05 | GET `/{post-id}/reactions?summary=true&limit=1`. Đối chiếu `summary=total_count` có và không có `type=LIKE`. `pages_read_engagement` ứng viên. | Ghi tổng reaction và kết quả có filter LIKE riêng. | **PASS**. Tổng reaction 1, lọc LIKE 1. Cả hai GET bổ sung có `data: []`. |
| FB-06 | GET `/{post-id}/comments?summary=true&filter=toplevel&limit=1` và GET `/{post-id}/comments?summary=true&filter=stream&limit=1`. Quyền như FB-01. | Ghi riêng `summary.total_count` theo filter. | **PASS**. Lượt đầu 1/1. Sau reply, `toplevel=4`, `stream=5`. |
| FB-07 | GET `/{post-id}?fields=id,shares`. `pages_read_engagement` ứng viên. | Ghi `shares.count` khi có. Khi field vắng trong response thành công, ghi `PROVIDER_OMITTED`. | **API_THAT / PROVIDER_OMITTED**. Response có đúng ID bài, thiếu `shares`. Giá trị metric là null. |
| FB-08 | GET `/{post-id}/insights?metric=post_media_view&period=lifetime` và GET `/{post-id}/insights?metric=post_total_media_view_unique&period=lifetime`. Page token, `read_insights`, `pages_read_engagement`, `ANALYZE`. | Ghi riêng tên, giá trị, đơn vị, entity, period/start/end, breakdown nếu có và loại bài đã thử. | **PASS** trên bài ảnh. Views 17, người xem duy nhất 3. Debugger xác nhận grant của Page token. Đã lưu lỗi credential trung gian `190/2069032`. |
| FB-09 | GET `/{post-id}?fields=id,created_time,permalink_url,full_picture` và GET `/{comment-id}?fields=id,parent,from,attachment`. Quyền theo field, loại đối tượng và access level. | Ghi metadata thực trả trên bài ảnh và comment thử. Nếu truy vấn nhóm lỗi, tách field để xác định phạm vi. | **PASS**. Bài ảnh có đủ bốn trường đã hỏi. Comment có `id` và `from`, vắng `parent` và `attachment`. |
| FB-10 | Lặp đúng GET của FB-04 bằng Page token riêng thiếu `pages_read_engagement`. | Ghi HTTP/code/subcode và quyền cả hai lượt. Xác minh các điều kiện đối chứng. | **PASS**. Lượt dương trả 1, lượt âm với `PAGE-NO-ENG-03` trả `OAuthException #100`. Cùng app, Page, loại token, version, query và phạm vi các quyền còn lại. HTTP status và subcode không quan sát được trong ảnh. |

### Bằng chứng live do Nghị cung cấp

Tô Lãng Nghị (`bob35306`) báo cáo các lượt thử qua Graph API Explorer v27.0 trên Social Management MD, Demo Page MD 24-31. Preflight ban đầu ghi Development, vai trò Developer, sáu Page tasks và bốn grant ở mục 2. SHA workspace lúc ghi hồ sơ là `7b148fb1fae849eeeb23355866b535babe4e1597`. SHA lúc thử, giờ thực thi, `connectionRevision` và link ảnh đã che còn thiếu trừ khi ghi riêng. HTTP status không quan sát được. Nguồn là JSON đã che trong báo cáo của Nghị.

Báo cáo bổ sung ghi GET `/v27.0/me?fields=id,name` khớp Page `1427548503779474`. Nghị gắn các ảnh Debugger và Explorer bên dưới với credential của từng lượt. `PAGE-NO-ENG-01` là mã tham chiếu cũ cho lượt User token. Hồ sơ dùng `PAGE-NO-ENG-02` để tham chiếu Page token giới hạn từ cặp ảnh mới. Các grant của preflight ban đầu áp dụng cho credential ban đầu. Nghị xác nhận các token cũ đều đã hết hiệu lực. Các ảnh chỉ giữ vùng bằng chứng, đã bỏ token, ID cá nhân và mã truy vết.

#### FB-01 · Đọc comment

- `API_THAT / PASS`. GET `/v27.0/1427548503779474_122109617565532657/comments?fields=id,message,created_time,from,parent&filter=toplevel&limit=2`. Kỳ vọng đọc comment thử trên đúng bài ảnh.
- Trả 1 comment cấp cao nhất, tác giả khớp Page. `parent` vắng. Có `before` và `after`, vắng `paging.next`. Giờ thực thi chưa xác nhận. `created_time` là thời điểm tạo comment. Explorer không hiển thị HTTP status. JSON không có Graph error code/subcode.

```json
{
  "data": [
    {
      "id": "122109617565532657_1365501218844524",
      "message": "Test comment API - SP-010",
      "created_time": "2026-11-13T09:58:56+0000",
      "from": { "name": "Demo Page MD 24-31", "id": "1427548503779474" }
    }
  ],
  "paging": { "cursors": { "before": "[REDACTED]", "after": "[REDACTED]" } }
}
```

#### FB-04 · Đọc follower

- `API_THAT / PASS`. GET `/v27.0/1427548503779474?fields=id,followers_count`. Kỳ vọng Page ID khớp và có số follower. Response trả `1`.
- Ảnh được người chạy ghi nhận lúc `2026-11-13T02:55:08Z` (09:55:08 +07:00). Giờ thực thi chưa xác nhận. Explorer không hiển thị HTTP status. JSON không có Graph error code/subcode.

```json
{ "id": "1427548503779474", "followers_count": 1 }
```

#### FB-02 · Phân trang

`API_THAT / PASS` cho thao tác chuyển trang trên bài ảnh `1427548503779474_122109617565532657`. Trang 2 dùng `after` của trang 1 và vắng `paging.next`. Trang 1 có C01, C02. Trang 2 có C03, C04. Tổng 4 ID duy nhất, 0 trùng theo mã đã che trong báo cáo.

| Request v27.0 | Số mục | `paging.next` | `cursors.after` | `paging.previous` |
|---|---:|---|---|---|
| Trang 1, GET `/{post-id}/comments?fields=id,message,created_time&filter=toplevel&limit=2` | 2 | Có | Có | Vắng |
| Trang 2, cùng GET với `&after=[REDACTED]` | 2 | Vắng | Có | Có |

Nghị báo cáo URL phân trang nguyên bản có chứa access token và xác nhận các token cũ đều đã hết hiệu lực. Hồ sơ lưu cờ có/vắng.

#### FB-05–07 và FB-09 · Số liệu và metadata

Các dòng dưới là `API_THAT` theo JSON đã che do Nghị báo cáo. Dùng mã bài ảnh và comment ở FB-01. Mỗi dòng tương ứng một GET riêng. FB-02 và các GET dưới đây chưa có giờ thực thi hay HTTP status. Báo cáo không có Graph error code/subcode. `created_time` trong payload là giờ tạo đối tượng.

| Ca / request v27.0 | Kỳ vọng | Kết quả / trạng thái |
|---|---|---|
| FB-05 GET `/{post-id}/reactions?summary=true&limit=1` | Đọc tổng reaction | **PASS**. `data: []`, `summary.total_count: 1`, `summary.viewer_reaction: "NONE"`, có cursors đã che. Kết quả mô tả viewer hiện tại và tổng edge. Chưa có bằng chứng loại LIKE hoặc lý do danh sách rỗng. |
| FB-06 GET `/{post-id}/comments?summary=true&filter=toplevel&limit=1` | Đọc summary phạm vi cấp cao nhất | **PASS**. 1 mục khớp comment FB-01, `summary.total_count: 1`, `order: "ranked"`, `can_comment: true`. |
| FB-06 GET `/{post-id}/comments?summary=true&filter=stream&limit=1` | Đọc summary phạm vi stream | **PASS**. Cùng 1 mục, `summary.total_count: 1`, `order: "chronological"`, `can_comment: true`. Hai kết quả chưa phân biệt phạm vi đếm khi có reply. |
| FB-07 GET `/{post-id}?fields=id,shares` | Đọc `shares.count` nếu có | **PROVIDER_OMITTED**. JSON chỉ có `{"id":"1427548503779474_122109617565532657"}`. Giá trị share null. |
| FB-09 GET `/{post-id}?fields=id,created_time,permalink_url,full_picture` | Đọc metadata bài ảnh | **PASS**. ID khớp, `created_time: "2026-11-12T07:15:30+0000"`, có `permalink_url` và `full_picture` đã che. |
| FB-09 GET `/{comment-id}?fields=id,parent,from,attachment` | Đọc metadata comment thử | **PASS**. ID khớp FB-01, `from` khớp Page, vắng `parent` và `attachment`. |
| FB-05 GET `/{post-id}/reactions?summary=total_count&limit=1` | Đọc tổng reaction | **PASS**. `data: []`, `summary.total_count: 1`. Báo cáo đã chuẩn hóa cú pháp JSON. |
| FB-05 GET `/{post-id}/reactions?type=LIKE&summary=total_count&limit=1` | Đọc tổng LIKE | **PASS**. `data: []`, `summary.total_count: 1`. Báo cáo đã chuẩn hóa cú pháp JSON. |
| FB-06 GET `/{post-id}/comments?summary=true&filter=toplevel&limit=1` sau reply | Đọc tổng cấp cao nhất | **PASS**. `total_count: 4`, `order: "ranked"`, `can_comment: true`. |
| FB-06 GET `/{post-id}/comments?summary=true&filter=stream&limit=1` sau reply | Đọc tổng theo stream | **PASS**. `total_count: 5`, `order: "chronological"`, `can_comment: true`. Chênh 1 giữa hai filter phù hợp với reply đã đối soát. Chưa có cặp đo sát trước/sau POST để quy toàn bộ thay đổi cho lần ghi. |

#### FB-03 · Gửi và đối soát reply

| Request v27.0 | Kỳ vọng | Kết quả `API_THAT` |
|---|---|---|
| GET `/122109617565532657_1365501218844524?fields=id,can_comment,message,parent,from` | Xác định comment cha, Page và khả năng reply | ID khớp, `can_comment=true`, `from.id=1427548503779474`. Người chạy xác nhận task `MODERATE`. |
| POST `/122109617565532657_1365501218844524/comments` với message bên dưới | Tạo reply và nhận ID sau khi được duyệt | Trả reply ID `122109617565532657_1449434260015945`. Báo cáo có một POST. Nghị xác nhận Minh đã duyệt comment cha và nội dung trước POST. |
| GET `/122109617565532657_1449434260015945?fields=id,message,from,parent,created_time` | Đối chiếu reply, Page, comment cha và nội dung | Khớp cả bốn. `created_time=2026-11-13T11:03:09Z` (18:03:09 +07:00), là giờ tạo reply. |

Nội dung đã đối soát là “SP-018 — Phản hồi thử nghiệm từ Page demo để xác minh Graph API v27.0.” Bằng chứng duyệt được ghi theo xác nhận của Nghị trong phiên trao đổi. [Debugger của Page token](api-verification/facebook/evidence/page-token-debugger.png) xác nhận `pages_manage_engagement`, `pages_read_user_content` và `pages_show_list` cho Page thử.

#### FB-08 · Post Insights

| Request v27.0 | Kỳ vọng | Kết quả `API_THAT` |
|---|---|---|
| GET `/{post-id}/insights?metric=post_media_view&period=lifetime` | Tổng số lần nội dung được phát hoặc hiển thị | **PASS**. `name=post_media_view`, `period=lifetime`, `values[0].value=17`. |
| GET `/{post-id}/insights?metric=post_total_media_view_unique&period=lifetime` | Tổng số người xem duy nhất | **PASS**. `name=post_total_media_view_unique`, `period=lifetime`, `values[0].value=3`. |
| GET insights, lượt lỗi trung gian | Dùng đúng Page token | OAuth `code=190`, `error_subcode=2069032`, yêu cầu Page Access Token. Báo cáo ghi metric thứ hai thành công ở lượt sau. Chờ xác nhận request, loại token và giờ của lượt lỗi. |

Hai metric thuộc bài ảnh. Response rút gọn chưa có start/end hoặc breakdown. Preflight ghi task `ANALYZE`. [Debugger của Page token](api-verification/facebook/evidence/page-token-debugger.png) xác nhận `read_insights` và `pages_read_engagement` trên credential Nghị dùng cho lượt thành công.

#### FB-10 · Cặp đối chứng follower

Các lượt dùng GET `/v27.0/1427548503779474?fields=id,followers_count`. Kỳ vọng lượt đủ quyền trả số follower và lượt thiếu quyền bị từ chối. Cặp chính là `PAGE-READ-02` và `PAGE-NO-ENG-03`. Hai lượt âm trước là bằng chứng bổ sung.

| Credential tham chiếu | Kết quả `API_THAT` | HTTP / Graph |
|---|---|---|
| `PAGE-READ-02` | Page khớp, `followers_count=1`. [Explorer, 21:54:58 +07:00](api-verification/facebook/evidence/fb-10-follower-success.png) | HTTP không quan sát được. Response thành công theo JSON. |
| `PAGE-NO-ENG-03` | Page token thiếu `pages_read_engagement`, các scope còn lại khớp lượt dương. GET bị từ chối. [Explorer, 22:14:01 +07:00](api-verification/facebook/evidence/fb-10-page-token-matching-page-scope-error.png) | HTTP và subcode không quan sát được. `OAuthException`, `code=100`. |
| `PAGE-NO-ENG-02` | Page token thiếu `pages_read_engagement`. GET bị từ chối. [Explorer, 22:02:33 +07:00](api-verification/facebook/evidence/fb-10-page-token-follower-error.png) | HTTP không quan sát được. `OAuthException`, `code=100`. Không thấy subcode. |
| `PAGE-NO-ENG-01` | Lượt User token bổ sung. Meta báo cần `pages_read_engagement` hoặc Page Public Content Access hoặc Page Public Metadata Access. [Explorer, 21:55:42 +07:00](api-verification/facebook/evidence/fb-10-follower-error.png) | HTTP không quan sát được. `OAuthException`, `code=100`, vắng subcode. |

Ba ảnh Debugger trong bảng dưới ghi cùng app ID `1650819950076669` và trạng thái hợp lệ lúc chụp, cùng expiry `1794585600` và data-access expiry `1802356731`. Giờ trên các link là giờ chụp theo tên ảnh ngày 13/11/2026 (+07:00). Ảnh GET lượt âm của cặp chính tương ứng `2026-11-13T15:14:01Z`.

| Debugger | Loại và phạm vi thực tế | Grant hiển thị |
|---|---|---|
| [Lượt dương, 21:47:40 +07:00](api-verification/facebook/evidence/page-token-debugger.png) | `Page`, ID `1427548503779474`. Bốn quyền Pages có granular target là Page thử. | `read_insights`, `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_engagement`, `public_profile`. |
| [Lượt âm Page token, 22:02:28 +07:00](api-verification/facebook/evidence/fb-10-page-token-limited-debugger.png) | `Page`, ID `1427548503779474`. `pages_show_list` và `pages_manage_engagement` trỏ tới Page thử. `pages_read_user_content` hiển thị áp dụng mọi đối tượng. | `read_insights`, `pages_show_list`, `pages_read_user_content`, `pages_manage_engagement`, `public_profile`. |
| [Lượt bổ sung User token, 21:55:32 +07:00](api-verification/facebook/evidence/user-token-debugger.png) | `User`. `pages_show_list` và `pages_manage_engagement` trỏ tới Page thử. `pages_read_user_content` hiển thị áp dụng mọi đối tượng. | `read_insights`, `pages_show_list`, `pages_read_user_content`, `pages_manage_engagement`, `public_profile`. |

Trong lượt âm bổ sung với `PAGE-NO-ENG-02`, Debugger hiển thị `pages_read_user_content` áp dụng mọi đối tượng. Cặp chính bên dưới có cùng granular target cho các quyền Pages còn lại.

Ảnh Debugger bổ sung lúc `2026-11-13T15:10:36Z` (22:10:36 +07:00 theo tên ảnh) ghi một **User token** hợp lệ của cùng app. Token thiếu `pages_read_engagement`, có `read_insights`, `pages_show_list`, `pages_read_user_content`, `pages_manage_engagement` và `public_profile`. Ba quyền Pages có granular target là Page thử. Expiry `1794589200`, data-access expiry `1802358621`. [Debugger của User token](api-verification/facebook/evidence/fb-10-user-token-matching-page-scope-debugger.png)

Ảnh lúc `2026-11-13T15:12:09Z` (22:12:09 +07:00 theo tên ảnh) xác nhận `PAGE-NO-ENG-03` là **Page token** hợp lệ của app `1650819950076669` và Page `1427548503779474`. Token thiếu `pages_read_engagement` và giữ năm scope còn lại. `pages_show_list`, `pages_read_user_content` và `pages_manage_engagement` cùng trỏ tới Page thử, khớp phạm vi các quyền này ở lượt dương. Expiry `1794589200`, data-access expiry `1802358621`. [Debugger của Page token](api-verification/facebook/evidence/fb-10-page-token-matching-page-scope-debugger.png)

**PASS cho đối chứng FB-10.** Lượt dương trả follower 1. Lượt âm được Nghị cung cấp sau preflight của `PAGE-NO-ENG-03` trả `OAuthException #100` trên cùng GET. Hai credential có cùng loại, app, Page và các quyền còn lại theo Debugger. Expiry và data-access expiry phản ánh hai lần cấp riêng. Giờ ảnh là mốc bằng chứng. HTTP status, subcode và giờ thực thi chính xác không quan sát được.

### Thứ tự chạy và đối soát

1. Sau preflight, chạy FB-01/02, FB-04–07 và FB-09 độc lập. FB-02 giữ nguyên scope/revision, post, fields, filter và limit. Khi có `paging.next`, dùng cursor `after` trong phiên thử. Ghi sự hiện diện của `next` và `after` cùng số ID duy nhất/trùng. Nếu có `next` nhưng thiếu cursor hoặc trang kế tiếp lỗi, ghi chưa xác nhận điểm kết thúc. Nếu cần thêm comment, ghi lần đầu rồi bắt đầu lại từ trang 1.
2. FB-03 dùng comment C01 lấy ở FB-01. Nội dung đề xuất **“SP-018 — Phản hồi thử nghiệm từ Page demo để xác minh Graph API v27.0.”** Sau khi đọc trước, kiểm tra `can_comment=true`, task moderation và grant `pages_manage_engagement` cùng quyền phụ thuộc. Nghị xác nhận đúng comment và nội dung trên Page của mình, hoặc xin Minh duyệt nếu dùng Page của Minh.
3. Sau xác nhận, chọn POST và dán `/{comment-id}/comments`. Thêm param `message` bằng đúng nội dung đã duyệt rồi Submit một lần. Lưu reply ID và chạy GET `/{reply-id}?fields=id,message,from,parent,created_time`. Đối chiếu `from.id` với Page, `parent.id` với C01 và nội dung với bản duyệt. Kiểm tra thêm trên Page nếu field vắng. Nếu POST mơ hồ, ghi `UNKNOWN` và đọc GET `/{comment-id}/comments?fields=id,message,from,parent,created_time&limit=25`. Đối chiếu Page, nội dung và khoảng giờ gửi, kiểm tra các trang kế tiếp hoặc giao diện Page trước mọi quyết định gửi lại.
4. FB-08 chạy hai GET độc lập sau khi đối chiếu `read_insights`, các quyền phụ thuộc và task analytics. Ghi `data: []`, metric thiếu, số 0 và Graph error thành các kết quả riêng. Nếu một grant của FB-03/08 vắng, ghi kết quả preflight rồi tiếp tục ca độc lập có đủ điều kiện.

### Đối chứng FB-10

1. Dùng kết quả FB-04 có `followers_count = 1` làm bằng chứng dương ban đầu. Bổ sung HTTP status từ bằng chứng cục bộ nếu có, hoặc ghi lại lượt dương bằng cùng Page token và đúng request trước khi thử lượt âm. Giữ cùng app ID, app mode, Page, tasks, phiên bản v27.0, endpoint và query. Ưu tiên cùng tài khoản cấp token và cùng các grant còn lại để cô lập quyền.
2. Tạo credential thử riêng với grant dự kiến thiếu `pages_read_engagement`. Trước phép đo, kiểm tra loại `PAGE`, validity, app ID, Page target và scope/granular scope bằng Debugger. Đối chiếu Page bằng `/me?fields=id,name` khi có thể. Việc bỏ chọn trong Explorer có thể vẫn giữ grant cũ. Ghi khác biệt thực tế giữa hai credential, gồm quyền phụ thuộc bị thay đổi. Giữ credential gốc dùng cho lượt dương.
3. Chỉ chạy lại GET `/{page-id}?fields=id,followers_count` khi đối chứng giữ được app, Page, version và cô lập được quyền. Nếu response vẫn có giá trị, lưu kết quả và ghi phép thử chưa chứng minh phụ thuộc quyền. Nếu thiếu field hoặc Graph từ chối, ghi đúng response và HTTP/code/subcode trước khi phân loại.
4. Nếu Meta không cấp được Page token cùng điều kiện, ghi giới hạn preflight. Phép đọc thay thế đề xuất là FB-08 `/{post-id}/insights?metric=post_media_view&period=lifetime`, giữ `pages_read_engagement` và task analytics, bỏ riêng `read_insights`. Chờ Nghị thống nhất phép thay thế và có lượt dương của chính request đó trước khi thử.

### Mẫu bằng chứng cho từng request

Sao chép một bản cho mỗi preflight, ca FB-01–10, trang kế tiếp, metric/filter, lượt đối soát hoặc đối chứng. Dùng cùng mã Page/bài/comment đã che trong cả lượt. Chỉ gắn `API_THAT` cho request đã gọi. Với lỗi hoặc thao tác chưa gọi, ghi riêng bước bị chặn. Nếu Explorer thiếu HTTP status, ghi `chưa quan sát được` hoặc lấy status từ network response cục bộ và chỉ chép số status.

```text
Ca / lượt:
Nguồn (API_THAT khi đã gọi) / trạng thái:
Revision (git SHA) / người chạy / thời điểm UTC:
Môi trường / app ID / app mode / app role / access level:
Page / bài / comment thử / Page tasks:
Credential ref / connectionRevision / token type / validity / expiry / data-access expiry:
Quyền thực cấp / granular targets liên quan:
Method / Graph API version thực gọi / path / params hoặc body đã che:
Kỳ vọng:
Thực tế (HTTP status / Graph error type, code, subcode nếu có):
Response đã che (dữ liệu rỗng, số 0, field vắng hoặc lỗi):
Theo ca (phân trang, xác nhận và đối soát reply, filter, metric/period/start/end/breakdown):
Link bằng chứng đã che:
Blocker / người xử lý / thao tác cần thực hiện:
```

Che token, secret, cookie, header Authorization, toàn bộ paging URL/cursor và dữ liệu cá nhân trước khi chia sẻ. Giữ ID Page/bài/comment/reply cần đối soát trong phiên thử và dùng mã tham chiếu nhất quán cho hồ sơ chia sẻ.

Nếu vướng quyền cấp app, gửi Minh tên app và use case, request hoặc bước cấp token bị chặn, quyền thực cấp/quyền thiếu, HTTP/code/subcode nếu quan sát được và ảnh đã che. Nhờ Minh kiểm tra quyền đó ở App Dashboard → Use cases → Customize → Permissions and features (hoặc mục tương ứng của app), bật quyền trong use case nếu đang thiếu và xử lý access level/App Review nếu Dashboard yêu cầu. Sau đó Nghị cấp lại Page token và chạy lại preflight. Nếu task trên Page thiếu, xử lý ở Page access và kiểm tra lại `/me/accounts`.

## 5. Capability Social v1

| Operation | Tình trạng trên kênh/revision thử | Bằng chứng cần có |
|---|---|---|
| `READ_COMMENTS` | `UNVERIFIED` trong hồ sơ capability | FB-01 đã đọc được 1 comment trên đúng bài. Chờ giờ kiểm chứng UTC và `connectionRevision` để ghi `AVAILABLE`. FB-02 theo dõi coverage riêng. |
| `REPLY_COMMENT` | `UNVERIFIED` trong hồ sơ capability | FB-03 tạo và đọc lại reply thành công với grant đã đối chiếu. Nghị xác nhận Minh duyệt trước POST. Chờ `connectionRevision` và giờ kiểm chứng để ghi trạng thái cho kết nối. |
| `READ_FOLLOWER_COUNT` | `UNVERIFIED` trong hồ sơ capability | FB-04 và lượt dương FB-10 đều trả `followers_count = 1`. Ảnh lượt dương cung cấp mốc bằng chứng `2026-11-13T14:54:58Z`. Chờ `connectionRevision`. |
| `READ_POST_METRICS` | `UNVERIFIED` trong hồ sơ capability | FB-05/06/08 xác nhận đọc được reaction, comment và hai metric insights. Grant insights đã đối chiếu. FB-07 thiếu `shares`. Chờ giờ kiểm chứng và `connectionRevision`. |

Hành vi đọc và reply đã được quan sát trên credential thử. `verifiedAt` còn trống trong hồ sơ capability theo kênh và revision. Theo Social v1, `AVAILABLE` cần `verifiedAt`, `PERMISSION_REQUIRED` và `UNSUPPORTED` cần `reasonCode` cùng `verifiedAt`. Với `UNVERIFIED`, ghi `reasonCode` giải thích đầu vào còn thiếu. Lỗi credential giới hạn ở FB-10 mô tả lần thử của credential đó. Metric thiếu trong response thành công dùng `PROVIDER_OMITTED` và giữ capability hiện có.

## 6. Bàn giao mapping Facebook cho SP-020

Thiện [xác nhận các trường UI và tài liệu + fixture đủ cho W1 ở draft-2](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5847811439). [Minh](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5861020207) và [Thiện](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5862180313) đã approve head `cdc633a` của contract trước squash merge. Bảng dưới áp dụng kiểu dữ liệu đã merge. Tài liệu Meta đã xác nhận các tên metric và kỳ ở mục 3. Phạm vi/kỳ thực trả trên Page thử còn chờ SP-018 live, cách hiển thị chờ Thiện. [Contract Room SP-012](room-schema-contract.md) đã merge qua #71, dùng khóa sample `(snapshotId, definitionId)` khi mỗi định nghĩa có một mẫu mỗi đợt. Nếu cần lưu nhiều kỳ, Nghị chốt period key với Đạt trước khi tạo constraint.

| Ca / metric key | Định nghĩa và đơn vị đề xuất | Kind / entity | Kỳ và mốc quan sát |
|---|---|---|---|
| FB-04 / `FOLLOWER_COUNT` | `followers_count`, số tài khoản theo dõi Page, `COUNT`. Bằng chứng live trả `1` trên Demo Page MD 24-31. | `GAUGE` / `CHANNEL` | Response thiếu mốc provider và kỳ. Dùng `observedAt = collectedAt` với basis `COLLECTION` khi xác nhận giờ thu. Hiện ghi riêng giờ ảnh. |
| FB-05 / `REACTION_COUNT` | `reactions.summary.total_count = 1` trên bài ảnh, `COUNT`. Filter LIKE cũng trả 1. Giữ tổng edge làm số reaction và LIKE là mẫu theo filter. Độ dài `data` và `viewer_reaction` được lưu riêng. | `CUMULATIVE` / `POST` | Không có kỳ hoặc mốc provider. Dùng basis `COLLECTION` khi xác nhận giờ thu. |
| FB-06 / `COMMENT_COUNT` | `comments.summary.total_count` trên bài ảnh có reply là 4 với `toplevel`, 5 với `stream`. Giữ filter trong định nghĩa để UI chọn phạm vi đếm. | `CUMULATIVE` / `POST` | Không có kỳ hoặc mốc provider. Dùng basis `COLLECTION` khi xác nhận giờ thu. |
| FB-07 / `SHARE_COUNT` | `shares.count`, `COUNT`. Bài ảnh trả ID và vắng `shares`, ghi `null / PROVIDER_OMITTED`. | `CUMULATIVE` / `POST` khi field xác nhận tổng hiện tại | Không có kỳ hoặc mốc provider. |
| FB-08 / `IMPRESSION_COUNT` | Số impression theo định nghĩa provider, `COUNT`. Chưa có metric tương đương được xác minh. Hiện `null / UNVERIFIED`. | `POST`, chờ chốt `PERIOD` hoặc `CUMULATIVE` theo định nghĩa nguồn | Chờ period/start/end và mốc provider. |
| FB-08 / `post_media_view` | Số lần nội dung được phát hoặc hiển thị, `COUNT`. Bài ảnh trả 17. | `CUMULATIVE` / `POST` cho tổng lifetime quan sát được. | Provider trả `lifetime`. Response rút gọn chưa có start/end hoặc breakdown. |
| FB-08 / `post_total_media_view_unique` | Số người xem duy nhất của bài, `COUNT`. Bài ảnh trả 3. | `CUMULATIVE` / `POST` cho tổng lifetime quan sát được. | Provider trả `lifetime`. Response rút gọn chưa có start/end. |

Mỗi `MetricSample` mang `scope = {platform: FACEBOOK, channelId, connectionRevision}`, `entityType/entityId`, `definitionId`, `collectionId`, `observedAt/observedAtBasis`, `collectedAt` UTC tại lúc nhận, `providerPeriodStart/End` khi có, `providerApiVersion`, `source` và `sourceRef`. `MetricDefinition` giữ `metricKey`, `providerMetricName`, `unit`, `measurementKind` và định nghĩa đếm. Ghi nhãn period gốc và breakdown trong evidence. Việc mang nhãn này sang DTO, thêm metric views/viewers hoặc khóa lưu nhiều kỳ là đề xuất qua SP-020, chờ Thiện và Đạt ở phần liên quan.

Giữ `0` với `missingReason = null`. Trường metric vắng trong response thành công dùng `PROVIDER_OMITTED` khi đã xác định đúng response và trường yêu cầu. Lưu riêng trường hợp provider trả `data: []` cho insight để đối chiếu trước khi gán lý do. Khi lần thu mới lỗi, giữ giá trị cache và timestamp của mẫu tốt, đồng thời trả freshness (`isStale`, `lastSuccessAt`), `lastAttemptAt` và lỗi/trạng thái offline của lần mới nhất. Giữ provenance từng sample. Với `PERIOD`, giữ khoảng UTC nửa mở `[start,end)` theo provider. Chỉ hiển thị tuần đủ khi có kỳ đúng hoặc cặp mốc thực đáp ứng coverage trong SP-020. Đo tại giờ thu chỉ cho mốc quan sát. ER theo impression chỉ tính khi ba thành phần engagement và mẫu số impression cùng định nghĩa/phạm vi/khoảng đo tương thích và mẫu số dương. Views/viewers giữ định nghĩa riêng.

Projection comment bàn giao giữ scope, post, comment và parent riêng. FB-01 trả ID, message, thời điểm tạo và tác giả Page trong một lượt có `filter=toplevel`. Trường `parent` vắng nên projection giữ `UNKNOWN`, còn phạm vi cấp cao nhất được ghi theo filter. Thiếu message giữ `OMITTED`. FB-09 xác nhận bài ảnh có permalink và ảnh, comment thử vắng `parent` và `attachment`. Thiện rà metadata nullable và Đạt xác nhận thiết kế lưu trữ nếu mapping kỳ đo ảnh hưởng schema.

## 7. Kiểm tra offline và bàn giao

- [Fixture](api-verification/facebook/fixtures.json) dùng ID, nội dung và cursor tổng hợp, `apiVersion = null`, provenance `MO_PHONG`.
- [Prototype](api-verification/facebook/response-prototype.cjs) giữ channel/post/comment/parent, không trộn trang giữa scope/revision/query, giữ 0 khác null và phân biệt lỗi với trang rỗng. Heuristic mã lỗi ở đây phục vụ fixture. Wrapper follower `POINT_IN_TIME` trong mẫu offline tương ứng khái niệm `GAUGE` khi bàn giao contract. Chưa có parser insights hoặc tính ER.
- [Kiểm tra fixture](api-verification/facebook/verify-fixtures.cjs) chạy lại trên Windows, Node v24.19.0, ngày 13/11/2026.

| Lệnh đã chạy | Kết quả |
|---|---|
| `node docs/api-verification/facebook/verify-fixtures.cjs` | 55/55 PASS, `MO_PHONG`, 0 lượt gọi live |
| `node docs/contracts/verify-analytics-examples.cjs` | 75/75 PASS, `MO_PHONG` |
| `node --check docs/api-verification/facebook/response-prototype.cjs` | PASS |
| `node --check docs/api-verification/facebook/verify-fixtures.cjs` | PASS |
| `git diff --check` và kiểm tra diff so với `origin/main` | PASS |

FB-01–10 đã có phản hồi live. FB-03 có xác nhận của Nghị về việc Minh duyệt trước POST. FB-03/08 có ảnh Debugger. FB-10 có cặp đối chứng đạt với hai Page token. Nghị xác nhận các token cũ đã hết hiệu lực. Giờ thực thi và `connectionRevision` vẫn thiếu ở một số lượt. Hồ sơ local sẵn sàng gửi Minh và Thiện review lại theo [#20](https://github.com/nigPeter2503/Social_Pilot/issues/20). Thiện rà mapping UI Facebook, Đạt xác nhận nếu mapping cần đổi schema.
