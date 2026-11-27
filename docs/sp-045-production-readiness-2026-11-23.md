# SP-045 · Đối chiếu SP-066 và gate snapshot production

Owner Tô Lãng Nghị · 23/11/2026, Asia/Saigon · giờ công thực tế: —.
**Đã kiểm phần độc lập; chưa có snapshot T1/T2 production.**

## Gate từ SP-041 và revision

SP-041 thực hiện trước, PR #87 tại `758c69b` (mã `f57cf55`, 244/244 JVM). PublishedPost local ID production **chưa được cấp** vì chưa có Channel DAO/grant và slot Room. Vì vậy SP-045 chỉ tiếp tục fixture/adapter/collector; không ghi POST production. Follower production cũng chờ Channel, grant riêng và slot.

- Baseline local/remote PR #88 `bc35f6dbd657eb61413078bb610b48b6d396cc80`, không tracked diff hay untracked. Baseline chạy mới **167/167**, 13 suite.
- Main đã xác minh/fetch `a351435b2b9f93ae245bd15c8400e753147c2043`, gồm SP-066 squash `eda55ac`. Merge `de2974d` giải quyết conflict duy nhất trong AppContainer bằng cách giữ cả collector injection của SP-045 và database/stream/recovery demo SP-017.
- Mã kiểm chứng `88f76494f722ebd00128b745e1b43c88fd110636`; commit tài liệu sau không đổi mã.
- `socialpilot.db` / AppDatabase **v1**, schema Draft hash không đổi. `socialpilot-prototype.db` v4 là demo riêng; không dùng cho snapshot production. Default collector trong AppContainer vẫn null, Worker trả DEPENDENCIES_UNAVAILABLE khi chưa có binding production.

## Artifact đầu vào và blocker

| Owner | Artifact đọc thực tế | Đầu ra còn cần |
|---|---|---|
| Minh / SP-022 | `3e18f98:docs/evidence/sp-022-fixture-verification.md`, issue #24; chưa có Channel entity/DAO ở nhánh đã fetch | Channel root cùng AppDatabase, identity IG/Page, revision bền vững, scoped lookup/credential reference, READ_FOLLOWER_COUNT và READ_POST_METRICS grant độc lập gắn account/media/API version. Fixture grant không mở production. |
| Minh / SP-024 | Issue #26, Room contract §4/§7 | Policy/hook invalidation/cancel/cleanup/recovery có revision; transaction cùng root, không dọn dữ liệu reconnect mới hoặc Facebook. |
| Đạt, phối hợp Thiện | Room contract; SP-035 `178e05c` AppDatabase v2, SP-036 `c6e3dd5` v3; SP-033 `adfd87c:docs/ai-budget-ledger-contract.md` xác nhận v2/v3 thuộc PR Thiện | Base/schema SHA, source/target version, owner giữ lượt và thứ tự Channel → PublishedPost → Comment/checkpoint → MetricDefinition/Snapshot/MetricSample/CollectionAttempt. Chưa có slot cấp cho SP-045 trong artifact đã kiểm. |
| Nghị / SP-041 | PR #87 `758c69b`, handoff PublishedPost | Local post PK được resolve đúng scope trong cùng transaction ghi snapshot. Không dùng media ID làm local FK, không seed root từ Explorer. |
| Thiện / SP-046/047, Đạt | Handoff analytics draft-4 + phụ lục SP-066 trên main | ACK semantics/query tại revision bàn giao. Không tự đổi enum/schema hoặc suy ER từ reach. |

Đã đọc issue #14/#24/#26/#43/#47 và PR #87/#88; không có issue comments tại lượt đọc. Không tìm thấy xác nhận slot/grant mới trong phạm vi artifact này. Không suy từ trạng thái closed của SP-012 rằng migration SP-045 đã được cấp.

## Expected → actual trên adapter và store mô phỏng

| Ca | Expected → actual | Nguồn |
|---|---|---|
| IG-06/13 follower | Đúng IG user `17841459976957663`, followers_count **1**, GAUGE/COUNT/LATEST; bind Page `1427548503779474` rồi trả payload IG → SCOPE_MISMATCH, không có sample. Đạt. | Response đã che + HTTP/Channel/grant MO_PHONG |
| IG-07/13 reach | Media `18111630677338097`, reach **0**, period lifetime, CUMULATIVE/COUNT/DISTINCT_TOTAL/LIFETIME; null period bounds không biến lifetime thành kỳ tuần. Đạt. | Response đã che + MO_PHONG |
| IG-11 impression | Capability metric UNSUPPORTED trên đúng media/query/version → null/UNSUPPORTED, không đọc credential/HTTP. Reach vẫn 0/AVAILABLE. Khi không có binding riêng, giữ UNVERIFIED; không blanket-map code 100. Đạt. | Evidence IG-11; binding fixture tường minh, chưa cấp grant production |
| IG-12 thiếu quyền | Evidence của query impression không phân loại lỗi reach; evidence code 10 đúng scope reach → PERMISSION_REQUIRED/RESOLVE_PERMISSION. HTTP 200 trong test là synthetic vì evidence không ghi status. Đạt. | Response đã che + MO_PHONG |
| Hai collection và replay | Hai lần thu bằng fake clock, mỗi lần có CHANNEL follower và POST local ID mô phỏng: 4 snapshot ID khác nhau; collection mới có ID mới. Dựng lại coordinator rồi replay cùng collection không gửi HTTP, không đổi snapshot/sample/time/provenance. Đạt. | MO_PHONG, không phải T1/T2 production |
| Cache sau refresh lỗi | Offline refresh giữ toàn bộ history/last-good, ghi lastAttempt mới, không thêm HTTP. Regression rollback, partial, 429, revision cũ, reconnect và scope isolation trong suite hiện hành đạt. | RAM DAO MO_PHONG |

