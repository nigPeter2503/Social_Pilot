# SP-045 (collector/Worker) · Thu snapshot từ manual refresh và WorkManager

Nghị · 19/11/2026 21:44 Asia/Saigon · Issue SP-045 #47. Phần điều phối đã kiểm local, dữ liệu và credential của toàn bộ test mang nhãn **MO_PHONG**. Số lần gọi API thật bằng 0.

## Base và phụ thuộc

Worktree `D:/CNTT/mobile/Social_Pilot-SP045`, nhánh `feat/sp-045-follower-adapter`. HEAD, merge-base, origin/main local và main remote đều ở `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa`. Phiên này chưa stage, commit, push, tạo PR hoặc gửi yêu cầu review.

| Đầu vào | Kết quả đối chiếu |
|---|---|
| Analytics draft-4 §§5/8/9, Social §§3/5 và master plan SP-045 (collector/Worker) | Khóa theo scope/subject/collection, action theo operation, revision ở transaction ghi. Slot ngày do caller truyền bằng UTC epoch. |
| SP-045 (adapter follower, metric bài và lưu snapshot) | Đọc ba evidence, hai adapter, request/result, SnapshotReading, SnapshotWriter, SnapshotDao, SyncCoordinator, AppContainer và AppDatabase. Trước sửa có 29 file untracked, diff tracked và index rỗng. Sau sửa 26/29 có cùng SHA-256. Ba file bổ sung API là SnapshotDao, SnapshotReading và SimulatedSnapshotDao. Writer và hai adapter giữ nguyên byte. |
| Minh · SP-022 | Issue #24 open, 0 comment. Main có ChannelInfo/OAuth. Trong refs remote chưa thấy nhánh giao SP-022 hoặc SHA cho Channel DAO, revision bền và capability/grant theo operation. [Issue #24](https://github.com/nigPeter2503/Social_Pilot/issues/24) |
| Đạt · SP-012 | PR #71 head `11abeb4`, merge `5a5f82b` là quy ước Room. PR #82 head `178e05c` dùng slot Brief. Issue #14 cùng PR #71/#82 có 0 issue comment. Chưa có base/slot được giao cho snapshot follower. AppDatabase và export v1 ở blob schema `5172492e197da5086e9ac37f1c0bcc8a5db5e15b`. [PR #71](https://github.com/nigPeter2503/Social_Pilot/pull/71) · [PR #82](https://github.com/nigPeter2503/Social_Pilot/pull/82) |
| SP-041 (PublishedPost) | Issue #43 open, 0 comment. Evidence worktree SP041 trên `230c2a6` có upsert mô phỏng và đề xuất Room. Chưa có PublishedPost production để resolve native post trong scope. [Issue #43](https://github.com/nigPeter2503/Social_Pilot/issues/43) |

Kiểm Git bằng `git status --short --branch --untracked-files=all`, `git rev-parse HEAD origin/main`, `git merge-base HEAD origin/main`, `git diff --stat HEAD`, `git diff --cached` và `git -c credential.helper= -c core.askPass= ls-remote origin 'refs/heads/*' 'refs/pull/*/head'`. Truy vấn GET GitHub issues cho #14/24/43/47/71/82. Lần đọc refs đầu trong sandbox lỗi DNS, truy vấn chỉ đọc ngoài sandbox thành công.

## Đường chạy và các giới hạn đã chọn

