# SP-045.09 · Cặp snapshot thật và đọc lại cache sau restart

**Chờ nghiệm thu live** · Nghị · #47 · 19/11/2026 22:28 Asia/Saigon. Cả ba loại tài khoản đều thiếu T1 cho follower và cho từng metric bài. **0 cặp đủ giao SP-046.04 và Thiện.** Phiên .09 dừng ở kiểm đầu vào, thực hiện 0 request đến Facebook/LinkedIn, chưa tạo collection, snapshot, sample hoặc attempt. Số call provider khi reload production **chưa đo** vì chưa có lượt reload. Thiết bị của .09: không dùng. Mốc giờ trên là giờ kiểm hồ sơ.

## Bàn giao tại thời điểm kiểm

| Đầu vào | Artifact đã đối chiếu | Kết quả |
|---|---|---|
| Git và hợp đồng | HEAD, upstream và head trên origin của `feat/sp-045-follower-adapter` = `82abd975436a7c5a2183ca34053b595067d4a86c`. Merge-base và origin/main = `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa`. Analytics blob `92daad64351f`. | Đọc mục và dòng tiến độ SP-045.09, HO-GRANT và điều kiện giao SP-046.04. Đối chiếu definition, thời điểm quan sát, kỳ và lịch theo [Analytics §§5–6/9](analytics-data-contract.md). |
| Minh · Channel và grant | #24/#26 mở, 0 issue comment. Contract #66 `d098195`, Facebook OAuth #74 `d1d55a0` và Graph #75 `853a1ac`, LinkedIn read spike #76 `306a1da` và OAuth/publish #77 `1dea02e`. | Kiểm refs và danh sách PR tới #86. Chưa thấy SHA giao SP-022/SP-024 cho lookup Channel production và revision bền, hay HO-GRANT gắn account/revision/operation trong app test. Các bằng chứng W1 chỉ dùng để xác định query cần kiểm. |
| Đạt · slot Room | #14 đã đóng. #71 `11abeb4` giao quy ước Room. #82 `178e05c` (Brief v2) và #83 `c6e3dd5` (editor v3) còn mở. #85 `96b2815` dùng fixture dashboard. | Chưa thấy owner/slot, base/schema SHA, version nguồn–đích và thứ tự migration cho Channel, PublishedPost và snapshot/sample/attempt. Đạt có thể xác nhận slot follower ngay sau Channel. |
| Nghị · SP-041.01 | #43 mở, 0 issue comment, chưa thấy PR giao root. Worktree SP041 HEAD `230c2a6`, evidence blob `cec475bad805`, handoff `6c33d013476d`. | Lookup hai chiều đã có ở harness mô phỏng. Entity, DAO và SQL vẫn là template. Chưa có row PublishedPost với local ID ↔ native ID/URN và ownership đã xác minh trên revision app. Chỉ các hàng POST cần phụ thuộc này. [Evidence .01](../../Social_Pilot-SP041/docs/sp-041-published-post-verification.md) · [Handoff](../../Social_Pilot-SP041/docs/handoffs/sp-041-published-post-input.md) |
| Nghị · đường thu/lưu | [Writer .03](sp-045-snapshot-storage-verification.md) `792091b6b1b2`, [Worker .04](sp-045-collection-worker-verification.md) `2fb820bec756`, [follower .06](sp-045-follower-live-verification.md) `f62bb8f57356`, [post .07](sp-045-post-live-verification.md) `cf7e99d2a946`, [recovery .08](sp-045-disconnect-verification.md) `db17c04fc352` (untracked). | .06/.07 đều ghi 0 request và chưa có response/row T1. Kiểm lại tại HEAD: có adapter và `MetricProvider.adapters(...)` cho cả follower/post. App mặc định truyền collector null, Worker trả `DEPENDENCIES_UNAVAILABLE`. `cached(...)` dùng port SnapshotDao. Room `socialpilot.db` v1 chỉ có Draft, export `5172492e197d`. Runner thiết bị inject coordinator và DAO mô phỏng. |

## Điều kiện thu T1 theo account và subject

Đọc từng hàng độc lập. Cột grant ghi phép đọc Minh cần xác nhận cho account test, cùng reference do app phân giải, version, role/product, verifiedAt và evidence đã che. Với bài, xác nhận theo từng query/field/kỳ bên dưới. “Chưa nối” ở cột provider là trạng thái đường chạy từ app mặc định.

