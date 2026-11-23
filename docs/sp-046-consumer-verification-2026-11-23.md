# SP-046 — Consumer PARTIAL và gate dashboard thật

Tô Lãng Nghị · 23/11/2026, Asia/Saigon · giờ công thực tế: —.
**Gói consumer MO_PHONG đã kiểm; tích hợp Room và reconciliation thật còn mở.**

## Revision và phạm vi

- Baseline local/remote PR #89: `0573ca9126a9bb22ad333996d75afc4ffdfa2bb1`; nhánh local `feat/sp-046-instagram`, remote `feat/sp-046-analytics-repository`. PR OPEN, CI baseline 4/4 đạt khi đọc lại.
- Mã/test mới: **`aa5d592f27436b833edc5b3b562083cab1b64bd2`**. Thêm bốn ca consumer; không sửa lại fix PARTIAL hoặc production code. Các commit tài liệu sau giữ nguyên cây Java/test đã kiểm.
- Remote main/fetch: `a351435b2b9f93ae245bd15c8400e753147c2043`; merge-base và PR base snapshot: `ac38888c7038eaa8135933acac72d565ab29f2e5`. Không gọi merge-base là main hiện hành. Chưa merge main trong lượt này: hai commit mới trên main là evidence SP-066 và demo SP-017, chưa cung cấp DAO production cần nối. Đọc artifact bằng ref pin; không cần đưa demo vào thay đổi consumer độc lập.
- AppDatabase **v1**, blob `9d7591dbaa6cf024e6748417e7fe9427c641f8be` giống main. Java DTO/query **0.1.0**, analytics **draft-4**, source semantics theo fix `0573ca9`; không đổi enum/schema/migration/index.
- 30 file evidence untracked có trước được chụp SHA-256, giữ ngoài commit. Không reset/clean/stash. Hồ sơ mới ở `D:/CNTT/mobile/.reviews/sp046-consumer-20261123/`; baseline cũ `.reviews/sp046-partial-review-fix/` chỉ được đọc.

## Kiểm chứng consumer

Thêm [AnalyticsDashboardConsumerTest](../android/app/src/test/java/com/socialpilot/app/data/repository/analytics/AnalyticsDashboardConsumerTest.java): chạy **DefaultAnalyticsRepository thật** với input thô và source RAM, rồi observer/callback chuyển qua hàng đợi UI điều khiển thủ công. Không dựng sẵn projection mong đợi; expected số học được tính độc lập từ input. Các ca cũ trong repository/projector/contract suite được tái sử dụng, không nhân đôi thành test mới.