- `AppContainer.metrics.manualRefresh` trả ticket gồm job, work ID và Operation để đợi kết quả enqueue. Cả refresh và caller theo lịch đều dùng cùng hàng đợi công việc một lần, cùng `MetricCollectionWorker` và `MetricCollectionCoordinator`. Interface placeholder `SyncCoordinator.requestSync(String)` thiếu revision, operation và target. Bản production hiện trả trạng thái `DEPENDENCIES_UNAVAILABLE` nếu được gọi trước khi nối DAO/provider. Instrumentation dùng constructor DI. Fake provider, DAO và clock nằm trong test.
- WorkData giữ nguyên collection ID, collectionStartedAt, identity/revision, version, cờ follower và từng query bài cùng period/evidence. Refresh mới cấp UUID ngay cả khi đồng hồ trùng nhau. Unique work `APPEND_OR_REPLACE` theo channel/revision tuần tự hóa hai refresh. Worker đọc lại progress theo khóa subject/collection. Lần thực thi có IO mới tạo attempt UUID mới. Đối tượng Batch giữ cố định attempt UUID, readings và completedAt cho giao lại. Một callback chỉ đi vào mapper của đúng request gốc.
- Một job tối đa 8 endpoint, mỗi query của một post đo một kỳ trong một collection. Mỗi endpoint có tối đa 3 lần thực thi đã ghi. Mỗi work request có tối đa 8 lượt bắt đầu trước khi dừng retry tự động. Mỗi callback được đợi tối đa 20 giây. Đây là các giới hạn local để review. WorkManager yêu cầu CONNECTED, dùng exponential backoff gốc 30 giây. Sau 429/mất mạng dừng phát thêm request trong lượt đang chạy. Coordinator dùng thời điểm muộn hơn giữa backoff và Retry-After. Worker có thể thức muộn hơn hoặc thức sớm để kiểm thời hạn, lần thức sớm không gọi provider. Refresh mới cùng revision cũng tôn trọng Retry-After đã ghi.
- Mỗi subject tạo một batch. Lượt sau chỉ gọi operation còn thiếu/chờ retry. Follower có thể hoàn tất riêng khi post thiếu ownership. Adapter kiểm capability, revision và grant con. Writer xác minh lại Channel và local post trong transaction; snapshot/attempt cùng commit. Cache trả lastGood nguyên sample/provenance, lastAttempt và gaps theo subject/operation của slot ngày, kèm outcome/lỗi nếu có. Kết quả nghiệp vụ ở output `state`; terminal domain result hoàn tất work item để lượt refresh kế tiếp trong chain được chạy.
- `cancel(channelId, revision)` ngắt lượt đang chờ callback và hủy queued work có tag đó. SP-045 (disconnect/recovery) sẽ gọi hook cùng invalidation revision và policy cleanup. Snapshot cache trong app còn phụ thuộc implementation Room của các port. Lịch hằng ngày/tập bài và slot giờ cần cấu hình khi có channel thật.

## Xác minh

| Kiểm tra local | Kết quả |
|---|---|
| `node docs/contracts/verify-analytics-examples.cjs` | 75/75 MO_PHONG |
| `node docs/api-verification/facebook/verify-fixtures.cjs` | 55/55 MO_PHONG |
| `node ../Social_Pilot/docs/api-verification/linkedin/verify-fixtures.cjs` | 128/128 MO_PHONG trên worktree W1 HEAD `7931f4d` |
| Gradle assembleDebug, testDebugUnitTest, lintDebug, installDebug, installDebugAndroidTest | BUILD SUCCESSFUL 33s trên bản cuối. JVM **161/161**, 0 skipped/errors/failures. Gồm 14 collector, 29 follower, 47 post, 11 storage, 60 regression khác. Lint **0 lỗi, 6 cảnh báo**. [JUnit](../android/app/build/reports/tests/testDebugUnitTest/index.html) · [Lint](../android/app/build/reports/lint-results-debug.html) |
| TestDriver và Worker trên AVD Pixel_9, emulator-5554, API 37 x86_64 | **3/3**. Hai refresh đang ENQUEUED/BLOCKED, 0 calls trước constraint, sau đó 2 UUID/2 calls/2 snapshots. Round-trip WorkData giữ nguyên URN, period và scope. Dựng lại 3 Worker trong cùng process, offline/15s/30s cho retry/retry/success, 1 call, 2 attempt ID, 1 snapshot. |
| WorkManager thật, hai tiến trình AVD, bật/tắt mạng thật của AVD | **2/2 pha của 1 kịch bản**. Job ngoại tuyến ở ENQUEUED, force-stop app, mở instrumentation với PID mới khi vẫn offline, bật mạng và đợi đúng work hoàn tất. Số liệu ở bảng dưới. |
| Diff | PASS `git diff --check`, `git diff --cached --check` và `git diff --no-index --check -- /dev/null <file>` cho **39/39 file untracked**. Index rỗng. Đối chiếu hash file đầu phiên và blob của 15 file code/build/test collector/Worker. Thay lại dòng cũ trong bộ nhớ tái tạo đúng từng byte master plan trước sửa. |

**Timeline thực của kịch bản AVD**