| Account / subject | Channel và revision hiện hành | Operation / grant hiện hành | Mapping bài | Provider trong app | Room snapshot/sample |
|---|---|---|---|---|---|
| Facebook PAGE / CHANNEL | Thiếu Page test gắn Channel CONNECTED và revision | READ_FOLLOWER_COUNT chưa xác nhận trên app. FB-10 và PAGE-READ-02 thuộc W1 | Không yêu cầu | Chưa nối follower | Thiếu DAO |
| LinkedIn MEMBER / CHANNEL | Thiếu Channel, subject của me và revision | READ_FOLLOWER_COUNT, product và r_member_profileAnalytics chưa xác nhận | Không yêu cầu | Chưa nối follower | Thiếu DAO |
| LinkedIn ORGANIZATION / CHANNEL | Thiếu Channel, identity org, actor và revision | READ_FOLLOWER_COUNT cho networkSizes, role và scope trên org chưa xác nhận | Không yêu cầu | Chưa nối follower | Thiếu DAO |
| Facebook PAGE / POST | Thiếu Page test gắn Channel và revision | READ_POST_METRICS theo edge và query Insights, gồm pages_read_engagement/read_insights tương ứng, chưa xác nhận | Thiếu local ↔ native và Page chủ | Chưa nối post | Thiếu DAO |
| LinkedIn MEMBER / POST | Thiếu Channel, member tác giả và revision | READ_POST_METRICS, r_member_postAnalytics và quyền theo queryType/kỳ chưa xác nhận | Thiếu local ↔ share/UGC của member | Chưa nối post | Thiếu DAO |
| LinkedIn ORGANIZATION / POST | Thiếu Channel, org/actor và revision | READ_POST_METRICS, product/role, rw_organization_admin và quyền theo field organic chưa xác nhận | Thiếu local ↔ share/UGC của org | Chưa nối post | Thiếu DAO |

## T1/T2 và gap theo definition

Dấu “—” là chưa có mẫu thật tại mốc đó. T1 và T2 đều **NOT_RUN** do các đầu vào đúng hàng ở bảng trên. Các definition dưới đây mô tả mapping dự kiến của adapter, đơn vị COUNT. Chưa cấp definitionId/version, entityId hoặc thời gian thay cho row thực. Graph v27.0 là version đã dùng W1, LinkedIn 202610 thuộc fixture. Mỗi queryType MEMBER có hai definition riêng cho lifetime và dateRange.

| Account / subject | Definition hoặc query cần truy về response | Kind / aggregation / window | T1 | T2 |
|---|---|---|---|---|
| FB PAGE / CHANNEL | FOLLOWER_COUNT · followers_count của đúng Page | GAUGE / LATEST | — | — |
| LI MEMBER / CHANNEL | FOLLOWER_COUNT · memberFollowersCount của me | GAUGE / LATEST | — | — |
| LI ORGANIZATION / CHANNEL | FOLLOWER_COUNT · firstDegreeSize, edge COMPANY_FOLLOWED_BY_MEMBER | GAUGE / LATEST | — | — |
| FB PAGE / POST | REACTION_COUNT · reactions, toàn edge | CUMULATIVE / TOTAL | — | — |
| FB PAGE / POST | LIKE_COUNT · reactions, type=LIKE | CUMULATIVE / TOTAL | — | — |
| FB PAGE / POST | COMMENT_COUNT · filter=toplevel | CUMULATIVE / TOTAL | — | — |
| FB PAGE / POST | COMMENT_COUNT · filter=stream | CUMULATIVE / TOTAL | — | — |
| FB PAGE / POST | SHARE_COUNT · shares.count | CUMULATIVE / TOTAL | — | — |
| FB PAGE / POST | MEDIA_VIEW_COUNT · post_media_view | CUMULATIVE / TOTAL / lifetime | — | — |
| FB PAGE / POST | UNIQUE_MEDIA_VIEWER_COUNT · post_total_media_view_unique | CUMULATIVE / DISTINCT_TOTAL / lifetime | — | — |
| FB PAGE / POST | IMPRESSION_COUNT | Mapping/semantics UNVERIFIED | — | — |
| LI MEMBER / POST | IMPRESSION · elements[].count | CUMULATIVE / TOTAL / lifetime | — | — |
| LI MEMBER / POST | IMPRESSION · elements[].count | PERIOD / TOTAL / dateRange [start,end), zone xác minh | — | — |
| LI MEMBER / POST | REACTION · elements[].count | CUMULATIVE / TOTAL / lifetime | — | — |
| LI MEMBER / POST | REACTION · elements[].count | PERIOD / TOTAL / dateRange [start,end), zone xác minh | — | — |
| LI MEMBER / POST | COMMENT · elements[].count | CUMULATIVE / TOTAL / lifetime | — | — |
| LI MEMBER / POST | COMMENT · elements[].count | PERIOD / TOTAL / dateRange [start,end), zone xác minh | — | — |
| LI MEMBER / POST | RESHARE · elements[].count | CUMULATIVE / TOTAL / lifetime | — | — |
| LI MEMBER / POST | RESHARE · elements[].count | PERIOD / TOTAL / dateRange [start,end), zone xác minh | — | — |
| LI MEMBER / POST | MEMBERS_REACHED · elements[].count | CUMULATIVE / DISTINCT_TOTAL / lifetime | — | — |
| LI MEMBER / POST | MEMBERS_REACHED · elements[].count | PERIOD / DISTINCT_TOTAL / dateRange [start,end), zone xác minh | — | — |
| LI ORGANIZATION / POST | IMPRESSION_COUNT · impressionCount | Rolling 12 tháng organic / TOTAL | — | — |
| LI ORGANIZATION / POST | COMMENT_COUNT · commentCount | Rolling 12 tháng organic / TOTAL | — | — |
| LI ORGANIZATION / POST | SHARE_COUNT · shareCount | Rolling 12 tháng organic / TOTAL | — | — |
| LI ORGANIZATION / POST | PROVIDER_LIKE_COUNT · likeCount, giữ giá trị có dấu | Rolling 12 tháng organic / TOTAL | — | — |
| LI ORGANIZATION / POST | REACTION_COUNT · all-reaction | Field UNVERIFIED | — | — |

