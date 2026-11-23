# SP-018 — Kiểm chứng Facebook comment, reply và metrics

Ngày cập nhật 15/11/2026 (Asia/Saigon). Owner Tô Lãng Nghị (`bob35306`).
[Issue #20](https://github.com/nigPeter2503/Social_Pilot/issues/20) · [PR #75](https://github.com/nigPeter2503/Social_Pilot/pull/75) · [Review đầu của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703) · [Review của Thiện](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5864307971) · [Review mới của Minh trên `13f9de3`](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5910635664).

**FB-01–10 đã có kết quả live trên Graph API Explorer v27.0.** FB-03 đã gửi và đọc lại reply sau khi Minh duyệt theo xác nhận của Nghị. [Ảnh Page ngày 15/11](api-verification/facebook/evidence/fb-03-reply-on-page.png) cho thấy reply dưới đúng comment cha. PR #75 đang Ready for review với `Closes #20`. Issue #20 còn mở trong khi Minh và Thiện đối chiếu bằng chứng và xác nhận bàn giao.

## 1. Phạm vi và revision

- **Revision lúc thử.** Nghị gọi Graph API Explorer trực tiếp. Báo cáo ghi workspace khi chuẩn bị và ghi preflight ở `7b148fb1fae849eeeb23355866b535babe4e1597`. SHA tại thời điểm từng request chưa được ghi nhận.
- **Revision ghi hồ sơ live.** [Commit `13f9de33bb3f50f4585af5cb639b608a64dc9fef`](https://github.com/nigPeter2503/Social_Pilot/commit/13f9de33bb3f50f4585af5cb639b608a64dc9fef) ghi JSON/tóm tắt và ảnh đã che. Git ghi commit lúc `2026-11-13T15:20:47Z`. Đây là mốc ghi hồ sơ.
- **Các revision được review.** Minh và Thiện yêu cầu thay đổi trên `943e89a`. Minh bổ sung review trên `13f9de3` lúc `2026-11-15T12:15:42Z`. Commit `886b947` bổ sung ảnh reply trên Page. Xem head hiện tại ở [tab Commits của PR #75](https://github.com/nigPeter2503/Social_Pilot/pull/75/commits). [Thông báo kết quả live của Nghị](https://github.com/nigPeter2503/Social_Pilot/pull/75#issuecomment-6857370903).
- SP-009 đã merge qua [PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66). Dùng [Social v1](social-contract.md) cho operation, capability và lỗi.
- [SP-010/#12](https://github.com/nigPeter2503/Social_Pilot/issues/12) và [PR #74](https://github.com/nigPeter2503/Social_Pilot/pull/74) đang mở. Head #74 khi đối chiếu ngày 15/11 là `d1d55a03ea9bafd3073946b1376ee37d36d9689f`. Xem [hồ sơ SP-010](https://github.com/nigPeter2503/Social_Pilot/blob/d1d55a03ea9bafd3073946b1376ee37d36d9689f/docs/drafts/sp-010-implementation-status.md).
- [SP-020/#22](https://github.com/nigPeter2503/Social_Pilot/issues/22) đã đóng sau khi [PR #67](https://github.com/nigPeter2503/Social_Pilot/pull/67) được squash merge vào `main` tại `e51dfc3`. Đã đối chiếu [hợp đồng analytics](analytics-data-contract.md) và hồ sơ/fixture SP-020 trên main khớp với head PR `cdc633a85a99167e391b2e238f64378c085152a6`.
- Lượt này cập nhật hồ sơ Facebook và rà bộ Node offline. Đề xuất bổ sung contract dùng chung đi qua SP-020. Inbox/sync production, OAuth, Android UI, Room và LinkedIn nằm ngoài phạm vi PR.

## 2. Đầu vào SP-010 và điều kiện chạy thật

- Minh xác nhận **Social Management MD** (App ID `1650819950076669`), **Development mode**, tài khoản thử có vai trò **Admin**. Page thử **Demo Page MD 24-31** có ID `1427548503779474`. Post ID `1427548503779474_122109132231532657`, tạo lúc `2026-11-11T06:58:10+0000` (UTC). Nội dung theo Minh là “Bài đăng kiểm thử SocialPilot - 11/11/2026”. [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703) · [Permalink bài thử](https://www.facebook.com/122109132717532657/posts/122109132231532657). Khi mở lại qua HTTP không đăng nhập, URL trả 200 nhưng phần HTML đọc được chỉ có tiêu đề Facebook. Việc đối chiếu nội dung và Page vẫn dựa trên xác nhận của Minh ở SP-010.
- SP-010 đã POST bài trong Graph API Explorer bằng **v27.0**, GET đọc lại và xác nhận permalink đúng Page/bài. [Cấu hình backend ở head #74](https://github.com/nigPeter2503/Social_Pilot/blob/d1d55a03ea9bafd3073946b1376ee37d36d9689f/backend/src/main/resources/application.properties) mặc định `v27.0`, ghi đè qua `FACEBOOK_GRAPH_API_VERSION`. Các request SP-018 bên dưới dùng `v27.0` theo báo cáo của Nghị và ảnh Explorer ở FB-10.
- **Bằng chứng lịch sử của User token** theo ảnh Debugger của SP-010 gồm `public_profile`, `pages_show_list`, `business_management`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_posts`. `GET /me/accounts?fields=id,name,tasks,access_token` đã trả đúng Page với các task `CREATE_CONTENT`, `MANAGE`, `MODERATE`, `MESSAGING`, `ANALYZE`, `ADVERTISE`, `MANAGE_LEADS`. [Review của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#pullrequestreview-5862085703).

- Minh xác nhận đã thêm Nghị làm **Developer** của **Social Management MD**. Nghị dùng **Demo Page MD 24-31**, cùng Page trong bằng chứng SP-010. [Phản hồi của Minh](https://github.com/nigPeter2503/Social_Pilot/pull/75#issuecomment-6828550286).
- Page token lịch sử của SP-010 hợp lệ lúc kiểm tra, Expires “Never”, có data-access expiry riêng và GET `/v27.0/me?fields=id,name` khớp Page. Bài ảnh có alt text dùng trong SP-018 khớp Post ID và giờ tạo ở hồ sơ SP-010. [Phản hồi về token](https://github.com/nigPeter2503/Social_Pilot/pull/75#issuecomment-6817634918) · [Bằng chứng bài ảnh và alt text](https://github.com/nigPeter2503/Social_Pilot/blob/d1d55a03ea9bafd3073946b1376ee37d36d9689f/docs/evidence/sp-010-photo-alt-text-2026-11-12.md).

### Quy trình tham chiếu khi kiểm tra lại Page và credential

1. Nghị tạo hoặc chọn Page demo do mình quản lý. Trên Page, đăng một bài chữ với nội dung `SP-018 — Bài chữ kiểm chứng Graph API v27.0`. Dùng tài khoản của mình để tạo ba comment cấp cao nhất `SP-018 C01`, `SP-018 C02`, `SP-018 C03` nếu bài chưa đủ dữ liệu. Dùng C01 làm comment cha cho FB-03. Giữ bộ comment ổn định trong lượt phân trang. Có thể dùng bài ảnh riêng để kiểm tra trường ảnh ở FB-09 sau khi ghi kết quả của bài chữ.
2. Trong [Graph API Explorer](https://developers.facebook.com/tools/explorer/), chọn **Social Management MD**, **v27.0** và **Get User Access Token**. Cho phép đúng Page demo trong bước consent. Yêu cầu `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`. Kiểm tra app mode và access level trong App Dashboard. Ghi nhận Page tasks như Meta trả về, gồm các task liên quan đến moderation và insights.
3. Với User token, chạy GET `/me/accounts?fields=id,name,tasks`. Chọn Page demo trong mục **User or Page** để lấy Page token. Nếu cần lấy từ `/me/accounts?fields=id,name,tasks,access_token`, giữ toàn bộ response chứa token trong phiên cục bộ.
4. Với Page token thực dùng, kiểm tra bằng [Access Token Debugger](https://developers.facebook.com/tools/debug/accesstoken/). Ghi loại `PAGE`, validity, expiry/data-access expiry nếu có, app ID, scope và granular target theo Page. Chạy GET `/me?fields=id,name` và đối chiếu đúng Page demo. Lặp bước này khi đổi credential, ghi `connectionRevision` hoặc mã tham chiếu lần cấp thử riêng cho mỗi credential. Ghi rõ nếu Explorer chạy độc lập với backend/Android.
5. Lấy Post ID qua GET `/{page-id}/published_posts?fields=id,message,created_time,permalink_url&limit=5`. Đối chiếu nội dung và permalink với bài trên Page. Nếu Page demo của Nghị chưa dùng được, xác nhận phạm vi với Minh trước khi chuyển sang Page của Minh và chạy lại preflight cho Page đó.

### Preflight ban đầu do Nghị báo cáo

- Nghị (`bob35306`) chạy bằng Graph API Explorer, **Social Management MD**, **Development**, vai trò **Developer**, Graph **v27.0**. SHA workspace lúc ghi hồ sơ là `7b148fb1fae849eeeb23355866b535babe4e1597`. Giờ UTC chạy, SHA lúc thử nếu khác, access level từng quyền, expiry, data-access expiry và granular targets của lượt này chưa được cung cấp.
- Page thử `1427548503779474` (**Demo Page MD 24-31**). Tasks thực trả gồm `MANAGE_LEADS`, `MODERATE`, `MESSAGING`, `ANALYZE`, `ADVERTISE`, `CREATE_CONTENT`.
- Debugger ban đầu ghi cả User token và Page token hoạt động và có `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`, `public_profile`. Page token ban đầu thiếu `pages_manage_engagement` và `read_insights`.
- Bài được chọn `1427548503779474_122109617565532657`, “Kiểm thử đăng ảnh và alt text SocialPilot - SP-010 - 12/11/2026.”, tạo lúc `2026-11-12T07:15:30Z` (14:15:30 +07:00). FB-01 ghi ID comment và số mục của response. FB-06 ghi số đếm theo từng filter.

| Lượt | Request v27.0 và credential | Kỳ vọng | Kết quả `API_THAT` do Nghị báo cáo |
|---|---|---|---|
| Preflight 1 | GET `/me/accounts?fields=id,name,tasks`, User token | Tìm đúng Page và tasks | Thành công, khớp Page và sáu tasks trên. |
| Preflight 2 | GET `/me?fields=id,name`, Page token | `/me` khớp Page demo | Thành công, xác nhận đúng Page `1427548503779474`. |
| Preflight 3 | GET `/me/posts?fields=id,message,created_time&limit=5`, Page token | Đọc bài của Page và nhận diện bài thử | Thành công, trả 4 bài. Nghị chọn bài ảnh nêu trên. |

Nguồn preflight ban đầu là báo cáo của Nghị trong phiên trao đổi ngày 13/11. Báo cáo ghi ba response thành công, chưa có ảnh/JSON riêng, HTTP status, giờ thực thi hoặc Graph error.

### Grant xác nhận cho lượt thành công

Nghị đối chiếu Page qua GET `/v27.0/me?fields=id,name` và gắn ảnh Debugger của Page token thành công với FB-03, FB-08 và lượt dương FB-10 (`PAGE-READ-02` trong FB-10). Ảnh xác nhận app, loại Page, Page ID, validity lúc chụp và sáu grant `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_engagement`, `read_insights`, `public_profile`. Bốn quyền Pages trỏ tới Page thử. Task `MODERATE` và `ANALYZE` lấy từ preflight báo cáo. Ảnh chụp lúc `2026-11-13T14:47:40Z` theo mốc Nghị ghi. Mã credential cho các lượt đọc khác chỉ ghi khi nguồn nối được với lượt đó. [Ảnh Debugger](api-verification/facebook/evidence/page-token-debugger.png) · [Scope của cặp FB-10](#fb-10--cặp-đối-chứng-follower).

## 3. Đối chiếu nguồn Meta ngày 12/11/2026

Lượt tra cứu tài liệu ngày 12/11 trả HTTP 200. Changelog và ví dụ request dùng `v27.0`. Ma trận ở mục 4 ghi version và kết quả thực tế của SP-018.

| Nguồn | Kết quả đối chiếu |
|---|---|
| [Comments](https://developers.facebook.com/docs/graph-api/reference/object/comments/) | GET `/{object-id}/comments` trên bài dùng Page token. Meta ghi tác vụ `MODERATE` để đọc ID comment của Page Post. Với `summary=true`, `filter=toplevel` đếm comment cấp cao nhất, còn `filter=stream` gồm cả reply khi filter áp dụng. Summary có thể khác số comment thực trả. POST lên comment cần Page token, `MODERATE` và `pages_manage_engagement`. Kiểm tra `can_comment` trên comment thử trước khi ghi. |
| [Explorer](https://developers.facebook.com/docs/graph-api/guides/explorer/), [Debug Token](https://developers.facebook.com/docs/graph-api/reference/debug_token/) | Explorer cho phép chọn app theo vai trò. Debugger xác nhận loại credential, validity và grant thực tế. Đối chiếu granular target nếu có và dùng `/me` để xác nhận Page. |
| [Page](https://developers.facebook.com/docs/graph-api/reference/page/), [Page Post](https://developers.facebook.com/docs/graph-api/reference/page-post/), [Pages posts](https://developers.facebook.com/documentation/pages-api/posts) | Reference ghi định nghĩa `followers_count`, `shares.count`, `full_picture` và `permalink_url`. Kết quả thực trả nằm ở FB-04, FB-07, FB-09 và FB-10. |
| [Permissions](https://developers.facebook.com/documentation/development/permissions) | `pages_read_engagement` áp dụng cho nội dung do Page đăng và metadata, `pages_read_user_content` cho nội dung người dùng đăng trên Page. `pages_manage_engagement` phụ thuộc `pages_read_user_content` và `pages_show_list`. `read_insights` phụ thuộc `pages_read_engagement` và `pages_show_list`. |
| [Insights](https://developers.facebook.com/docs/graph-api/reference/insights/) | Đọc insights cần Page token, `read_insights`, `pages_read_engagement` và `ANALYZE`. Hai metric Post Media Views được tài liệu liệt kê với `period=lifetime`. `post_media_view` đếm số lần phát hoặc hiển thị, có breakdown `is_from_ads` và `is_from_followers`. `post_total_media_view_unique` đếm người xem duy nhất. |
| [Graph API changelog](https://developers.facebook.com/docs/graph-api/changelog/), [Changelog v25.0](https://developers.facebook.com/documentation/development/graph-api/changelog/version25.0), [Metric đã bỏ của Pages API](https://developers.facebook.com/documentation/pages-api/platforminsights/page/deprecated-metrics/) | Changelog hiển thị `v27.0`. Meta liệt kê việc ngừng hỗ trợ `post_impressions` và `post_impressions_unique`. Hai metric media views có định nghĩa riêng. Giữ `IMPRESSION_COUNT` chưa có nguồn tương đương và ER theo impression chưa tính. |
| SDK chính thức của Meta ([Comment](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/comment.py), [PagePost](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/pagepost.py), [Page](https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/page.py)) | Đọc được khai báo GET/POST `/comments`, GET `/reactions`, GET `/insights` với tham số `metric` và `period`, cùng các trường `shares`, `full_picture`, `attachment` và `followers_count`. Đây là căn cứ cho request ứng viên. Quyền thực cấp, metric/period hợp lệ và hành vi trên bài thử cần bằng chứng riêng. |

Lượt trước trình duyệt tra cứu tài liệu Meta trả HTTP 429. Phần kiểm tra rate limit của FB-10 dùng fixture.

## 4. Ma trận FB-01–10

Mọi request bên dưới dùng Graph API Explorer `v27.0`. `{page-id}` là `1427548503779474`, `{post-id}` là bài ảnh `1427548503779474_122109617565532657` và `{comment-id}` là comment thử từ FB-01. Cột quyền nêu điều kiện tham chiếu, grant thực cấp nằm ở mục 2 và FB-10.

| Ca | Request v27.0 / quyền liên quan | Kỳ vọng | Kết quả và bằng chứng |
|---|---|---|---|
| FB-01 | GET `/{post-id}/comments?fields=id,message,created_time,from,parent&filter=toplevel&limit=2`. Page token, task moderation để đọc ID, `pages_read_engagement` và `pages_read_user_content` theo loại nội dung. | Đối chiếu comment với bài thử. Ghi riêng trang rỗng, field vắng và lỗi quyền. | **PASS** theo JSON người chạy cung cấp. Có 1 comment, trường `parent` vắng. [Bằng chứng FB-01](#fb-01--đọc-comment). |
| FB-02 | GET `/{post-id}/comments?fields=id,message,created_time&filter=toplevel&limit=2`, thêm `after={cursor}` ở trang 2. Quyền như FB-01. | Lấy ít nhất hai trang từ cùng query. Ghi số ID trùng/duy nhất và điều kiện dừng. | **PASS**. Bài ảnh, hai trang, 4 ID duy nhất, 0 trùng. [Bằng chứng FB-02](#fb-02--phân-trang). |
| FB-03 | GET `/{comment-id}?fields=id,can_comment,message,parent,from`, POST `/{comment-id}/comments`, GET `/{reply-id}?fields=id,message,from,parent,created_time`. Page token, `MODERATE`, `pages_manage_engagement` và quyền phụ thuộc ở mục 3. | Gửi một lần sau khi duyệt, nhận reply ID và đọc lại đúng Page, comment cha và nội dung. | **PASS**. Nghị xác nhận Minh duyệt trước POST. GET đối soát khớp, Debugger xác nhận grant và ảnh Page thấy reply dưới đúng comment cha. [Bằng chứng FB-03](#fb-03--gửi-và-đối-soát-reply). |
| FB-04 | GET `/{page-id}?fields=id,followers_count`. Page token, `pages_read_engagement` ứng viên. | Đọc follower của đúng Page tại thời điểm thu, gồm cả 0. | **PASS** theo JSON người chạy cung cấp. Page khớp và `followers_count = 1`. [Bằng chứng FB-04](#fb-04--đọc-follower). |
| FB-05 | GET `/{post-id}/reactions?summary=true&limit=1`. Đối chiếu `summary=total_count` có và không có `type=LIKE`. `pages_read_engagement` ứng viên. | Ghi tổng reaction và kết quả có filter LIKE riêng. | **PASS**. Tổng reaction 1, lọc LIKE 1. Cả hai GET bổ sung có `data: []`. [Bằng chứng FB-05](#fb-0507-và-fb-09--số-liệu-và-metadata). |
| FB-06 | GET `/{post-id}/comments?summary=true&filter=toplevel&limit=1` và GET `/{post-id}/comments?summary=true&filter=stream&limit=1`. Quyền như FB-01. | Ghi riêng `summary.total_count` theo filter. | **PASS**. Lượt đầu 1/1. Sau reply, `toplevel=4`, `stream=5`. [Bằng chứng FB-06](#fb-0507-và-fb-09--số-liệu-và-metadata). |
| FB-07 | GET `/{post-id}?fields=id,shares`. `pages_read_engagement` ứng viên. | Ghi `shares.count` khi có. Khi field vắng trong response thành công, ghi `PROVIDER_OMITTED`. | **API_THAT / PROVIDER_OMITTED**. Response có đúng ID bài, thiếu `shares`. Giá trị metric là null. [Bằng chứng FB-07](#fb-0507-và-fb-09--số-liệu-và-metadata). |
| FB-08 | GET `/{post-id}/insights?metric=post_media_view&period=lifetime` và GET `/{post-id}/insights?metric=post_total_media_view_unique&period=lifetime`. Page token, `read_insights`, `pages_read_engagement`, `ANALYZE`. | Ghi riêng tên, giá trị, đơn vị, entity, period/start/end, breakdown nếu có và loại bài đã thử. | **PASS** trên bài ảnh. Views 17, người xem duy nhất 3. Debugger xác nhận grant của Page token. Đã lưu lỗi credential trung gian `190/2069032`. [Bằng chứng FB-08](#fb-08--post-insights). |
| FB-09 | GET `/{post-id}?fields=id,created_time,permalink_url,full_picture` và GET `/{comment-id}?fields=id,parent,from,attachment`. Quyền theo field, loại đối tượng và access level. | Ghi metadata thực trả trên bài ảnh và comment thử. Nếu truy vấn nhóm lỗi, tách field để xác định phạm vi. | **PASS**. Bài ảnh có đủ bốn trường đã hỏi. Comment có `id` và `from`, vắng `parent` và `attachment`. [Bằng chứng FB-09](#fb-0507-và-fb-09--số-liệu-và-metadata). |
| FB-10 | Lặp đúng GET của FB-04 với `PAGE-READ-02` và `PAGE-NO-ENG-03`, Page token lượt âm thiếu `pages_read_engagement`. | Ghi HTTP/code/subcode và quyền cả hai lượt. Xác minh các điều kiện đối chứng. | **PASS**. Lượt dương trả 1, lượt âm với `PAGE-NO-ENG-03` trả `OAuthException #100`. Cùng app, Page, loại token, version, query và phạm vi các quyền còn lại. HTTP status và subcode không quan sát được trong ảnh. [Bằng chứng FB-10](#fb-10--cặp-đối-chứng-follower). |

### Bằng chứng live do Nghị cung cấp

Nguồn cho các request là JSON đã che hoặc bản chép/tóm tắt response của Nghị, đối chiếu với ảnh ở các mục có đính kèm. [Xác nhận công khai của người chạy](https://github.com/nigPeter2503/Social_Pilot/pull/75#issuecomment-6857370903) chốt phạm vi FB-01–10. Giờ tạo (`created_time`), giờ chụp và giờ thực thi/nhận response là ba mốc riêng. Ảnh đính kèm ghi nhận grant, payload FB-10 và reply hiện có trên Page ở FB-03. Ngày 15/11, người cung cấp ảnh xác nhận log gốc đã không còn. Các mốc thực thi, HTTP status, subcode và mã credential còn thiếu chỉ ghi theo dữ liệu đã lưu. Các lượt Explorer chưa có bản ghi liên kết với `connectionRevision` của ứng dụng. Minh có thể đối chiếu revision cùng người tích hợp kết nối trên ứng dụng.

Nghị xác nhận các token cũ đã hết hiệu lực. Chín PNG Debugger/Explorer giữ vùng cần đối chiếu, gồm ID app/Page thử và grant, đã lược ô token, ID tài khoản cá nhân và mã truy vết. PNG của FB-03 chỉ giữ vùng Page, comment cha và reply. JSON dùng `[REDACTED]` cho cursor, hồ sơ giữ cờ có/vắng thay URL phân trang gốc và ghi có metadata URL đã che ở FB-09.

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
- Nghị ghi giờ chụp `2026-11-13T02:55:08Z` (09:55:08 +07:00) cho FB-04. Ảnh gốc của FB-04 chưa có trong checkout. Ảnh lượt dương FB-10 là lượt riêng. Giờ thực thi và HTTP status chưa quan sát được. JSON không có Graph error code/subcode.

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
| FB-05 GET `/{post-id}/reactions?summary=true&limit=1` | Đọc tổng reaction | **PASS**. `data: []`, `summary.total_count: 1`, `summary.viewer_reaction: "NONE"`, có cursors đã che. Kết quả mô tả viewer hiện tại và tổng edge. Lượt này không lọc loại. Đối chiếu LIKE ở lượt riêng bên dưới. Lý do danh sách rỗng chưa được xác nhận. |
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

Nội dung đã đối soát là “SP-018 — Phản hồi thử nghiệm từ Page demo để xác minh Graph API v27.0.” Nguồn duyệt trước POST là xác nhận của Nghị, có nhắc lại trong [báo cáo trên PR](https://github.com/nigPeter2503/Social_Pilot/pull/75#issuecomment-6857370903). [Debugger của Page token](api-verification/facebook/evidence/page-token-debugger.png) xác nhận `pages_manage_engagement`, `pages_read_user_content` và `pages_show_list` cho Page thử.

Ảnh giao diện do người dùng gửi ngày 15/11 cho thấy **Demo Page MD 24-31** là tác giả của comment cha “Test comment API - SP-010” và của reply hiện có ngay bên dưới. Nội dung khớp GET. Reply ID lấy từ GET vì giao diện trong ảnh chỉ hiển thị tên, vị trí và nội dung. Mốc chụp `2026-11-15T13:02:08Z` được quy đổi từ tên tệp `20:02:08` theo Asia/Saigon. Bản đính kèm cắt bỏ thanh trình duyệt và thông tin tài khoản cá nhân. Toàn bộ pixel trong vùng giữ lại khớp ảnh gốc. [Ảnh reply trên Page](api-verification/facebook/evidence/fb-03-reply-on-page.png).

HTTP status của ba request, giờ thực thi và `connectionRevision` chưa có nguồn. Nguồn duyệt trước POST vẫn là xác nhận của Nghị.

#### FB-08 · Post Insights

| Request v27.0 | Kỳ vọng | Kết quả `API_THAT` |
|---|---|---|
| GET `/{post-id}/insights?metric=post_media_view&period=lifetime` | Tổng số lần nội dung được phát hoặc hiển thị | **PASS**. `name=post_media_view`, `period=lifetime`, `values[0].value=17`. |
| GET `/{post-id}/insights?metric=post_total_media_view_unique&period=lifetime` | Tổng số người xem duy nhất | **PASS**. `name=post_total_media_view_unique`, `period=lifetime`, `values[0].value=3`. |
| GET insights, lượt lỗi trung gian | Dùng đúng Page token | OAuth `code=190`, `error_subcode=2069032`, yêu cầu Page Access Token. Báo cáo ghi metric thứ hai thành công ở lượt sau. Request chính xác, loại token và giờ của lượt lỗi chưa được xác nhận. |

Hai metric `COUNT` thuộc bài ảnh. Response rút gọn ghi `period=lifetime`. Start, end và breakdown chưa được cung cấp. HTTP status và giờ thực thi chưa quan sát được. Preflight ghi task `ANALYZE`. [Debugger của Page token](api-verification/facebook/evidence/page-token-debugger.png) xác nhận `read_insights` và `pages_read_engagement` trên credential Nghị dùng cho lượt thành công.

#### FB-10 · Cặp đối chứng follower

Các lượt dùng GET `/v27.0/1427548503779474?fields=id,followers_count`. Kỳ vọng lượt đủ quyền trả số follower và lượt thiếu quyền bị từ chối. Cặp chính là `PAGE-READ-02` và `PAGE-NO-ENG-03`. Hai lượt âm trước là bằng chứng bổ sung.

| Credential tham chiếu | Kết quả `API_THAT` | HTTP / Graph |
|---|---|---|
| `PAGE-READ-02` | Page khớp, `followers_count=1`. [Explorer, 2026-11-13T14:54:58Z](api-verification/facebook/evidence/fb-10-follower-success.png) | HTTP chưa quan sát được. JSON thành công. |
| `PAGE-NO-ENG-03` | Page token thiếu `pages_read_engagement`, phạm vi các quyền còn lại khớp lượt dương. [Explorer, 2026-11-13T15:14:01Z](api-verification/facebook/evidence/fb-10-page-token-matching-page-scope-error.png) | `OAuthException`, `code=100`. HTTP và subcode chưa quan sát được trong ảnh. |
| `PAGE-NO-ENG-02` | Page token giới hạn, GET bị từ chối. [Explorer, 2026-11-13T15:02:33Z](api-verification/facebook/evidence/fb-10-page-token-follower-error.png) | `OAuthException`, `code=100`. HTTP và subcode chưa quan sát được trong ảnh. |
| `PAGE-NO-ENG-01` | User token, GET bị từ chối. Báo cáo ghi Meta yêu cầu `pages_read_engagement` hoặc Page Public Content Access hoặc Page Public Metadata Access. [Explorer, 2026-11-13T14:55:42Z](api-verification/facebook/evidence/fb-10-follower-error.png) | `OAuthException`, `code=100`. HTTP và subcode chưa quan sát được trong ảnh. |

Ba ảnh Debugger trong bảng dưới ghi cùng app ID `1650819950076669` và trạng thái hợp lệ lúc chụp, cùng expiry `1794585600` và data-access expiry `1802356731`. Thời gian trên link là giờ chụp do Nghị ghi, đã quy đổi sang UTC. Các PNG trong checkout dùng tên rút gọn và vùng ảnh không hiện giờ chụp.

| Debugger | Loại và phạm vi thực tế | Grant hiển thị |
|---|---|---|
| [Lượt dương, 2026-11-13T14:47:40Z](api-verification/facebook/evidence/page-token-debugger.png) | `Page`, ID `1427548503779474`. Bốn quyền Pages có granular target là Page thử. | `read_insights`, `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`, `pages_manage_engagement`, `public_profile`. |
| [Lượt âm Page token, 2026-11-13T15:02:28Z](api-verification/facebook/evidence/fb-10-page-token-limited-debugger.png) | `Page`, ID `1427548503779474`. `pages_show_list` và `pages_manage_engagement` trỏ tới Page thử. `pages_read_user_content` hiển thị áp dụng mọi đối tượng. | `read_insights`, `pages_show_list`, `pages_read_user_content`, `pages_manage_engagement`, `public_profile`. |
| [Lượt bổ sung User token, 2026-11-13T14:55:32Z](api-verification/facebook/evidence/user-token-debugger.png) | `User`. `pages_show_list` và `pages_manage_engagement` trỏ tới Page thử. `pages_read_user_content` hiển thị áp dụng mọi đối tượng. | `read_insights`, `pages_show_list`, `pages_read_user_content`, `pages_manage_engagement`, `public_profile`. |

Trong lượt âm bổ sung với `PAGE-NO-ENG-02`, Debugger hiển thị `pages_read_user_content` áp dụng mọi đối tượng. Cặp chính bên dưới có cùng granular target cho các quyền Pages còn lại.

Ảnh Debugger bổ sung lúc `2026-11-13T15:10:36Z` (22:10:36 +07:00 theo tên ảnh) ghi một **User token** hợp lệ của cùng app. Token thiếu `pages_read_engagement`, có `read_insights`, `pages_show_list`, `pages_read_user_content`, `pages_manage_engagement` và `public_profile`. Ba quyền Pages có granular target là Page thử. Expiry `1794589200`, data-access expiry `1802358621`. [Debugger của User token](api-verification/facebook/evidence/fb-10-user-token-matching-page-scope-debugger.png)

Ảnh lúc `2026-11-13T15:12:09Z` (22:12:09 +07:00 theo tên ảnh) xác nhận `PAGE-NO-ENG-03` là **Page token** hợp lệ của app `1650819950076669` và Page `1427548503779474`. Token thiếu `pages_read_engagement` và giữ năm scope còn lại. `pages_show_list`, `pages_read_user_content` và `pages_manage_engagement` cùng trỏ tới Page thử, khớp phạm vi các quyền này ở lượt dương. Expiry `1794589200`, data-access expiry `1802358621`. [Debugger của Page token](api-verification/facebook/evidence/fb-10-page-token-matching-page-scope-debugger.png)

**PASS cho đối chứng FB-10.** Lượt dương trả follower 1. Lượt âm được Nghị cung cấp sau preflight của `PAGE-NO-ENG-03` trả `OAuthException #100` trên cùng GET. Hai credential có cùng loại, app, Page và các quyền còn lại theo Debugger. Expiry và data-access expiry phản ánh hai lần cấp riêng. Giờ ảnh là mốc bằng chứng. HTTP status, subcode và giờ thực thi chính xác chưa quan sát được.

### Bổ sung bằng chứng sau review

- Log gốc đã không còn. Với lượt đọc bổ sung, ghi UTC của request/response, HTTP status nếu thấy, credential ref và sourceRef ngay khi thực thi. Ảnh FB-03 ghi nhận reply hiện có trên Page.
- Minh xác nhận bằng chứng FB-03 và đối chứng FB-10. Nếu cần đối chiếu trên kết nối ứng dụng, ghi đúng kênh, revision và mốc kiểm chứng theo mục 5.

### Mẫu bằng chứng cho từng request

Sao chép một bản cho mỗi preflight, ca FB-01–10, trang kế tiếp, metric/filter, lượt đối soát hoặc đối chứng. Dùng cùng mã Page/bài/comment đã che trong cả lượt. Chỉ gắn `API_THAT` cho request đã gọi. Với lỗi hoặc thao tác chưa gọi, ghi riêng bước bị chặn. Nếu Explorer thiếu HTTP status, ghi `chưa quan sát được` hoặc lấy status từ network response cục bộ và chỉ chép số status.

```text
Ca / lượt:
Nguồn (API_THAT khi đã gọi) / trạng thái:
Revision lúc thử (git SHA) / người chạy / giờ gọi và giờ nhận response (UTC):
Giờ chụp (UTC) / created_time của đối tượng (nếu có):
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
| `READ_COMMENTS` | `UNVERIFIED` | FB-01/02 đọc và phân trang được trên bài thử. Cần nối lần kiểm chứng thao tác đọc với revision và giờ UTC trên kết nối ứng dụng. |
| `REPLY_COMMENT` | `UNVERIFIED` | FB-03 tạo và đọc lại reply với grant đã đối chiếu. Cần đối chiếu bằng chứng hiện có với revision và giờ UTC của lần kiểm chứng thao tác reply trên kết nối ứng dụng. |
| `READ_FOLLOWER_COUNT` | `UNVERIFIED` | FB-04 và lượt dương FB-10 trả `followers_count = 1`. Ảnh lượt dương chụp lúc `2026-11-13T14:54:58Z`. Cần revision và giờ UTC của lần kiểm chứng trên kết nối ứng dụng. |
| `READ_POST_METRICS` | `UNVERIFIED` | FB-05/06/08 đọc được reaction, comment và hai metric insights. Cần revision và giờ UTC của lần kiểm chứng trên kết nối ứng dụng. FB-07 ghi dữ liệu metric vắng riêng. |

Hồ sơ để `verifiedAt = null` cho cả bốn operation. Lý do cho `reasonCode` của `UNVERIFIED` là thiếu bằng chứng gắn với revision và thời điểm kiểm chứng trên kết nối. Minh cùng người tích hợp xác nhận kênh, `connectionRevision` hiện hành, giờ UTC `verifiedAt` và nguồn bằng chứng của từng operation trước khi chuyển `AVAILABLE`. Mã tham chiếu credential Explorer và SHA Git chỉ dùng để tra nguồn. Theo Social v1, `PERMISSION_REQUIRED` và `UNSUPPORTED` cũng cần `reasonCode` và `verifiedAt`. FB-10 ghi lỗi của credential giới hạn. FB-07 dùng `PROVIDER_OMITTED` ở cấp metric.

## 6. Bàn giao mapping Facebook cho SP-020

[Minh](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5861020207) và [Thiện](https://github.com/nigPeter2503/Social_Pilot/pull/67#pullrequestreview-5862180313) đã approve head `cdc633a` của SP-020 trước squash merge. Bảng dưới áp dụng kiểu dữ liệu đã merge cho phạm vi và kỳ quan sát được trên Facebook. Thiện cần xác nhận phần hiển thị riêng của Facebook trong bảng bàn giao. [Contract Room SP-012](room-schema-contract.md) đã merge qua #71, dùng khóa sample `(snapshotId, definitionId)` khi mỗi định nghĩa có một mẫu mỗi đợt. Nếu cần lưu nhiều kỳ, Nghị chốt period key với Đạt trước khi tạo constraint.

| Ca / metric key | Định nghĩa và đơn vị đề xuất | Kind / entity | Kỳ và mốc quan sát |
|---|---|---|---|
| FB-04 / `FOLLOWER_COUNT` | `followers_count`, số tài khoản theo dõi Page, `COUNT`. Bằng chứng live trả `1` trên Demo Page MD 24-31. | `GAUGE` / `CHANNEL` | Response thiếu mốc provider và kỳ. Dùng `observedAt = collectedAt` với basis `COLLECTION` khi xác nhận giờ thu. Hiện ghi riêng giờ ảnh. |
| FB-05 / `REACTION_COUNT` | `reactions.summary.total_count = 1` trên bài ảnh, `COUNT`. Filter LIKE cũng trả 1. Giữ tổng edge làm số reaction và LIKE là mẫu theo filter. Độ dài `data` và `viewer_reaction` chỉ là metadata của response. | `CUMULATIVE` / `POST` | Không có kỳ hoặc mốc provider. Dùng basis `COLLECTION` khi xác nhận giờ thu. |
| FB-06 / `COMMENT_COUNT` | `comments.summary.total_count` trên bài ảnh có reply là 4 với `toplevel`, 5 với `stream`. Giữ filter trong định nghĩa để UI chọn phạm vi đếm. | `CUMULATIVE` / `POST` | Không có kỳ hoặc mốc provider. Dùng basis `COLLECTION` khi xác nhận giờ thu. |
| FB-07 / `SHARE_COUNT` | `shares.count`, `COUNT`. Bài ảnh trả ID và vắng `shares`, ghi `null / PROVIDER_OMITTED`. | `CUMULATIVE` / `POST` khi field xác nhận tổng hiện tại | Không có kỳ hoặc mốc provider. |
| FB-08 / `IMPRESSION_COUNT` | Số impression theo định nghĩa provider, `COUNT`. Chưa có metric tương đương được xác minh. Hiện `null / UNVERIFIED`. | `POST`, chờ chốt `PERIOD` hoặc `CUMULATIVE` theo định nghĩa nguồn | Chờ period/start/end và mốc provider. |
| FB-08 / `post_media_view` | Số lần nội dung được phát hoặc hiển thị, `COUNT`. Bài ảnh trả 17. | `CUMULATIVE` / `POST` cho tổng lifetime quan sát được. | Provider trả `lifetime`. Response rút gọn chưa có start/end hoặc breakdown. |
| FB-08 / `post_total_media_view_unique` | Số người xem duy nhất của bài, `COUNT`. Bài ảnh trả 3. | `CUMULATIVE` / `POST` cho tổng lifetime quan sát được. | Provider trả `lifetime`. Response rút gọn chưa có start/end. |

| Phần UI | Ánh xạ theo SP-020 | Điểm Thiện xác nhận |
|---|---|---|
| Định nghĩa và phạm vi | Tra `MetricDefinition` qua `definitionId` để lấy `metricKey`, `providerMetricName`, đơn vị, `measurementKind` và quy tắc đếm. `MetricSample` giữ `scope={platform: FACEBOOK, channelId, connectionRevision}` và `entityType/entityId` (**CHANNEL** cho Page, **POST** cho bài). Dùng định nghĩa riêng cho tổng reaction, LIKE, comment cấp cao nhất, comment gồm reply và hai metric views/viewers. | Chọn phạm vi mặc định cho card comment. Duyệt nhãn và chỗ hiển thị riêng cho Views và Người xem duy nhất. |
| Kỳ và mốc đo | `PERIOD` có đủ `providerPeriodStart` và `providerPeriodEnd` theo UTC `[start,end)`. Hai insights lifetime là tổng tích lũy, giữ nhãn provider trong hồ sơ, để start/end trống khi chưa có nguồn. Render kỳ tuần theo coverage của SP-020. `observedAt` lấy từ mốc đo provider hoặc dùng `collectedAt` khi nguồn chỉ có giờ nhận, kèm `observedAtBasis`. Mỗi mẫu giữ `collectionId`, version và provenance. | Duyệt nhãn lifetime và cách hiển thị kỳ thiếu coverage. Nếu cần đưa nhãn provider hoặc breakdown vào DTO, thống nhất với SP-020. |
| Giá trị và lý do thiếu | Render `0` khi value bằng 0 và `missingReason=null`. Với value null, hiển thị giá trị thiếu theo lý do. FB-07 là `null / PROVIDER_OMITTED`. Impression là `null / UNVERIFIED`. ER theo impression chưa tính. Khi đủ dữ liệu, công thức cần các thành phần cùng định nghĩa, phạm vi và khoảng đo tương thích, cùng mẫu số dương. | Duyệt trạng thái hiển thị 0, field vắng, thiếu quyền và ER thiếu đầu vào. |
| Độ mới và lần thu gần nhất | Tính freshness của từng mẫu tốt theo `collectedAt` và ngưỡng thu, trả `isStale` và `lastSuccessAt`. Chưa có mẫu tốt thì cả hai để null. `lastAttemptAt`, `syncState` (`IDLE/LOADING/ERROR`), cờ offline và lỗi mô tả lần thu mới nhất. Refresh lỗi/offline giữ giá trị và mốc của mẫu cache, kèm trạng thái của lần thu. Chỉ biểu diễn empty khi query thành công và danh sách rỗng. | Duyệt cách hiện cache cũ cùng lỗi/offline, thời điểm thành công và thời điểm thử gần nhất. Xác nhận ngưỡng stale theo lịch thu thực tế. |
| Comment và metadata nullable | `CommentRecord` giữ scope, postId, commentId và parent riêng. FB-01 vắng parent nên giữ `parentCommentId=null`, `parentState=UNKNOWN`. Filter `toplevel` ghi ở query. FB-03 đối soát `parentState=KNOWN` với ID cha. `message` nullable theo `contentState`, trường vắng là `OMITTED`. `authorDisplayName`, `createdAt` và `providerUpdatedAt` nullable. Thiếu `createdAt` cần `timeMissingReason`. `syncedAt` là giờ nhận, còn `replyCapability` lấy theo revision hiện hành. FB-09 có permalink và ảnh ở bài, comment vắng attachment. | Rà hiển thị khi thiếu tên, thời gian, nội dung hoặc parent. Chốt nhu cầu render permalink/ảnh bài và attachment theo metadata nullable. |

Nếu UI cần lưu nhiều kỳ của một định nghĩa trong cùng snapshot, Nghị chuyển đề xuất period key cho Đạt để đánh giá ảnh hưởng khóa `(snapshotId, definitionId)`, schema và migration theo SP-012. Thay đổi shared DTO hoặc metric key đi qua SP-020.

## 7. Kiểm tra offline và bàn giao

- [Fixture](api-verification/facebook/fixtures.json) dùng ID, nội dung và cursor tổng hợp, `apiVersion = null`, provenance `MO_PHONG`.
- [Prototype](api-verification/facebook/response-prototype.cjs) giữ channel/post/comment/parent, không trộn trang giữa scope/revision/query, giữ 0 khác null và phân biệt lỗi với trang rỗng. Heuristic mã lỗi ở đây phục vụ fixture. Wrapper follower `POINT_IN_TIME` trong mẫu offline tương ứng khái niệm `GAUGE` khi bàn giao contract. Chưa có parser insights hoặc tính ER.
- [Kiểm tra fixture](api-verification/facebook/verify-fixtures.cjs) chạy lại trên Windows, Node v24.19.0, ngày 15/11/2026. Lượt review tài liệu này dùng hồ sơ live đã có.

| Lệnh đã chạy | Kết quả |
|---|---|
| `node docs/api-verification/facebook/verify-fixtures.cjs` | 55/55 PASS, `MO_PHONG`, 0 lượt gọi live |
| `node docs/contracts/verify-analytics-examples.cjs` | 75/75 PASS, `MO_PHONG` |
| `node --check docs/api-verification/facebook/response-prototype.cjs` | PASS |
| `node --check docs/api-verification/facebook/verify-fixtures.cjs` | PASS |
| `git diff --check`, diff PR GitHub từ base `5a5f82b` đến head `13f9de3` và diff gộp cả bản sửa local từ base đó | PASS |

### Đối chiếu tiêu chí issue #20

- [x] Dùng token thử với grant đã xác nhận trên Page và bài thật. Hồ sơ giữ request, danh tính Page thử và bằng chứng đã che.
- [ ] Đã đọc comment, phân trang, gửi một reply đã duyệt, GET đối soát và kiểm tra thiếu quyền. Ảnh đã cắt cho thấy reply dưới đúng comment cha, chờ Minh đối chiếu. Giờ thực thi ở các lượt thiếu nguồn được ghi là chưa xác nhận vì log gốc đã không còn.
- [x] Đã đọc follower và metrics bài. Trường thiếu có lý do. Bốn capability có kết quả thử và phần bằng chứng còn thiếu theo từng operation ở mục 5.
- [ ] Thiện xác nhận bảng UI, Minh xác nhận phần bằng chứng FB-03/10 và hai reviewer duyệt revision cuối. Đạt đánh giá đề xuất khóa/kỳ khi phát sinh yêu cầu lưu nhiều kỳ.