| Pha | PID | WorkRequest | collectionId / collectionStartedAt | Provider và snapshot |
|---|---:|---|---|---|
| Xếp offline | 17247 | `dee31759-1b9d-4123-8410-036b6b763e5d` | `bf5db419-045f-4725-b3af-c5751b7e7b70` / `1795099442614` | 0 calls |
| Sau force-stop, ứng dụng khởi tạo lại và có mạng | 17392 | Cùng WorkRequest | Cùng collectionId và epoch trên | 1 call, snapshot MO_PHONG `43b2a792-173e-474e-a922-96e4dd5df80d` |

WorkManager dùng database thật của nó trong phép thử hai PID. Snapshot được tạo trong DAO mô phỏng của tiến trình thứ hai. Test JVM tạo coordinator mới trên cùng DAO kiểm 429 sau khi đã ghi attempt. Hai phép thử có phạm vi bằng chứng khác nhau.

**Các mốc đã assert trong JVM** (đơn vị mili giây; `T=1795060800000`).

| Tình huống | Phép đối chiếu |
|---|---|
| 429 có Retry-After 95s | T có 1 HTTP và 1 attempt. T+94.999 vẫn 1 HTTP. T+95.000 đạt 2 HTTP, attempt ID mới, cùng khóa/start, đúng 1 snapshot. Refresh mới bị giữ khi scope còn embargo 120s. |
| 429 thiếu header | Mốc retry T+30.000, T+90.000. Sau lỗi thứ 3, EXHAUSTED; gọi lại sau hạn vẫn tổng 3 HTTP, 3 attempts, 0 snapshots. |
| Offline sau mẫu tốt | Lượt mới 0 HTTP bổ sung, lastAttempt ở slot hôm sau, gap OFFLINE. Cùng snapshot ID, giá trị 0, observed/collected T và nguồn MO_PHONG. |
| Partial theo post | Follower + likes tốt, shares 429 tạo 2 snapshots. Retry chỉ đọc shares, tổng 4 HTTP, 3 subject attempts, vẫn 2 snapshots, like/timestamp/provenance giữ nguyên. Omitted shares giữ mẫu tốt từ collection trước và trả lý do riêng. |
| Scope, callback, capability | Busy khi lượt đầu đang chờ, 1 provider call. Callback đến sau cancel hoặc đổi revision không sinh snapshot. Callback nhầm collection bị loại, batch giao lại cùng UUID chỉ lưu một attempt. 401 trả RECONNECT, thiếu quyền trả RESOLVE_PERMISSION, unsupported trả NONE; sai operation, thiếu root không phát HTTP tương ứng. |

**Lệnh thiết bị** · Chạy trong PowerShell từ `android/`, AVD riêng mở với `-avd Pixel_9 -read-only -no-window -no-snapshot -no-audio`, đã đóng sau test. SDK ở `C:/Users/ASUS/AppData/Local/Android/Sdk`. Gradle ban đầu gặp Access denied tại file khóa của cache wrapper. Chạy lại offline với quyền đọc/ghi cache đã đạt. TestDriver từng đạt 2/2 trên runner mặc định. Bản cuối dùng runner DI chọn bằng Gradle property vì manifest test đầu tiên chỉ đăng ký runner mặc định.

```powershell
$env:JAVA_HOME = 'D:/CNTT/mobile/.reviews/pr70-tools/jdk-21.0.13+9'
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
.\gradlew.bat --offline --no-daemon --console=plain '-PdeviceTestRunner=com.socialpilot.app.core.MetricDeviceRunner' :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:installDebug :app:installDebugAndroidTest
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
$runner = 'com.socialpilot.app.test/com.socialpilot.app.core.MetricDeviceRunner'
$testClass = 'com.socialpilot.app.core.MetricWorkDeviceTest'
$cases = @('driverQueuesTwoManualCollectionsAndRunsWorkerOnlyWhenConnected','restoresPostTargetsAndVerifiedPeriodFromWorkData','recreatedWorkersReuseInputAndOnlyRetryWhenDue') | ForEach-Object { "$testClass#$_" }
& $adb -s emulator-5554 shell am instrument -w -e class ($cases -join ',') $runner
& $adb -s emulator-5554 shell svc wifi disable
& $adb -s emulator-5554 shell svc data disable
& $adb -s emulator-5554 shell am instrument -w -e sp045Phase queue -e class "$testClass#offlineProcessRestart" $runner
& $adb -s emulator-5554 shell am force-stop com.socialpilot.app
& $adb -s emulator-5554 shell am instrument -w -e sp045Phase resume -e class "$testClass#offlineProcessRestart" $runner
```

## Phạm vi còn chờ