Khoảng trống ở đây là kết quả kiểm bàn giao. Không có attempt nên chưa có missingReason từ provider, lastAttempt hoặc lastGood để báo. Với FB shares, chỉ gán PROVIDER_OMITTED nếu response thực vắng field. Với impression FB và all-reaction của org, còn thiếu mapping kể cả khi có grant. Khi nhận partial, giữ nguyên số 0, giá trị hợp lệ và missingReason riêng của từng sample. Lỗi lần sau giữ lastGood cùng provenance gốc và ghi gap cho lần thiếu.

**Đối soát khi có mẫu.** Cho từng mốc, Nghị giữ response đã che và sourceRef, ghi collectionId, snapshotId, collectionStartedAt, account/Channel/revision, entityType/entityId (kèm mapping bài), definitionId, providerApiVersion, unit, kind, aggregation, filter/window và bounds/zone. So field/value hoặc missingReason, observedAt/basis, collectedAt của sample và thời điểm hoàn tất snapshot với row Room, source=API_THAT. Force-stop rồi mở lại app, đọc đúng history trước refresh kế và so lại cùng ID, giá trị, thời gian, provenance, lastGood/lastAttempt và gap. Ghi chênh bộ đếm call quanh riêng lần đọc cache nếu đo được.

Chỉ tính delta khi cả hai giá trị có mặt, collection khác nhau, observedAt(T2) > observedAt(T1) và cùng scope/revision, entity, definition, unit, kind, source, filter/window tương thích. Đổi version cần bằng chứng mapping tương đương. PERIOD giữ nguyên kỳ provider, rolling organic giữ phạm vi 12 tháng và bounds theo response. Không lấy hiệu các kỳ khác nhau làm số tăng trong tuần. Hiện không có delta, actualStart/end hay sourceSnapshotIds để chuyển sang SP-046.04.

## Recovery theo môi trường

| Môi trường / nguồn | Quan sát thực | Phạm vi đã xác minh |
|---|---|---|
| JVM · .03/.04/.08 · DAO trong RAM, MockWebServer | .04: 429, Retry-After 95 giây. Dựng lại coordinator trên cùng DAO, trước hạn vẫn 1 HTTP, tới hạn có HTTP thứ 2, attempt mới, cùng collection/start và đúng 1 snapshot. Offline sau lượt tốt giữ snapshotId, số 0, observedAt/collectedAt và provenance MO_PHONG, thêm gap OFFLINE. Retry partial chỉ thu phần thiếu. .08 giữ History B và chặn replay/callback A7 sau đổi revision. | Kiểm retry, lastGood và ranh giới ghi trên fixture. .08 đã chạy 163/163 JVM, fixture analytics/Facebook/LinkedIn 75/75, 55/55, 128/128, assemble đạt, lint 0 lỗi/6 cảnh báo. |
| WorkManager trên Pixel_9 API 37 x86_64, emulator-5580 · .08, cùng revision | PID 8671 → 8822 qua force-stop. Giữ work `a488ad2c-c1ef-48a6-89c9-40ccc08fdcab`, collection `f53d7bcf-8efe-435c-b859-ee7ac07fb782`, start `1795101732745`. Calls 0 offline, 1 sau resume. Snapshot `619e5ab0-7d89-470f-891a-f908ea133754` tạo trong DAO MO_PHONG của PID mới. | 2/2 pha. WorkManager phục hồi input/job từ DB của nó. Chưa có snapshot từ PID đầu để kiểm đọc lại. |
| Cùng AVD · .08, A7 cũ gặp A9 | PID 8042 → 8183, work `d3b0bb25-5ce1-489b-9db9-e2346eb7fd23`, collection `d2acdfa3-7ab7-4f39-b827-ba7466c9a771`. A7 trả FAILED với CHANNEL_DISCONNECTED/RECONNECT trước HTTP, calls=0. A9 có 0 snapshot/attempt trước refresh mới. Job A9 mới COMPLETE, 1 call. | 2/2 pha. DAO MO_PHONG được dựng lại. TestDriver cùng phiên kiểm cancel A7 trong khi B7 giữ cache và A9 chạy được. Các bộ đếm thuộc provider fixture. |
| Room production / API_THAT · .09 | **NOT_RUN** cho retry offline/429 cùng collection qua restart, đọc lại ID/value/time/provenance trước refresh và đối soát response → row → cache. Số call reload chưa đo. Migration/device API 23/36 cũng NOT_RUN. | DB chỉ có Draft v1, thiếu DAO, binding và slot migration. Cần Nghị tích hợp, chạy trên thiết bị với DB bền và đối chiếu trước/sau PID. Chỉ thu qua app test khi đúng grant. |

