# SP-045.06 · Lượt thu follower đầu tiên qua app test

**Chờ nghiệm thu live · 19/11/2026 21:52 Asia/Saigon · Nghị · #47.** Cả ba account dừng trước manual refresh. Phiên này thực hiện 0 request tới Facebook/LinkedIn. Chưa có response của app, collection, snapshot hoặc mốc thu đầu. Gap dưới đây ghi trong hồ sơ nghiệm thu. Chưa phát sinh attempt trong Room.

## Bàn giao và khả năng chạy

Nhánh `feat/sp-045-follower-adapter`. HEAD, merge-base, origin/main local và main trên GitHub cùng ở `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa` (merge #75). Đã đối chiếu mục và dòng tiến độ .06, quy tắc definition/timestamp ở §5, lịch thu và gap ở §9. [Analytics data contract](analytics-data-contract.md)

| Scope test | Channel / identity / revision hiện hành | READ_FOLLOWER_COUNT, grant và credential reference | GET và field trong adapter .01 | Ghi / đọc lại Room |
|---|---|---|---|---|
| Facebook PAGE · FB-04/10 | Chưa có lookup Channel production hoặc Page test gắn revision hiện hành | UNVERIFIED, chưa có verifiedAt hay reference của app. `PAGE-READ-02` là bí danh Explorer W1 có `pages_read_engagement`; lượt dương trả 1. Minh cần xác nhận lại Page, quyền và reference trên revision app | `/v27.0/{page}?fields=id,followers_count` → `followers_count`. v27.0 đã dùng ở W1, chờ pin cho kết nối app | NOT_RUN / NOT_RUN |
| LinkedIn MEMBER (PERSON) · LI-04 | Chưa xác nhận Channel, member đứng sau `me` hoặc revision | UNVERIFIED, thiếu verifiedAt, reference theo member và xác nhận product/tier, `r_member_profileAnalytics` | `/rest/memberFollowersCount?q=me` → `elements[].memberFollowersCount`, current total. `202610` là version fixture, live chưa pin | NOT_RUN / NOT_RUN |
| LinkedIn ORGANIZATION · LI-05 | Chưa xác nhận Channel, tổ chức/actor được phép hoặc revision | UNVERIFIED, thiếu verifiedAt, reference, product/tier và role của actor trên tổ chức. Minh kiểm scope thực tế cho networkSizes, gồm đối chiếu `rw_organization_admin` trong hồ sơ W1 | `/rest/networkSizes/{organization-urn}?edgeType=COMPANY_FOLLOWED_BY_MEMBER` → `firstDegreeSize` của đúng edge. `202610` là version fixture, live chưa pin | NOT_RUN / NOT_RUN |

**Minh · HO-CHANNEL / HO-GRANT.** #24 mở, 0 issue comment. Các ref đã công bố chưa có nhánh SP-022 giao DAO/lookup bền qua restart và reconnect. OAuth Facebook #74 head `d1d55a0`, bằng chứng Graph #75 head `853a1ac`, spike LinkedIn #76 head `306a1da` và OAuth/publish #77 head `1dea02e` cung cấp ngữ cảnh W1. Cả ba scope còn thiếu xác nhận grant follower gắn với identity, revision và reference hiện hành trong app. [SP-022](https://github.com/nigPeter2503/Social_Pilot/issues/24) · [Facebook W1](https://github.com/nigPeter2503/Social_Pilot/pull/75) · [LinkedIn SP-019](https://github.com/nigPeter2503/Social_Pilot/pull/76) · [LinkedIn SP-011](https://github.com/nigPeter2503/Social_Pilot/pull/77)

**Đạt · HO-SCHEMA.** #14 đã đóng. #71 head `11abeb4`, merge `5a5f82b`, giao quy ước Room. #82 đang mở, head `178e05c`, đề xuất v1→v2 cho Brief, 0 review và 0 inline comment ở thời điểm kiểm. Chưa tìm thấy bàn giao owner, base/schema SHA, version nguồn–đích và thứ tự merge dành cho follower. [Quy ước Room](https://github.com/nigPeter2503/Social_Pilot/pull/71) · [Migration Brief](https://github.com/nigPeter2503/Social_Pilot/pull/82)

**Nghị · binding thực tế.** `SocialPilotApplication.createContainer()` gọi constructor mặc định của `AppContainer`, truyền collector `null` cho `MetricCollectionWork`. Worker có nhánh trả `DEPENDENCIES_UNAVAILABLE`. Builder mở `socialpilot.db` v1 chỉ có `DraftEntity/drafts()`; blob export `5172492e197da5086e9ac37f1c0bcc8a5db5e15b`. `SnapshotWriter` đã có port giao dịch; `MetricCollectionCoordinator.cached(...)` gọi `SnapshotDao.read(...)`. Implementation mô phỏng và runner inject coordinator nằm ở test. Chưa có kết nối ghi/đọc follower bằng Room trong app. [Adapter .01](sp-045-follower-adapter-verification.md) · [Writer .03](sp-045-snapshot-storage-verification.md) · [Worker .04](sp-045-collection-worker-verification.md)

## Nhật ký kiểm chứng

| Lệnh hoặc phép kiểm | Kết quả phiên .06 |
|---|---|
| `git status --short --branch --untracked-files=all`, `git diff`, `git diff --cached --stat`, `git ls-files --others --exclude-standard` | Đầu phiên có 3 file tracked đã sửa và 39 untracked từ trước. Index sạch. |
| `git rev-parse HEAD origin/main`, `git merge-base HEAD origin/main`, `git worktree list --porcelain`, `git -c credential.helper= -c core.askPass= ls-remote origin 'refs/heads/*' 'refs/pull/*/head'` | SHA như trên. Truy vấn remote ban đầu lỗi DNS trong sandbox, chạy lại chỉ đọc thành công. |
| GitHub `GET /repos/nigPeter2503/Social_Pilot/issues/{n}` (#14/24/43/47/71/74/75/76/77/79/81/82), `GET …/pulls/82/{reviews,comments}` | Đối chiếu mô tả #14/24/43/47/82 với ref PR. #24/43/47 mở, mỗi issue có 0 comment. |
| `git hash-object -- <path>` trên từng file trong manifest .04, SHA-256 của các dòng blob/path | Khớp 15/15, gói local `410f588ed9a22ab718c63a0a21964cafada579e30e881b0d0bb85abbf12fc184`. |
| `node docs/verify-sp-045-draft-fixture.cjs` | PASS trên SQLite bộ nhớ của host với export v1. Draft theo thứ tự 42/11/7, topic null và timestamp giữ đúng, sequence 50 sau xóa, ID mới 51. |
| Migration thật, fresh install và cache trước live | NOT_RUN. Thiếu Channel, slot nguồn–đích và DAO Room follower. Giữ schema v1. |
| Thiết bị, manual refresh, response ↔ Room, đóng/mở và đọc cache | NOT_RUN trên cả ba account vì các phụ thuộc trong bảng. Phiên .06 không dùng thiết bị. Số call provider trong bước reload **chưa quan sát**. Pixel_9 API 37 x86_64 ở .04 đã kiểm WorkManager qua hai tiến trình với dữ liệu MO_PHONG. |
| Fixture analytics/Facebook/LinkedIn và Gradle assembleDebug/testDebugUnitTest/lintDebug | NOT_RUN lại ở .06 vì chỉ sửa tài liệu. Kết quả .04 trên gói đã đối chiếu là 75/75, 55/55, 128/128, assemble đạt, unit 161/161, lint 0 lỗi/6 cảnh báo. |

## Nghị chạy lại khi nhận bàn giao

1. Minh giao SHA/PR của Channel production, bí danh của identity và Channel test, revision, trạng thái CONNECTED, reference do kho credential phân giải, product/scope/role, version và `READ_FOLLOWER_COUNT=AVAILABLE` kèm verifiedAt/evidence cho từng account đủ quyền. Đạt giao slot follower cùng base/schema SHA, version nguồn–đích và thứ tự. Chạy từng account ngay khi đủ điều kiện; follower chỉ cần root Channel.
2. Nối adapter .01, writer .03 và coordinator .04 vào `AppContainer` của app test qua DAO Room. Kiểm từ đúng version nguồn được giao và từng đường nâng cấp hỗ trợ gồm Draft v1. Trên API 23/36, so export nâng cấp với fresh install, kiểm ID/topic/null/timestamp/sequence bằng builder thật, rollback và revision guard. Mở lại DB và kiểm đọc cache theo Channel trước khi bật refresh thật.
3. Trên thiết bị test ghi model/API, build SHA, alias/revision. Đọc cache ban đầu. Chạy một manual refresh follower qua app cho scope đó (`manualRefresh(scope, version, true, emptyList)`). Đối chiếu identity Page trả về, subject của credential cho `q=me`, hoặc tổ chức và edge của request. Giữ response rút gọn đã che, giờ nhận và field số nguyên không âm. Giá trị 0 hợp lệ.
4. Đọc sample đã commit từ Room. Ghi alias Channel + revision, `collectionId`, `snapshotId`, `definitionId` thực tế, `value`, `observedAt`, `observedAtBasis`, `collectedAt`, `source=API_THAT`, `sourceRef` đã che và `providerApiVersion`. So field và definition theo account/version, CHANNEL / FOLLOWER_COUNT / GAUGE / COUNT / LATEST. Với mapping .01 dùng giờ nhận thực, basis COLLECTION và period null. Thiếu field thì ghi null/PROVIDER_OMITTED. Khi có lỗi, ghi reason/action theo bằng chứng, attempt/gap và lastGood trước đó.
5. Đóng tiến trình app test, mở lại và đọc Room **trước refresh tiếp theo**. Đối chiếu cùng hai ID, scope/revision, definition, giá trị, timestamp/basis và provenance với bước 4. Ghi bộ đếm provider trước/sau nếu quan sát được. Khi chưa có mẫu lưu, toàn bộ trường sample và hai mốc đều để trống.
6. Sau T1 đã đối soát, Nghị hẹn T2 ở lượt hằng ngày kế tiếp còn grant, ghi giờ thực và collection mới. 00:15 giờ cấu hình là slot đề xuất ở §9. T2 cần observedAt tăng, cùng scope/revision/definition và version tương thích. Mục tiêu mẫu hai 19/11 hiện chưa có T1; mốc 21/11 chỉ đối chiếu được scope đã đủ cặp. Nghị chuyển manifest sang .09/SP-046.04, Thiện nhận phạm vi hiển thị qua SP-055 khi có số liệu.

## Phần SP-045.07 và review toàn SP-045

SP-041.01 trên base `230c2a6` còn ở mức upsert mô phỏng và đề xuất Room. Nghị cần PublishedPost production có local ID bền, native post ID/URN, lookup theo đúng Channel/identity/revision; đối soát Page sở hữu post hoặc member/organization author của share/UGC. Kiểm ownership lại trong transaction trước ghi. [Bàn giao PublishedPost](../../Social_Pilot-SP041/docs/sp-041-published-post-verification.md) · [Mapping metric bài .02](sp-045-post-adapter-verification.md)

| Account | Grant Minh cần xác nhận cho `READ_POST_METRICS` theo post/query/version |
|---|---|
| Facebook PAGE | Quyền Page đọc reactions/LIKE, comments theo toplevel/stream, shares; đối chiếu `pages_read_engagement` và `read_insights` cho views/unique viewers. Shares vắng giữ null/PROVIDER_OMITTED. Impression chờ mapping riêng. |
| LinkedIn MEMBER | `r_member_postAnalytics` theo đúng actor và từng query IMPRESSION, REACTION, COMMENT, RESHARE hoặc MEMBERS_REACHED. Xác nhận TOTAL, lifetime hoặc dateRange có bounds/zone. |
| LinkedIn ORGANIZATION | Xác nhận product, `rw_organization_admin`, role và quyền trên đúng org/post cho organic impression/comment/share/like; giữ filter share/UGC, cửa sổ rolling và definition like riêng. |

Trước khi yêu cầu **Đạt + Thiện** review toàn SP-045, cần evidence migration/FK và bảo toàn Draft, ghi/đọc Room sau restart cho follower và metric bài, cặp mốc API_THAT tương thích ở .09, retry giữ ID và gap sau lỗi, disconnect/reconnect .08 chặn callback revision cũ và giữ cache kênh khác. Đạt đối chiếu migration/ownership; Thiện đối chiếu definition, lastGood/lastAttempt, provenance và gap với DTO. Người nhận lượt chạy tiếp là Nghị.