| NOT_RUN | Lý do và người nhận |
|---|---|
| Thu follower thật SP-045 (lượt follower T1) | Minh giao Channel/credential reference/capability READ_FOLLOWER_COUNT theo account và revision, xác minh grant/endpoint/version thật. Đạt giao slot follower, Nghị nối DAO Room và AppContainer, kiểm migration rồi đo từ ứng dụng. |
| Thu metric bài thật SP-045 (lượt metric bài T1) | Thêm root PublishedPost của SP-041 có local/native ID đúng scope, ownership transaction và grant từng metric/query/version/period. Nghị thu bộ sample thật với provenance. |
| Đọc lại snapshot/attempt từ Room qua kill giữa retry, migration/API 23 và 36 | DB app hiện chỉ export v1 Draft. Chưa có migration hoặc DAO production để chạy. Host chỉ có AVD system image API 37. Cần kiểm data v1→đích và fresh install trên ma trận đã chốt sau khi Đạt cấp slot. |
| Lịch hằng ngày và cặp mốc thật, cleanup theo chính sách | Chờ cấu hình lịch/grant, hook invalidation/retention SP-045 (disconnect/recovery) và hai lượt thu thực tế SP-045 (lượt T2/recovery). Nghị đối chiếu gap/partial và phối hợp SP-046 (repository/query). |

**Đủ cho Draft PR issue #47** · Nghị có thể đóng gói các adapter và collector/Worker gồm adapter, writer, coordinator/Worker, tests và evidence để review phần độc lập. **Trước review toàn SP-045** · Hoàn tất các gate live/Room và recovery ở trên, Đạt kiểm migration/ownership, Thiện kiểm lastGood, lastAttempt, provenance, gap và DTO consumer. Việc gửi review nằm ở phiên bàn giao sau.

## Định danh bản local

SHA-256 của manifest 15 Git blob theo thứ tự đường dẫn, UTF-8, hai khoảng trắng, LF cuối là `410f588ed9a22ab718c63a0a21964cafada579e30e881b0d0bb85abbf12fc184`. Đây là SHA gói local trên base nêu trên.

```text
70fced665428e470593c8f47e9b5824179b3bf60  android/app/build.gradle
603cad90b4e3633174e49ffdad8572273b097a63  android/app/src/androidTest/java/com/socialpilot/app/core/MetricDeviceRunner.java
1f0888fb754ee99d50075174e6acd81834165d20  android/app/src/androidTest/java/com/socialpilot/app/core/MetricWorkDeviceTest.java
2f47c26927726578e4801c90c00335f499873cac  android/app/src/androidTest/java/com/socialpilot/app/data/remote/social/DeviceMetricFixture.java
b0eca028fb00d5b7f30ababf2215fdd3d38867db  android/app/src/main/java/com/socialpilot/app/SocialPilotApplication.java
8bf47bf5b782afe5d9653db6645162e3d2583150  android/app/src/main/java/com/socialpilot/app/core/AppContainer.java
d858a39f9a43be21b8bee34ee696822810483f68  android/app/src/main/java/com/socialpilot/app/core/MetricCollectionWork.java
1b7aa6063a3199657296b718a8166bcbb55dd0f2  android/app/src/main/java/com/socialpilot/app/core/MetricCollectionWorker.java
5da2e755d84686fe8c237b3f1975b1a44abfc8f0  android/app/src/main/java/com/socialpilot/app/data/local/snapshot/SnapshotDao.java
7021b39608ea17be1cffcbe940b498ad30d79b80  android/app/src/main/java/com/socialpilot/app/data/remote/social/SnapshotReading.java
4f07fdc63e3904d09d0cd448680cdfca7a3852cc  android/app/src/main/java/com/socialpilot/app/data/repository/metrics/MetricCollectionCoordinator.java
4bb8b744fc59aacf8705984d2268fdc5b7b4e2c7  android/app/src/main/java/com/socialpilot/app/data/repository/metrics/MetricJob.java
dfba7612639108361a3e8e9062a472b1ff13bbde  android/app/src/main/java/com/socialpilot/app/data/repository/metrics/MetricProvider.java
d24fdb77bcd10bf5573787c870095080cd6acace  android/app/src/test/java/com/socialpilot/app/data/local/snapshot/SimulatedSnapshotDao.java
246b6c32fd2a5fc41a62f501830a4f406e751751  android/app/src/test/java/com/socialpilot/app/data/remote/social/MetricCollectionTest.java
```