Test đọc trực tiếp JSON tại `docs/api-verification/instagram/evidence/responses/`; helper `Sp066Evidence` chỉ ở test source set. Sample.source luôn MO_PHONG; observedAtBasis COLLECTION và clock fixture, **không nhập giờ Explorer IG-13 vào snapshot app**. Không gọi Meta, không POST reply.

## Kiểm chứng

Windows, Temurin 21.0.13+9 tại `.reviews/pr70-tools/jdk-21.0.13+9`; Android SDK `C:/Users/ASUS/AppData/Local/Android/Sdk`, compile/target 36, minSdk 23; dependency không đổi.

```powershell
# tại android/
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# tại root checkout
node docs/api-verification/instagram/verify-fixtures.cjs
node docs/api-verification/instagram/verify-api-fixtures.cjs
node docs/api-verification/facebook/verify-fixtures.cjs
node docs/contracts/verify-analytics-examples.cjs
node docs/verify-sp-045-draft-fixture.cjs
git diff --check
```

**173/173 test**, 14 suite, 0 failure/error/skip; assemble đạt; lint **0 lỗi, 6 cảnh báo**. Tăng từ baseline chạy mới 167 bằng 2 test nhận từ main và 4 test SP-066. Hai validator Instagram đạt, Facebook **55/55**, analytics **75/75**, validator input Draft v1 đạt; validator Draft chỉ kiểm fixture SQL, không là migration Room đã chạy. Diff check đạt.

Log local, baseline và tổng hợp XML ở `D:/CNTT/mobile/.reviews/sp041-sp045-20261123/`; [kết quả máy đọc](sp-045-current-verification.json). Instrumentation Worker sau merge được ghi trong kết quả cùng thư mục; nó dùng store MO_PHONG, không thay thế kiểm Room production.

**Hồi quy thiết bị sau merge:** 4/4 ca riêng trong `MetricWorkDeviceTest` đạt trên Pixel_9/API **37** x86_64, emulator chạy read-only, không lưu snapshot. `recreatedWorkersReuseInputAndOnlyRetryWhenDue`, `driverQueuesTwoManualCollectionsAndRunsWorkerOnlyWhenConnected`, `disconnectCancelsRunningAndBlockedRevisionWhileOtherWorkSurvives`, `restoresPostTargetsAndVerifiedPeriodFromWorkData` kiểm giữ input/collection, chờ hạn, queue, cancel revision cũ, giữ B7 và A9, khôi phục period. Build test bằng `-PdeviceTestRunner=com.socialpilot.app.core.MetricDeviceRunner :app:assembleDebugAndroidTest`, chạy từng ca qua `adb shell am instrument -w -e class ...#method com.socialpilot.app.test/com.socialpilot.app.core.MetricDeviceRunner`. Một lượt ca queue bị OS kill trước TestRunner; `dumpsys activity exit-info` ghi **LOW_MEMORY**, không có Java crash. Sau khi boot ổn định, ca queue chạy lại đạt hai lần, ba ca khác đạt. Không sửa code để né lỗi. Đây là store RAM/test WorkManager; ca process-death hai phase chưa chạy và **API 23/36 migration production vẫn NOT_RUN**.

## Bàn giao SP-046/Thiện

[Handoff analytics](handoffs/sp-045-analytics-input.md) pin follower gauge đúng IG account và reach lifetime theo từng media. Hai sample chỉ so sánh khi platform/channel/revision, subject local ID, definition semantics, source và measurement window tương thích; observedAt tăng và provenance còn đủ. Giá trị không đổi vẫn có delta 0 nếu cặp hợp lệ. Reach DISTINCT_TOTAL không cộng các bài thành unique reach, không làm mẫu số ER. Impressions UNSUPPORTED không thay bằng views/reach. Thiếu grant, lỗi, thiếu root hoặc thiếu cặp để lại lý do loại; không bù lịch sử.

[Manifest production](sp-045-live-manifest.json): follower/reach T1/T2 đều **NOT_RUN**, snapshotId/collectionId/observedAt/collectedAt/readback chưa có. Impressions của media IG-11 không đủ điều kiện thu, null/UNSUPPORTED theo evidence; capability production vẫn chờ Channel owner. Lịch T1 bắt đầu sau mở gate của từng metric, T2 là lần thu thật sau T1 trên cùng scope/revision; không đặt giờ hứa hẹn trước đầu vào.

Room entities/DAO/migration, fresh install, upgrade bảo toàn Draft v1, FK/unique/rollback và restart/readback trên API 23/36 đều **NOT_RUN** vì chưa có slot/root production. Nghị hiện thực sau khi nhận artifact rồi mới thu live và đóng gate. Issue #47 tiếp tục mở.