## Lệnh kiểm và bảo toàn

- Trước sửa: `git status --porcelain=v2 --branch --untracked-files=all`, `git diff --no-ext-diff --unified=1`, `git diff --cached --stat`, `git ls-files --others --exclude-standard`, `git rev-parse '@{upstream}' origin/main`, `git merge-base HEAD origin/main` và `git worktree list --porcelain`. Index trống. Ba test .08 có diff chưa stage (178 dòng thêm, 5 dòng xóa), tài liệu .08 là file untracked duy nhất.
- Bàn giao: `git -c credential.helper= -c core.askPass= ls-remote origin 'refs/heads/*' 'refs/pull/*/head'`; GitHub REST GET `pulls?state=all&per_page=100&sort=updated&direction=desc` và `issues/{14,24,26,43,47,82,83}`. Hai lượt truy vấn đầu lỗi DNS, chạy lại chỉ đọc thành công. #24/#26/#43/#47 còn mở, cả bốn có 0 issue comment.
- Đối chiếu source và artifact: `rg` trên `android/app/src`, `git hash-object` cho export v1, contract và các evidence. Kiểm blob và SHA-256 trực tiếp trên byte của bốn file .08 trước/sau. Kiểm `git diff --check`, index, full status và `git diff --no-index` cả với file evidence mới. Master plan nằm ngoài worktree, đối chiếu SHA-256 khi phục hồi riêng dòng SP-045.09 trong bộ nhớ.
- Fixture, Gradle và instrumentation **NOT_RUN lại ở .09** vì phiên này chỉ cập nhật hồ sơ. Lệnh build/test, AVD và kết quả đã chạy ở .08 xem [nhật ký thiết bị và test](sp-045-disconnect-verification.md).

| File .08 có sẵn | SHA-256 trước và sau |
|---|---|
| MetricWorkDeviceTest.java | `987ed78e2f438b3e3b729a196d4f63647778c67205c11bbd1010e11c9c11cf86` |
| MetricCollectionTest.java | `ac2de21e18d5482a06cebf65c3cf4d3f1ed2d0975db3148f597d0e35e39ac9a3` |
| SnapshotStorageTest.java | `c4cc5ed5f2595ec9f47599c558101104104102b4f3c7cb96d7fcb77003010f1a` |
| sp-045-disconnect-verification.md | `12ba3b29d71d21db5a04a0aa6315c156cc526c3edcdeb44e5694940d1a2d6e9a` |

## Mốc thu tiếp theo và người nhận

1. **SP-046.04 và Thiện** · Follower lẫn post của FB PAGE, LI MEMBER và LI ORGANIZATION đều còn gap. Mục tiêu T2 ngày 19/11 chưa đạt. Mốc 21/11 là điểm rà soát, chưa có lịch thu T2 được xác nhận. T1 là lượt app đầu tiên sau khi từng hàng đủ điều kiện. Nghị ghi giờ thật của T1 và hẹn T2 vào lượt hằng ngày kế tiếp theo lịch/zone đã xác nhận cho account. 00:15 ở §9 mới là đề xuất. Giữ coverage trống cho từng hàng tới khi có cặp hợp lệ.
2. **Minh, Đạt, Nghị** · Minh giao Channel/revision, grant và reference theo operation, ownership bài và lịch thu. Đạt pin slot, base/schema SHA, version, thứ tự và review migration/provenance. Nghị hoàn thiện root bài SP-041.01 cho POST, nối provider và Room cho từng scope sẵn sàng, kiểm retry cùng collection và cache trước refresh sau restart, rồi thu và đối chiếu T1/T2. Bằng chứng hiện có gồm JVM fixture và hàng đợi WorkManager qua hai PID. Recovery của cache metric bằng Room production còn chờ.