| Ca và test chứng minh | Dữ liệu cũ → mới | Status/errors | Freshness | Coverage/provenance/revision |
|---|---|---|---|---|
| `partialRendersNewFollowerAndOldEngagementBeforeTerminalOnUi` (mới) | follower 10 → 15; tuần `(12−0)+(3−0)+(5−0)=20` → 20; batch refresh không có post/history | CONTENT/ERROR, PARTIAL; NETWORK_ERROR / READ_POST_METRICS / RETRY_WHEN_SAFE; data render trước terminal | follower collectedAt mới, fresh; tuần giữ START+7, stale; lastAttemptAt mới | FULL temporal/cohort 1/1; actual START/END và toàn bộ provenance/snapshot IDs cũ bằng nhau; collection mới chỉ ở follower; exact FB scope revision 1 |
| `reattachDuringPartialKeepsOneRefreshAndDropsDetachedUiWork` (mới) | 15/20 đọc lại sau attach; frame của view đã tháo không được vẽ | PARTIAL giữ ở owner refresh; source.refreshes=1 | Như trên | Cùng bounds/coverage/provenance; attach không khởi động request mới |
| `cancelAndQueryOrRevisionChangeRejectPartialAlreadyQueuedToUi` (mới) | cancel outcome không rollback số 15 đã commit; đổi grouping/query rồi reconnect revision 2 | Outcome queued cũ bị loại; revision 2 EMPTY từ source đã invalidated | Không tái sử dụng freshness revision 1 | Không render data/outcome cũ sau đổi generation/query; không đưa history revision 1 vào revision 2 |
| `newRepositoryReadsCommittedPartialHistoryWithoutItsFormerMemoryCache` (mới) | close repository, tạo instance mới → 15/20 từ cùng source RAM | ERROR + lỗi attempt mới; không callback/refresh giả | follower fresh, tuần stale như trước | Full provenance và coverage giữ nguyên; **không phải process death/Room** |
| `staleReadbackCannotPublishAnAttemptBatchAsComplete` (repository regression có sẵn) | cache 10/20; attempt 15 thiếu history nhưng readback thuộc attempt cũ → giữ 10/20 | FAILED/CACHE_READ_FAILED | Không lấy attempt mới làm timestamp mẫu | Không công bố batch attempt như projection đầy đủ; mismatch được chặn |
| `firstRefreshAfterRepositoryCreationReadsPersistedGoodHistory` và `failedPostDoesNotDiscardAnotherPostsSuccessfulSamples` (có sẵn) | repository chưa có cache vẫn 15/20; bài thành công 25 + bài lỗi giữ 30 = 55 | PARTIAL | Theo từng input được chọn | Đọc history source; 2 bài được giữ; nguồn RAM |
| `instagramZeroAndReachStayScopedAndDoNotSupplyImpressions` (projector có sẵn) | IG follower 0 và reach lifetime 0; FB impression 400 không thay mẫu IG thiếu | 0 có reason null; ER null có reason; không tự xếp hạng reach | Theo sample IG | Đúng platform/channel/revision; reach không thành impression hoặc tổng unique reach |
| `partialSubtotalFreshnessUsesOnlyIncludedInput`, `fullCohortAndPartialTemporalCoverageAreIndependent` (có sẵn) | subtotal 20 loại bài thiếu baseline; cặp bên trong kỳ giữ bounds thực | PARTIAL_COVERAGE và excludedReasons đúng bài | Chỉ input được cộng | Temporal/cohort độc lập; provenance không chứa bài bị loại |
| Consumer contract + calculator regression hiện có | 0 khác null; null có reason/missingDetails; zero denominator không thành 0% | PERMISSION_REQUIRED/RESOLVE_PERMISSION hoặc MISSING_COMPONENT/ZERO_DENOMINATOR theo ca | Cache offline giữ thời điểm cũ | API_THAT không nhận MO_PHONG; definition/window/source không tương thích bị loại |

PARTIAL chỉ mô tả kết quả refresh, không tự làm coverage của lịch sử đã giữ thành PARTIAL. Coverage vẫn phản ánh mẫu thực dùng. `lastSuccessAt` toàn dashboard có thể đổi do follower thành công; consumer phải dùng freshness/provenance của từng giá trị. Source chịu trách nhiệm **readCached đầy đủ**: attempt equality không phát hiện được source vi phạm hợp đồng bằng một readback thiếu hàng nhưng mang cùng attempt. Gate DAO phải kiểm tập ID/sample, transaction, rollback và restart thật.

## Kiểm chứng Android và fixture

Đường dẫn đã xác minh: JAVA_HOME `D:/CNTT/mobile/.reviews/pr70-tools/jdk-21.0.13+9`; ANDROID_HOME `C:/Users/ASUS/AppData/Local/Android/Sdk`. Windows, Temurin 21.0.13+9; Gradle wrapper 8.13, compile/target SDK 36, minSdk 23. Không đổi dependency.

```powershell
# android/
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# checkout root
node docs/contracts/verify-analytics-examples.cjs
git diff --check
```

Kết quả mới: **159/159 JVM**, **17 suite**, **0 failures / 0 errors / 0 skips**, bao gồm 4/4 consumer mới; assembleDebug đạt; lint **0 lỗi / 14 cảnh báo**; analytics **75/75**, diff check đạt. Task unit đã thực thi (không UP-TO-DATE). Lần thử sandbox đầu bị chặn file khóa cache Gradle, chạy lại với quyền cache và JDK đã pin thì đạt; không đổi script lịch sử để ép PASS.

`results.json`, `android.log`, `analytics-fixtures.log`, bản sao `TEST-*.xml`, `lint-results-debug.xml`, `java-version.txt`, `node-version.txt` nằm trong thư mục evidence local nêu trên. `baseline.json` ghi status/staged/untracked và SHA-256; `pr-89-checks.json` ghi checks theo đúng head lúc đọc. CI cuối sau push được ghi vào PR body và hai plan local để tránh vòng lặp commit chỉ nhằm chép SHA của chính nó.

## Kiểm tra phụ thuộc production

