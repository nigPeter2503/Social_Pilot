# SP-019 — Chuẩn bị kiểm chứng LinkedIn comment, reply và metrics

Ngày kiểm tra: 09/11/2026, Asia/Saigon. Owner: Tô Lãng Nghị (`bob35306`).
Issue: [#21](https://github.com/nigPeter2503/Social_Pilot/issues/21).
Reviewer dự kiến: Đạt (`dat532006`), Thiện (`PhuocThien06`).
Trạng thái: **đang làm; chuẩn bị offline, phần live chờ đầu vào SP-011**.
**0 API_THAT**; chưa có account hoặc quyền nào được xác minh trên app thật.

## 1. Phạm vi và revision

- Nhánh `feat/sp-019-linkedin-comments-metrics-spike`, tạo từ `origin/main`
  `0348982c8f295d946815da91eae491bd9645ac94` sau fetch 09/11/2026 khi working tree sạch.
- REST GitHub: #13/#21 còn OPEN, chưa có bình luận hay branch/PR SP-011/SP-019
  tại lần kiểm tra. Project live và công việc chưa push của nhóm chưa xác minh.
- [SP-009 PR #66](https://github.com/nigPeter2503/Social_Pilot/pull/66), head `289e4d1`,
  và [SP-020 PR #67](https://github.com/nigPeter2503/Social_Pilot/pull/67), head `bf89da1`,
  vẫn OPEN. Contract SP-020 còn draft-2; SP-012 chưa có thiết kế đã xác nhận.
- Nhánh SP-018/commit `9c80601` được giữ nguyên. Các file Facebook và contract SP-020
  thuộc nhánh riêng, không nằm trên main/SP-019. Không coi chúng đã được tích hợp.
- Chỉ thêm tài liệu và prototype Node offline không dependency. Chưa sửa Java,
  SocialConnector, Room, UI, backend, cấu hình OAuth hoặc phiên bản build.

## 2. Đầu vào còn thiếu và người mở chặn

Minh (`nigPeter2503`), [SP-011/#13](https://github.com/nigPeter2503/Social_Pilot/issues/13),
cần xác nhận account MEMBER hay ORGANIZATION; product/access tier của app; scope
đã cấp thực tế, loại/hiệu lực token và **nơi cấu hình an toàn**; author/organization,
post/comment URN và vai trò liên quan; đối tượng và nội dung reply được phép thử.
Chưa được chỉ vị trí mới trong phiên này; không suy rằng token chắc chắn không tồn tại.
Không gửi token/client secret qua chat, issue, fixture hoặc PR.

SP-009 cung cấp contract capability/lỗi; SP-011 cung cấp đầu vào LinkedIn thật.
Đạt xác nhận schema/ID/ownership SP-012; Thiện xác nhận trường UI theo SP-020.
Nội dung bàn giao trong hồ sơ này **chưa gửi**. Không tự đổi shared contract.

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
| LI-02 | GET cùng edge với `start=0&count={N}`, sau đó `start=N`; GET edge comment URN để đọc reply | Như LI-01; giữ nguyên version/query/connectionRevision | Hai trang khi đủ dữ liệu, overlap/dedup, nested parent; **NOT_RUN** |
| LI-03 | POST `/socialActions/{commentUrn}/comments`, body actor/message/object/parentComment đã xác minh | MEMBER: `w_member_social_feed`; ORGANIZATION: `w_organization_social_feed`, role được docs cho phép. Phải có xác nhận đối tượng/nội dung | Gửi một lần, lưu ID tham chiếu đã che và đối soát đúng parent/actor/text; **NOT_RUN**, chưa được xác nhận ghi |
| LI-04 | MEMBER: GET `/memberFollowersCount?q=me` | `r_member_profileAnalytics`, product thực tế | Current total follower, không dùng connections; **NOT_RUN** |
| LI-05 | ORGANIZATION: GET `/networkSizes/{organizationUrn}?edgeType=COMPANY_FOLLOWED_BY_MEMBER` | Organization lookup liệt kê `rw_organization_admin`; xác minh app/role/access cho request thực tế | `firstDegreeSize` là follower trong đúng edge tổ chức; **NOT_RUN** |
| LI-06 | MEMBER: GET `/memberCreatorPostAnalytics?q=entity&entity={Rest.li union share hoặc ugc}&queryType={metric}&aggregation=TOTAL` | `r_member_postAnalytics`; từng IMPRESSION/REACTION/COMMENT/RESHARE độc lập | Target entity, count, kỳ/aggregation/source; thiếu giữ null, **NOT_RUN** |
| LI-07 | ORGANIZATION: GET `/organizationalEntityShareStatistics?q=organizationalEntity&organizationalEntity={orgUrn}&shares=List({shareUrn})` hoặc filter `ugcPosts` encode theo Rest.li | `rw_organization_admin`, ADMINISTRATOR; kiểm tra loại post | Một post/organic, giữ cửa sổ nguồn; impression/comment/share/like khác all-reaction; **NOT_RUN** |
| LI-08 | Thử finder `dateRange` member hoặc `timeIntervals` toàn organization trên endpoint tương ứng | Scope analytics/role như case tương ứng; không ghép timeIntervals với filter single share/UGC | Kỳ start/end, granularity/timezone; không dùng series thay current total; **NOT_RUN** |
| LI-09 | 401/403/429, malformed URN/version và thiếu dữ liệu | Fixture cho lỗi khó tái hiện; không cố gây throttle | Error khác empty/0/unsupported; **MO_PHONG**, chưa API_THAT |

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
4. Reply chỉ khi đã có xác nhận. Nhận ID từ `x-restli-id` hoặc body là accepted;
   phải đọc lại và đối chiếu nền tảng đúng parent/actor/nội dung mới xác nhận.
   Timeout/5xx/mất ID/mâu thuẫn ID: UNKNOWN, đối soát trước, không tự gửi lại.
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

Quy tắc đề xuất: ID là string; khóa comment gồm platform/channel và composite
`object + id`, không dùng ID số đơn lẻ hoặc `x-resourceidentity-urn`. Không tự đổi
share/ugcPost/activity URN cho nhau; việc xác minh alias thật chưa có trong prototype.
Thiếu parent giữ UNKNOWN, thiếu message giữ OMITTED, không suy ROOT/DELETED.
Merge cùng scope/query/version; quan sát mới hơn thắng trong khảo sát offline.
Chưa có transaction, tombstone, alias resolution hay chống race disconnect.

Pagination có next link hoặc total mới xác định hasMore; thiếu cả hai giữ null,
kể cả trang ngắn. Prototype không kiểm chứng chuỗi trang đã được thu đủ; phần live
cần đối chiếu start/count, next và vòng lặp thực tế. Lỗi không merge như trang rỗng.

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
- Sau sửa và rà soát ca biên: Node v24.19.0, **102/102 checks PASS**, exit 0;
  hai file CJS `node --check` PASS, mỗi lệnh exit 0. Tất cả MO_PHONG, 0 API_THAT.
- QA UTF-8/LF/final newline/whitespace của 4 file mới và plan PASS; ba link local
  trong hồ sơ này PASS; Git whitespace bao gồm file untracked PASS.
  Lượt QA link toàn plan đầu phát hiện hai link local thuộc nhánh SP-018/SP-020
  không hiện diện trên SP-019; đổi link sang revision đã push, giữ nguyên nội dung nhánh.
- Chưa chạy build Android/backend vì không đổi code ứng dụng; vấn đề JDK21/SDK36
  trước đó chưa được xác minh lại. Chưa thử HTTP/Java/Room hoặc API/reply thật.
- Chưa commit/push, tạo PR, gửi bàn giao/review hoặc cập nhật Project trong phiên này.
  Reviewer dự kiến Đạt/Thiện; task #21 giữ mở. Hai human approvals/checks/merge và
  bằng chứng live cần thiết vẫn còn thiếu; fixture PASS không thay nghiệm thu.

### Checkpoint

Phần offline sẵn sàng review sau QA. Bước kế là Minh SP-011 cung cấp metadata và
vị trí cấu hình an toàn, xác nhận đối tượng thử; pin version/account rồi chạy LI-01–09.
SP-018 vẫn chờ Minh SP-010; SP-020 còn review ở PR #67. Không ghi giờ công khi chưa đo.
