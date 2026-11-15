# SP-019 — Chuẩn bị kiểm chứng LinkedIn comment, reply và metrics

Ngày kiểm tra gần nhất: 15/11/2026, Asia/Saigon. Owner: Tô Lãng Nghị (`bob35306`).
Issue: [#21](https://github.com/nigPeter2503/Social_Pilot/issues/21).
Reviewer dự kiến: Đạt (`dat532006`), Thiện (`PhuocThien06`).
Trạng thái: **đang làm; chuẩn bị offline, phần live chờ đầu vào SP-011**.
**0 API_THAT cho SP-019**. Bốn capability comment/reply/metrics/follower vẫn chờ bằng chứng theo từng tài khoản.

## 1. Phạm vi và revision

- Nhánh `feat/sp-019-linkedin-comments-metrics-spike`, tạo từ `origin/main`
  `0348982c8f295d946815da91eae491bd9645ac94` sau fetch 09/11/2026 khi working tree sạch.
- [Review PR #76 của Đạt](https://github.com/nigPeter2503/Social_Pilot/pull/76#pullrequestreview-5910459929)
  trên `63869f1`, cũng là HEAD của PR khi đối chiếu ngày 15/11.
- Bàn giao theo các hợp đồng đã merge: [Social v1 / SP-009](https://github.com/nigPeter2503/Social_Pilot/blob/4f69a3936ff5e3d988d907f7f2f46083dcd967b5/docs/social-contract.md),
  [Room SP-012](https://github.com/nigPeter2503/Social_Pilot/blob/5a5f82b732aeb97423925c6b2e2667c940777486/docs/room-schema-contract.md)
  và [Analytics SP-020](https://github.com/nigPeter2503/Social_Pilot/blob/e51dfc3375e389ed39b7ae18d44a8c7525d88cc1/docs/analytics-data-contract.md).
- Chỉ thêm tài liệu và prototype Node offline không dependency. Chưa sửa Java,
  SocialConnector, Room, UI, backend, cấu hình OAuth hoặc phiên bản build.

## 2. Đầu vào còn thiếu và người mở chặn

Đầu mối là Minh (`nigPeter2503`), [SP-011/#13](https://github.com/nigPeter2503/Social_Pilot/issues/13).
[PR #77](https://github.com/nigPeter2503/Social_Pilot/pull/77) được chuẩn bị dưới
dạng draft và đang mở, Ready for review khi đối chiếu ngày 15/11.
[Hồ sơ bàn giao tại `908316f`](https://github.com/nigPeter2503/Social_Pilot/blob/908316f1a65eb6b6944ce889cc578fbeb4e80273/docs/sp-011-handoff.md)
ghi nhận OAuth PERSON (MEMBER trong SP-019), UserInfo và đăng chữ thật trên Android
với version `202609`, scope quan sát được `openid profile w_member_social`, product
Share on LinkedIn Default Tier và Sign In with LinkedIn using OpenID Connect Standard Tier.
Hồ sơ có author URN và hai post URN riêng cho Android và Postman. URL bài Android
do owner xác nhận. Token được tham chiếu qua kho bảo mật theo channel/revision,
và cấu hình app đã thử chưa có refresh token.

Minh còn cần xác nhận cách truy cập token thử hợp lệ qua kho bảo mật, version
dùng cho SP-019, product/access tier và scope riêng cho từng thao tác, tài khoản/
role tổ chức nếu có, URN và phạm vi đối tượng thử. Bằng chứng đăng bài SP-011
chỉ bao phủ thao tác đăng chữ. LI-03 cần xác nhận riêng comment cha và nội dung
được phép gửi. Giữ token/client secret trong kho bảo mật.

SP-009 cung cấp contract capability/lỗi; SP-011 cung cấp đầu vào LinkedIn thật.
Đạt xác nhận schema/ID/ownership SP-012; Thiện xác nhận trường UI theo SP-020.
Nội dung bàn giao trong hồ sơ này **chưa gửi**. Không tự đổi shared contract.

### Capability bàn giao theo Social v1

| SocialOperation | Từng account MEMBER | Từng account ORGANIZATION | ReasonCode |
|---|---|---|---|
| `READ_COMMENTS` | `UNVERIFIED` (LI-01/02) | `UNVERIFIED` (LI-01/02) | `SP011_TEST_ACCESS_PENDING` |
| `REPLY_COMMENT` | `UNVERIFIED` (LI-03) | `UNVERIFIED` (LI-03) | `SP011_REPLY_TARGET_PENDING` |
| `READ_POST_METRICS` | `UNVERIFIED` (LI-06/08) | `UNVERIFIED` (LI-07/08) | `SP011_TEST_ACCESS_PENDING` |
| `READ_FOLLOWER_COUNT` | `UNVERIFIED` (LI-04) | `UNVERIFIED` (LI-05) | `SP011_TEST_ACCESS_PENDING` |

`verifiedAt=null` cho mỗi capability trên mỗi account. Ghi bằng chứng theo
`channelId`, `accountType`, `connectionRevision` và thao tác đã thử. ReasonCode là nhãn đề xuất cho spike.
HTTP 403 chưa xác định nguyên nhân giữ `UNVERIFIED`. Chỉ gán `PERMISSION_REQUIRED`
khi đã xác nhận thiếu quyền cho thao tác và ghi thời điểm kiểm chứng. Ghi kết quả
từng metric riêng để một metric thành công không che trường khác còn thiếu.

## 3. Nguồn chính thức và version khảo sát

Đã đọc nội dung các trang dưới ngày 09/11/2026, view `li-lms-2026-10` khi có.
Trang có banner yêu cầu đăng nhập nhưng nội dung kỹ thuật đọc được; không chứng minh
app của nhóm đã được cấp quyền. Các request vẫn phải kiểm chứng với Minh.

| Nguồn | Kết quả khảo sát dùng cho phép thử |
|---|---|
| [Versioning](https://learn.microsoft.com/en-us/linkedin/marketing/versioning?view=li-lms-2026-10) | Trang liệt kê `202610`; chọn làm **version ứng viên tài liệu**, chưa pin cho app. Header `Linkedin-Version: YYYYMM`; `202511` có lịch sunset 16/11/2026. Đọc lại trước live |
| [Increasing Access](https://learn.microsoft.com/en-us/linkedin/marketing/increasing-access?view=li-lms-2026-10) | Community Management cần app được duyệt; product, scope và vai trò phải kiểm tra riêng. Không suy quyền analytics/comment từ quyền đăng bài |
| [Comments](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/comments-api?view=li-lms-2026-10) | Đọc/ghi/nested comment, composite URN; `r_member_social_feed` bị hạn chế cho một số developer. Request phải có Rest.li 2.0 và version |
| [Member followers](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/members/follower-statistics?view=li-lms-2026-10) | `memberFollowersCount`, `r_member_profileAnalytics`; tách finder `me` và `dateRange` |
| [Member post statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/members/post-statistics?view=li-lms-2026-10) | `memberCreatorPostAnalytics`, `r_member_postAnalytics`; tách từng metric và aggregation; chưa viết parser endpoint này |
| [Organization lookup](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/organization-lookup-api?view=li-lms-2026-10) | Tổng follower qua `networkSizes`, edge `COMPANY_FOLLOWED_BY_MEMBER`, field `firstDegreeSize`; kiểm tra quyền/vai trò app |
| [Organization follower statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/follower-statistics?view=li-lms-2026-10) | Không còn `totalFollowerCounts`; không cộng các facet để suy tổng. Thống kê theo kỳ tách khỏi tổng hiện tại |
| [Organization share statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/share-statistics?view=li-lms-2026-10) | Organic, cửa sổ 12 tháng; `likeCount` có thể âm. `engagement` của provider không tự bằng ER đề xuất SP-020 |
| [Pagination](https://learn.microsoft.com/en-us/linkedin/shared/api-guide/concepts/pagination), [protocol](https://learn.microsoft.com/en-us/linkedin/shared/api-guide/concepts/protocol-version), [errors](https://learn.microsoft.com/en-us/linkedin/shared/api-guide/concepts/error-handling) | Phân trang `start/count`; URI phải encode đúng Rest.li; lỗi HTTP không chuyển thành empty/0. 403 cần điều tra quyền/product/role/field, không tự kết luận UNSUPPORTED |

Fixture ghi `documentationVersionCandidate=202610`, `liveApiVersion=null`.
Không lấy ngày hiện tại để tự suy header `202611`. Chỉ pin version thật sau khi
đối chiếu app/product và ghi trong evidence. Token luôn đặt ở header công cụ thử.

## 4. Ma trận phép thử live

Prefix ứng viên: `https://api.linkedin.com/rest`. Dùng `Linkedin-Version: {V}`,
`X-Restli-Protocol-Version: 2.0.0`; POST JSON có Content-Type phù hợp.
URN trong bảng là placeholder chưa encode; phải encode theo từng path/query Rest.li,
tránh encode hai lần hoặc coi ID post hiển thị trên UI là URN đã xác minh.

| Case | Account / request ứng viên | Scope / điều kiện phải đối chiếu | Expected / actual hiện tại |
|---|---|---|---|
| LI-01 | Cả hai: GET `/socialActions/{shareUrn hoặc ugcPostUrn}/comments` | MEMBER: `r_member_social_feed` restricted; ORGANIZATION: `r_organization_social_feed`, role ADMINISTRATOR hoặc DIRECT_SPONSORED_CONTENT_POSTER theo docs | Comment thuộc bài đã duyệt; đối chiếu object/ID/parent/time; **NOT_RUN**, chờ SP-011 |
| LI-02 | GET cùng edge với `start=0&count={N}`, sau đó `start=N` nếu continuation hợp lệ; GET edge comment URN để đọc reply | Như LI-01; giữ nguyên version/query/connectionRevision | Hai trang khi đủ dữ liệu, kiểm tra offset, overlap/dedup và nested parent; **NOT_RUN** |
| LI-03 | POST `/socialActions/{commentUrn}/comments`, body actor/message/object/parentComment đã xác minh | MEMBER: `w_member_social_feed`; ORGANIZATION: `w_organization_social_feed`, role được docs cho phép. Phải có xác nhận đối tượng/nội dung | Gửi một lần, lưu ID tham chiếu đã che và đối soát đúng parent/actor/text; **NOT_RUN**, chưa được xác nhận ghi |
| LI-04 | MEMBER: GET `/memberFollowersCount?q=me` | `r_member_profileAnalytics`, product thực tế | Current total follower, không dùng connections; **NOT_RUN** |
| LI-05 | ORGANIZATION: GET `/networkSizes/{organizationUrn}?edgeType=COMPANY_FOLLOWED_BY_MEMBER` | Organization lookup liệt kê `rw_organization_admin`; xác minh app/role/access cho request thực tế | `firstDegreeSize` là follower trong đúng edge tổ chức; **NOT_RUN** |
| LI-06 | MEMBER: GET `/memberCreatorPostAnalytics?q=entity&entity={Rest.li union share hoặc ugc}&queryType={metric}&aggregation=TOTAL` | `r_member_postAnalytics`; từng IMPRESSION/REACTION/COMMENT/RESHARE độc lập | Target entity, count, kỳ/aggregation/source; thiếu giữ null, **NOT_RUN** |
| LI-07 | ORGANIZATION: GET `/organizationalEntityShareStatistics?q=organizationalEntity&organizationalEntity={orgUrn}&shares=List({shareUrn})` hoặc filter `ugcPosts` encode theo Rest.li | `rw_organization_admin`, ADMINISTRATOR; kiểm tra loại post | Một post/organic, giữ cửa sổ nguồn; impression/comment/share/like khác all-reaction; **NOT_RUN** |
| LI-08 | Thử finder `dateRange` member hoặc `timeIntervals` toàn organization trên endpoint tương ứng | Scope analytics/role như case tương ứng; không ghép timeIntervals với filter single share/UGC | Kỳ start/end, granularity/timezone; không dùng series thay current total; **NOT_RUN** |
| LI-09 | 401/403/429, malformed URN/version và thiếu dữ liệu | Fixture cho lỗi khó tái hiện; không cố gây throttle | Error khác empty/0/unsupported; API thật **NOT_RUN**, có kiểm tra **MO_PHONG** |

### LI-02 — Đối chiếu offset

Ghi `requestPaging.start/count` từ request đã gửi, kể cả giá trị mặc định mà
caller áp dụng. `paging.start/count` của response phải khớp cả hai giá trị.
Offset kế tiếp dự kiến là `request.start + request.count`, giữ nguyên `count`.
Mỗi link `rel=next` phải có đúng một tham số `start` bằng offset dự kiến và đúng
một `count` bằng count của request. Nếu có `total`, link next không được xuất hiện
khi offset dự kiến đã đạt/vượt total. Ví dụ `start=0,count=2,total=5,next start=4`
trả `hasMore=null`, `nextStart=null`, `paginationReason=PAGING_CONFLICT`.
Thiếu request/response paging hoặc thiếu cả next và total thì continuation
`NOT_ESTABLISHED`. Gọi trang sau qua endpoint đã duyệt và offset đã kiểm tra.

Member analytics dùng union `entity=(share:{encodedShareUrn})` hoặc
`entity=(ugc:{encodedUgcPostUrn})` theo docs; không gửi URN trần thay union.
Nếu dùng DAILY phải kiểm tra metric hỗ trợ; docs lưu ý hạn chế daily impression
ở single post. Hồ sơ này chưa cam kết hỗ trợ mọi metric/aggregation.

## 5. Quy trình live và evidence sau khi mở chặn

1. Minh xác nhận nơi cấu hình, account/product/access tier/scope/role, URN và phạm vi
   thử. Đọc lại tài liệu/version; ghi capability từng thao tác, không suy từ OAuth.
2. Chạy các phép đọc riêng. Ghi HTTP/expected/actual, thời điểm UTC, provider metric,
   đơn vị/kỳ/organic-or-paid và version. Thiếu hoặc rỗng không tự thành số 0.
3. Phân trang giữ cùng query/target và revision; tái tạo request từ endpoint đã duyệt,
   không follow/lưu nguyên paging URL. Chưa đủ bằng chứng trang cuối thì giữ UNKNOWN;
   chống lặp và có giới hạn số trang trong công cụ live sau này.
4. Reply chỉ khi đã có xác nhận. Với 201, đối chiếu `X-RestLi-Id`, `body.id`,
   `body.commentUrn`, `body.object`, `body.parentComment` và thread/parent đã
   xác minh trong request khi có dữ liệu. Đọc lại qua GET comment rồi đối chiếu
   thread, parent, actor và nội dung để xác nhận. 201 chỉ có header ID chờ đối soát.
   Timeout/5xx/mất ID/mâu thuẫn định danh giữ `UNKNOWN`; `canRetryAutomatically=false`.
5. Che Authorization/token/cookie, paging URL, dữ liệu cá nhân và ID nhạy cảm trước
   khi lưu. Prototype không phải công cụ che bí mật và từ chối provenance API_THAT.
6. Bổ sung evidence từng case, gửi Minh capability/lỗi, Thiện DTO có definition/source,
   Đạt lưu ý ID/schema. Sửa SP-020 bằng revision riêng được review khi cần.

Mẫu evidence:

`caseId | source | revision | accountType | product/accessTier | role/grantedScopes |
apiVersion | method/endpoint | collectedAtUTC | metric/definition/unit |
providerPeriod/start/end/timezone | expected | actual/HTTP/errorCode |
redactedEvidenceRef | verifiedBy | blocker/owner`

Reply thêm `approvedTestRef | requestTime | parentRef | replyIdRef |
reconciledAt | outcome | noAutomaticRetry` trong phạm vi chia sẻ phù hợp.

## 6. Bộ offline và giới hạn mapping

- [fixtures.json](api-verification/linkedin/fixtures.json): hoàn toàn **MO_PHONG**,
  ID/nội dung/thời gian/URL tổng hợp; không phải response thật đã che.
- [response-prototype.cjs](api-verification/linkedin/response-prototype.cjs): khảo sát
  comment/paging/dedup, follower theo account, organic post statistics tổ chức và
  outcome reply. Chỉ nhận provenance MO_PHONG; không HTTP/credential/Room/Java API.
- [verify-fixtures.cjs](api-verification/linkedin/verify-fixtures.cjs): đối chiếu ca
  bình thường/rỗng/lỗi, precision ID, UTC, thiếu field, overlap, scope/revision,
  missing/0, like âm, request total khác series và reply chưa rõ.

### Ánh xạ sang Room SP-012

- Dùng `commentUrn` đã đối chiếu với `object` và `id` làm `externalCommentId`
  dạng chuỗi trong khóa `(platform, channelId, externalCommentId)`. Khi response
  bỏ trường URN, dựng composite từ thread và ID hợp lệ. Từ chối phép đọc khi mâu thuẫn.
- Tra bài theo `(channelId, externalPostId=postUrn)` để lấy ID local của
  `PublishedPost` cùng kênh. Fixture nhận cặp `verifiedPostThread` do caller
  cung cấp cho phạm vi request hiện tại. Thiếu mapping đã xác minh giữa share/
  UGC và thread thì giữ `postUrn=null`, `postState=UNVERIFIED`; hoãn ghi Room
  đến khi xác minh được mapping và tìm được bài cùng kênh.
- Với request nested, thread của row phải khớp thread của target. Nếu provider
  có trả `parentComment`, toàn bộ URN phải khớp target, gồm ID cha và thread.
  Nếu bỏ trường thì giữ `UNKNOWN`. Khi biết URN cha nhưng cha chưa đồng bộ,
  giữ URN external và trạng thái parent chờ đồng bộ. Khi cha về, chỉ liên kết
  ID local sau khi xác nhận cùng kênh và cùng bài. Trường message vắng giữ `OMITTED`.
- Trong cùng transaction ghi comment/checkpoint, đối chiếu `connectionRevision`
  với kết nối hiện hành và bỏ kết quả từ revision cũ. Nếu khóa comment đã tồn tại
  dưới post khác, giữ liên kết đã lưu, ghi nhận xung đột đã che dữ liệu để đối soát
  mapping với provider, và giữ checkpoint của trang xung đột để chạy lại sau xử lý.

### Merge trang theo Analytics SP-020

Chỉ merge trong cùng scope/query/version và cùng mapping đã xác minh nếu có.
Khi cả hai row có `providerUpdatedAt`, ưu tiên bản sửa mới hơn. Khi không so
sánh được thời điểm sửa, dùng thời điểm thu. Nếu cùng thời điểm sửa mà các trường
cùng có mặt mâu thuẫn, giữ bản đã lưu và đánh dấu `needsRefresh`. Nếu row được
chọn bỏ sót `actorUrn`, `createdAt` hoặc `providerUpdatedAt`, giữ giá trị hợp
lệ còn lại từ row kia. Trang lỗi không được merge như trang rỗng.

Org like âm giữ ở PROVIDER_LIKE_COUNT; REACTION_COUNT giữ null/DEFINITION_MISMATCH.
Cửa sổ organic 12 tháng không trộn với lifetime member hoặc paid metrics để tính ER.
Docs cho phép hiểu post vắng trong kết quả specific-post là 0; prototype vẫn giữ null
vì chưa xác minh request/ownership/cửa sổ bằng API thật. Chỉ áp dụng ngoại lệ 0 sau khi
các điều kiện đó đã được đối chiếu và contract được review. Endpoint organization share
statistics không hỗ trợ pagination; không áp dụng parser paging comment cho endpoint này.
Các reason và measurementKind ở đây là đề xuất khảo sát, chưa thêm vào enum chung.
Chưa có parser member post analytics/dateRange, bộ tính ER, adapter hoặc UI tích hợp.
Reply prototype chỉ accept 201 + ID nhất quán trong fixture; đây là quy tắc thận trọng
chưa kiểm chứng live, các 2xx khác giữ UNKNOWN và không retry tự động.

## 7. Kiểm tra, kết quả và bàn giao

Tại repo root, chạy từng lệnh và kiểm tra exit code:

```powershell
node docs/api-verification/linkedin/verify-fixtures.cjs
node --check docs/api-verification/linkedin/response-prototype.cjs
node --check docs/api-verification/linkedin/verify-fixtures.cjs
git diff --check
```

- Lượt đầu: 94 checks PASS, một check UTC FAIL vì expected bị ghi nhầm 02:20Z;
  epoch tổng hợp thực tế là 01:00Z. Sửa expected, giữ parser epoch milliseconds.
  Không coi exit cuối của chuỗi lệnh shell là kết quả validator đầu chuỗi.
- Ngày 09/11, sau sửa và rà soát ca biên: Node v24.19.0, **102/102 checks PASS**, exit 0;
  hai file CJS `node --check` PASS, mỗi lệnh exit 0. Tất cả MO_PHONG, 0 API_THAT.
- Trước đợt xử lý review, bản trích xuất đúng HEAD `63869f1` đạt 102 checks
  MO_PHONG. Diff local có sẵn thêm hai ca lệch thread ở phép đọc và reply, đạt
  104 checks MO_PHONG. Cả hai có 0 API_THAT.
- QA UTF-8/LF/final newline/whitespace của 4 file mới và plan PASS; ba link local
  trong hồ sơ này PASS; Git whitespace bao gồm file untracked PASS.
  Lượt QA link toàn plan đầu phát hiện hai link local thuộc nhánh SP-018/SP-020
  không hiện diện trên SP-019; đổi link sang revision đã push, giữ nguyên nội dung nhánh.
- Chưa chạy build Android/backend vì không đổi code ứng dụng; vấn đề JDK21/SDK36
  trước đó chưa được xác minh lại. Chưa thử HTTP/Java/Room hoặc API/reply thật.
- Ngày 15/11, working tree tại HEAD `63869f19d4874a6b9919b0c41255e59ef20d8e4d`
  kèm thay đổi chưa commit đạt **126/126 checks MO_PHONG**, 0 API_THAT.
  Hai lệnh `node --check` và `git diff --check` đều exit 0.
- PR #76 chờ xem diff local, review Đạt/Thiện và bằng chứng live từng ca.

### Checkpoint

Khi Minh xác nhận metadata và nơi cấu hình, pin version rồi chạy LI-01 và ca follower
LI-04 hoặc LI-05 theo account trên đối tượng thử đã xác nhận. LI-03 cần riêng comment
và nội dung reply đã duyệt. Ghi bằng chứng theo từng capability trong bảng trên.