Đọc lại GitHub issues #14/#24/#26/#48/#49, PR #87/#88/#89, reviews/comments #89 và fetch remote ngày 23/11. Issues trên chưa có comments cấp artifact mới; PR #89 có ACK consumer và phản hồi fix, không có cấp DAO/slot. Đây là phạm vi bằng chứng đã kiểm, không suy về trao đổi ngoài repo.

| Owner | Artifact hiện có / revision đã kiểm | Artifact thiếu, ảnh hưởng và bước mở chặn |
|---|---|---|
| Minh/SP-022 | `3e18f9832bb4385d02a388fec54daa34649700a3`, `docs/evidence/sp-022-fixture-verification.md`: lifecycle/scoped grant MO_PHONG; main chỉ Draft DAO | Channel production lookup/identity/ownership, revision bền vững và grant đúng operation/media/version. Cần commit/schema + evidence lookup để nối source và thu follower. |
| Đạt, phối hợp Thiện | Room contract; SP-035 `178e05c` v2; SP-036 `c6e3dd5` v3; ledger `adfd87c` ghi các version thuộc Thiện | Pin base/schema SHA, slot, nguồn–đích và thứ tự Channel → PublishedPost → analytics. Chưa được cấp slot; không tự lấy v2/v3 hoặc PrototypeDatabase v4. |
| Nghị/SP-045 | PR #88 `b6a59ee7767e6cf6d86bb09d0c7b3327bfca40b7`, handoff + production-readiness + live-manifest NOT_RUN | Snapshot/MetricSample/MetricDefinition/CollectionAttempt DAO và collector binding thật; consistent read và sample+attempt/errors transaction. Sau tích hợp kiểm rollback, idempotency, revision, restart/readback/query plan. |
| Nghị/SP-041 | PR #87 `758c69b00a1b8e93d21db1c92397aee2e62f2ef5`, handoff PublishedPost + readiness | PublishedPost **local ID** đúng Channel trong transaction cho metric POST; hiện chưa có root production. Follower không chờ root bài nhưng vẫn chờ Channel/slot/grant. |
| Minh/SP-024 | Issue #26 và social/Room contract | Policy/hook invalidation → cancel → cleanup/recovery cùng revision; cần trước tích hợp disconnect production. |
| Nghị/SP-045 → SP-046 | API_THAT SP-066 `eda55ac` là mapping; manifest app vẫn không có T1/T2 | Hai collection production tương thích, IDs/time/bounds/source/readback. Không dùng hai mốc Explorer hoặc replay làm snapshot app. Nghị tính expected độc lập, ghi included/excluded và actual khi đủ cặp. |
| Thiện/SP-047, Đạt review | ACK Thiện tại `2012752`/`c2da648`; Đạt CHANGES_REQUESTED trên `1f96962` | Đối chiếu fix/bản consumer `aa5d592`, re-review finding và ACK revision mới; ACK production chỉ sau DAO/reconciliation. |

## Reconciliation và nội dung bàn giao chưa gửi

[Manifest](sp-046-reconciliation-manifest.json) để **NOT_RUN** cho Room transaction/rollback/replay, restart/readback, query plan, migration/fresh install API 23/36 và reconciliation live. Không đổi schema nên không có migration mới để kiểm. SP-066 API_THAT chỉ là đầu vào mapping; mọi ca consumer ở đây MO_PHONG. Không có request Meta trong lượt này.

Thiện có thể đối chiếu observer/refresh/generation và render fixture ngay. Nội dung chuẩn bị, **chưa gửi**:

> Thiện đối chiếu giúp gói consumer mã `aa5d592` trên PR #89, source semantics fix `0573ca9`: refresh PARTIAL hiện follower 15 và giữ tuần 20 cùng timestamp/provenance/coverage cũ; lỗi/lastAttemptAt mới và stale từng số vẫn rõ. Bốn test mới chạy repository → UI queue, reattach/cancel/query/revision và tạo repository mới từ source mô phỏng. Java DTO 0.1.0 giữ nguyên. ACK `2012752`/`c2da648` của Thiện đã được ghi đúng phạm vi; nhờ xác nhận bản PARTIAL mới theo SHA/contract. Binding dashboard thật chờ Channel/DAO/slot và manifest T1/T2 production.

SP-046 tiếp tục **chờ production và review**, chưa hoàn tất; không triển khai SP-047/SP-042 thay owner, không merge PR/đóng issue.
