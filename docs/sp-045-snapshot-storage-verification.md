# SP-045.03 · Tầng lưu snapshot

**Nghị · 19/11/2026 21:23 Asia/Saigon · Chờ phụ thuộc Room.** SP-045.04 có thể dùng writer với DAO mô phỏng. SP-046 có thể kiểm chữ ký đọc lịch sử và cache theo scope. Kết nối với database của app và hai mốc đo thật vẫn chờ bàn giao.

## Đối chiếu đầu vào

| Đầu vào | Kiểm tra trong phiên | Kết quả |
|---|---|---|
| Git | Worktree `Social_Pilot-SP045`, branch `feat/sp-045-follower-adapter`. `git rev-parse HEAD origin/main`, `git merge-base HEAD origin/main`, `git status --short --branch --untracked-files=all`, diff tracked/index và `git ls-remote` đọc các head branch/PR trên origin. | HEAD, merge-base, origin/main và remote main cùng `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa`. 17 file untracked có sẵn của .01/.02 khớp SHA-256 trước/sau. Bản triển khai mới chưa có commit. |
| SP-022 / Minh | [Issue #24](https://github.com/nigPeter2503/Social_Pilot/issues/24) mô tả công việc, 0 comment. Main có ChannelInfo và OAuth, data/local có AppDatabase/DraftDao/DraftEntity. Danh sách refs remote chưa có branch SP-022. [PR #82](https://github.com/nigPeter2503/Social_Pilot/pull/82) cũng ghi đang chờ registry kênh. | Có DTO cho identity và connectionRevision. Chưa xác minh được commit/PR giao Channel root, scoped DAO lookup, identity tuple và revision bền qua reconnect. |
| Slot / Đạt | [SP-012, PR #71](https://github.com/nigPeter2503/Social_Pilot/pull/71) có head `11abeb4940bd59c3eed4b9fa82827fd17ccfef73`, merge `5a5f82b732aeb97423925c6b2e2667c940777486`. Brief W2–3 trên main yêu cầu Đạt cấp từng lượt sau khi root tích hợp. PR #82 tại `178e05cc6c01082ba2cbb94461504f953a22080f` dùng v1→v2 cho Brief. | Chưa thấy Đạt xác nhận base/schema SHA, version nguồn–đích và thứ tự merge dành cho Snapshot/MetricSample hoặc slot riêng follower. Trên base, export v1 có Git blob `5172492e197da5086e9ac37f1c0bcc8a5db5e15b` và identityHash `99448fa4c3ad713a7903aaaf7bda7983`. |
| Root cấp bài | Worktree SP041 cùng HEAD `230c2a6`. [Handoff PublishedPost](../../Social_Pilot-SP041/docs/handoffs/sp-041-published-post-input.md) blob `6c33d013476d3ee160474dba7209d81fc303fe0d` và [evidence SP-041.01](../../Social_Pilot-SP041/docs/sp-041-published-post-verification.md) blob `cec475bad80573c3a276bf0be2216834958d674e` còn untracked. | Có lookup theo scope/local/native ID trên harness MO_PHONG. Chờ root Room đã tích hợp để gắn POST vào local PublishedPost PK. |
| Contract và adapter | [Analytics draft-4 §§5/8/9](analytics-data-contract.md) blob `92daad64351fcdbf3bfea0b593d1bc42fb260375`, [Room §§2–6](room-schema-contract.md) blob `4115755e7c533b8f270c38cf4982c9847500f176`. [SP-045.01](sp-045-follower-adapter-verification.md) blob `05022c7cc530d1c44cabb3783e5e7dbb32828184`, [SP-045.02](sp-045-post-adapter-verification.md) blob `a2f3c65f9a481d0247c26ccc04b650220f27bf10`. | Dùng lại request, kết quả, definition registry của adapter và provenance từng response. Đã đọc mục công việc, dòng tiến độ và hai handoff HO-CHANNEL/HO-SCHEMA trong master plan. |

## Hợp đồng cho collector và dashboard

- **SP-045.04** ghép callback với đúng request đã gửi bằng `SnapshotReading.from(request, result)`. Follower lấy externalAccountId từ request gốc. Phải dùng revision bền vững của Minh khi đổi danh tính. Gom các endpoint của một subject/collection rồi gọi `SnapshotWriter.write(attemptId, collectionStartedAt, completedAt, readings)` trên IO. Collection ID và thời điểm bắt đầu thuộc lần thu logic. Mỗi lần thực thi lại sau lỗi dùng attempt ID mới, còn giao lại cùng callback dùng lại attempt ID. Manual refresh dùng collection ID mới.
- Trong cùng transaction, writer kiểm identity, trạng thái kết nối, revision và resolve subject POST bằng native ID trong đúng scope. Khóa snapshot gồm channelId, subjectType, **local** subjectId và collectionId. Mẫu follower dùng chính channelId. Cùng collection retry giữ snapshotId, giữ cả giá trị 0, giờ và nguồn của số đã đo. Retry có thể bổ sung definition hoặc điền mẫu thiếu. Registry từ chối cùng definitionId có semantics khác.
- Khóa sample gồm snapshotId, definitionId và kỳ **được yêu cầu** nếu có. Tách hai biên request khỏi providerPeriodStart/End đã xác minh. Điều này giữ riêng hai kỳ có cùng definition và cho phép retry điền đúng kỳ còn thiếu. Khi cấp slot, dùng hai unique index có điều kiện (có kỳ/không kỳ) hoặc một khóa period tương đương, có ràng buộc đủ cả hai biên. Tránh unique tuple chứa NULL để lọt bản sao.
- Mẫu giữ source/sourceRef, ID định nghĩa có version, providerApiVersion, unit/aggregation/filter/window, observedAt/basis, collectedAt, period evidence và value hoặc missingReason. Attempt giữ trạng thái từng endpoint, lỗi, nextAction và retryAfterAt khi có. Một attempt chỉ có lỗi ghi chẩn đoán riêng. Một batch partial giữ số và lý do thiếu theo từng metric. Dữ liệu cùng collection qua nhiều endpoint vẫn có giờ nhận riêng.
- **SP-046** gọi `SnapshotDao.read(scope, source, from, through)` một lần cho mọi subject trong kênh. Kết quả gồm snapshot có mẫu trong khoảng đóng theo observedAt, attempt trong khoảng theo completedAt, cùng lastGood từng chuỗi và lastAttempt đến mốc through (kể cả cache trước from). Snapshot trả đủ thành phần để tra provenance. Khi tính theo kỳ, consumer lọc observedAt từng mẫu và giữ actual bounds. Số liệu hiển thị dùng clock của sample. Tích hợp tiếp theo map sang [AnalyticsSource.Batch của SP-046](../../Social_Pilot-SP046/android/app/src/main/java/com/socialpilot/app/domain/analytics/AnalyticsSource.java), blob `dc1da4525dea8c4f1c45da696b4c1318c475507f`. Cần Thiện xác nhận mapping cho metric rolling/provider-specific và definition tương thích trước khi tổng hợp.
- DAO hiện là interface Java. Bản triển khai tham chiếu có copy-on-write chỉ nằm ở test source set. Sau bàn giao, Room cần serialize với Channel invalidation trên cùng AppDatabase, kiểm ownership/FK, đăng ký migration trong builder, index scope/subject theo thời gian và definition cho đọc batch. Ngưỡng freshness, Worker, hook disconnect và phép tính analytics thuộc các bước kế tiếp.

## Kiểm chứng local

| Lệnh / phạm vi | Kết quả thực chạy |
|---|---|
| `node docs/contracts/verify-analytics-examples.cjs` | PASS 75/75 MO_PHONG |
| `node docs/api-verification/facebook/verify-fixtures.cjs` | PASS 55/55 MO_PHONG |
| `node ../Social_Pilot/docs/api-verification/linkedin/verify-fixtures.cjs` | PASS 128/128 MO_PHONG, API_THAT=0 |
| `node docs/verify-sp-045-draft-fixture.cjs` | PASS SQLite host trên DDL của export v1. Thứ tự 42/11/7, topic NULL của 42, topic/timestamp trùng của 7/11 và toàn bộ timestamp đúng. ID 50 từng bị xóa, ID kế tiếp 51. [Seed tái dùng cho migration](../android/app/src/androidTest/assets/sp045/drafts-v1-MO_PHONG.sql) |
| Trong `android/`, `.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | BUILD SUCCESSFUL, lượt cuối 32s. 147/147 JVM, gồm 11 snapshot, 29 follower, 47 post và 60 regression khác, 0 skip/failure/error. Lint 0 lỗi/6 cảnh báo baseline. [JUnit](../android/app/build/reports/tests/testDebugUnitTest/index.html) · [Snapshot XML](../android/app/build/test-results/testDebugUnitTest/TEST-com.socialpilot.app.data.remote.social.SnapshotStorageTest.xml) · [Lint](../android/app/build/reports/lint-results-debug.html) |
| Git | `git diff --check` và `git diff --cached --check`, kiểm whitespace cả file mới bằng no-index. So hash 17 file cũ, kiểm tracked/index và đối chiếu master plan ngoài đúng một dòng. |

JDK 21 ở `D:/CNTT/mobile/.reviews/pr70-tools/jdk-21.0.13+9`, Android SDK 36, wrapper Gradle 8.13, Node 24.19.0. Truy vấn GitHub ban đầu bị sandbox chặn DNS và Gradle bị chặn khóa wrapper cache. Đã chạy lại truy vấn chỉ đọc và build offline với quyền phù hợp.

11 test storage kiểm replay qua writer mới và replay đồng thời, giữ zero/provenance, cùng native post ở kênh A/B, root thiếu, callback revision cũ, disconnect và thay identity, rollback sau khi ghi definition/snapshot, failure giữ cache cũ qua khoảng thời gian trống, retry điền PERIOD còn thiếu, nguồn MO_PHONG bị chặn ở writer production, batch sai scope/thời điểm, callback REJECTED. Fixture org SP-019 đi qua parser thật rồi vào kho mô phỏng, giữ `likeCount=-1`, comment 0 và reaction thiếu lý do UNVERIFIED.

**NOT_RUN** · Room persistence, FK/index SQLite cho snapshot, upgrade mọi path và so fresh install, mở lại app qua builder, giữ Draft sau nâng cấp, instrumentation migration API 23/36. Cần Channel + slot trước khi có migration đích để chạy. Test JVM qua writer mới dùng chung fixture trong bộ nhớ. Thu live, Worker và đối chiếu hai mốc thật cũng chưa chạy.

## Định danh bản local

SHA-256 của danh sách 11 Git blob và đường dẫn dưới đây (UTF-8, hai khoảng trắng, LF cuối) là `b11f8f16e6b5f5fcfe20fc9bd8284240f1894ca0c7d103ac7ce1376435e4c175`. Các blob định danh source/test/fixture trong worktree chưa commit. AppDatabase, AppContainer, Draft DAO và schema export vẫn ở base v1.

```text
b1bd7dadfafbed88ace8cd1e4f1f32af250b7b64  android/app/src/androidTest/assets/sp045/drafts-v1-MO_PHONG.sql
37d107d1852b3e25f280d0d8abdc44ef9ad3417b  android/app/src/main/java/com/socialpilot/app/data/local/snapshot/CollectionAttempt.java
a8a2a7174224f27b9cfdb4aa0a4acdacc60fc480  android/app/src/main/java/com/socialpilot/app/data/local/snapshot/MetricDefinition.java
731f5550ee4842292c2a4acba6d704f2117a451c  android/app/src/main/java/com/socialpilot/app/data/local/snapshot/MetricSample.java
8bc7a29235b21f5b3bc9f7ea5fb5bc346f27b814  android/app/src/main/java/com/socialpilot/app/data/local/snapshot/Snapshot.java
9cfb7f0d5208d1ee1b1073b7bbbf7387fab61100  android/app/src/main/java/com/socialpilot/app/data/local/snapshot/SnapshotDao.java
6ff13ca1359a0cabc691b95d18efff7a0c577ed1  android/app/src/main/java/com/socialpilot/app/data/local/snapshot/SnapshotWriter.java
23b8309e25c3601b1b4bbdc413509f9846258929  android/app/src/main/java/com/socialpilot/app/data/remote/social/SnapshotReading.java
1907c3ac602032465e144571e573ee0bd67dfb46  android/app/src/test/java/com/socialpilot/app/data/local/snapshot/SimulatedSnapshotDao.java
c16bf49f22fbb7a2ea48856c195e03ad82ecffef  android/app/src/test/java/com/socialpilot/app/data/remote/social/SnapshotStorageTest.java
6b25a4e3b519e82dddc8616b6104e7ff08f524e1  docs/verify-sp-045-draft-fixture.cjs
```

## Bàn giao

1. **Đủ đưa vào Draft PR cho SP-045, issue #47** khi Nghị đóng gói cùng adapter .01/.02. Gói .03 gồm model, mapper, writer, DAO đọc/ghi, fixture và evidence. Có thể review khóa, provenance, retry và hợp đồng offline ngay.
2. **Trước review toàn SP-045 của Đạt + Thiện**, Minh giao Channel/grant theo operation và revision, Đạt pin base/schema SHA, slot follower hoặc slot chung cùng version nguồn–đích/thứ tự, SP-041.01 giao root bài. Nghị tích hợp Room, export/migration và chứng minh Draft qua nâng cấp API 23/36, hoàn tất collector/backoff, disconnect/recovery và hai mốc app thật cho phạm vi follower/post, đối soát gap. Thiện đối chiếu định nghĩa/provenance với consumer. Phiên này chỉ chuẩn bị local. #47 là số issue, số PR sẽ do GitHub cấp.
