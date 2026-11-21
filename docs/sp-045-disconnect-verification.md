# SP-045 (disconnect/recovery) · Ngắt kênh và bảo vệ lần ghi snapshot

**Chờ phụ thuộc** · Nghị · 19/11/2026 22:23 Asia/Saigon. Trên dữ liệu `MO_PHONG`, hủy A7 dừng HTTP và Worker, callback cũ bị loại ở điểm ghi, cache B7 vẫn đọc được. WorkManager khôi phục job A7 qua force-stop và từ chối chạy khi A đã reconnect ở A9. Cleanup vật lý và giao dịch liên module trên Room production còn chờ Minh và Đạt. Tất cả lượt dưới đây dùng kênh test, với 0 request đến Facebook/LinkedIn thật.

## Base và bàn giao đã kiểm

| Nguồn | Bằng chứng tại thời điểm kiểm |
|---|---|
| Git | Nhánh `feat/sp-045-follower-adapter`, HEAD = upstream local = remote head `82abd975436a7c5a2183ca34053b595067d4a86c`. Remote main `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa`. Đầu phiên `git status --short --branch --untracked-files=all`, `git diff`, `git diff --cached`, `git ls-files --others --exclude-standard` đều cho thấy 0 file thay đổi. Master plan nằm ngoài worktree, SHA-256 đầu phiên `b63b17972143dfb9aa59a1a2ee363d9bb80e5a70124ca659beefc9359876d581`. |
| Đầu vào của Nghị | Đã đối chiếu mục, dòng tiến độ disconnect/recovery và HO-DISCONNECT với Analytics §8 `92daad64`, Room §§4/7 `4115755e`, Social §7 `609fa69e`. Commit `2f1d994` có writer/coordinator/Worker. Tầng lưu snapshot giao DAO port và fixture; collector/Worker giao hàm cancel và test callback muộn; hồ sơ metric bài đầu tiên ghi binding app còn thiếu. Các blob evidence lần lượt `792091b6`, `2fb820be`, `cf7e99d2`. [Storage](sp-045-snapshot-storage-verification.md) · [Worker](sp-045-collection-worker-verification.md) · [Live metric bài](sp-045-post-live-verification.md) |
| Minh · SP-024/SP-022 | #26 và #24 mở, 0 issue comment. Timeline #26 có tham chiếu #4/#54, chưa thấy commit hoặc PR giao hook. PR #66 head `d09819594e5b939d88c810a83cf8165c0abcc12c` giao contract Social. Danh sách head branch/PR hiện hành chưa có SP-024/SP-022 với Channel DAO, transaction invalidation, admission và hợp đồng retry cleanup. [#26](https://github.com/nigPeter2503/Social_Pilot/issues/26) · [#24](https://github.com/nigPeter2503/Social_Pilot/issues/24) · [#66](https://github.com/nigPeter2503/Social_Pilot/pull/66) |
| Đạt · schema/ownership | PR #71 head `11abeb4940bd59c3eed4b9fa82827fd17ccfef73`, merge `5a5f82b732aeb97423925c6b2e2667c940777486` giao quy ước. PR #82 `178e05cc6c01082ba2cbb94461504f953a22080f` export v2 cho Brief. #83 `c6e3dd5b612eade8fb75761c826afd9747da6462` dựa trên #82, export v3 cho editor. Chưa thấy pin slot, base/schema SHA hoặc version nguồn–đích cho snapshot/sample/attempt. AppDatabase tại HEAD chỉ có DraftEntity/DraftDao, export v1 `5172492e197da5086e9ac37f1c0bcc8a5db5e15b`. [#71](https://github.com/nigPeter2503/Social_Pilot/pull/71) · [#82](https://github.com/nigPeter2503/Social_Pilot/pull/82) · [#83](https://github.com/nigPeter2503/Social_Pilot/pull/83) |

Đối chiếu origin bằng `git -c credential.helper= -c core.askPass= ls-remote origin 'refs/heads/*' 'refs/pull/*/head'` và GitHub REST GET trên repo `nigPeter2503/Social_Pilot` cho `issues/{26,24,14,47}`, `issues/26/timeline`, `pulls?state=all&per_page=100`, PR #71/#82/#83 cùng files, issue comments và reviews. Lần đầu DNS trong sandbox lỗi, lượt đọc lại thành công.

## Điểm tích hợp và trách nhiệm

1. **Minh/SP-024 sở hữu luồng ngắt và transaction đổi revision của Channel.** Theo contract, trong cùng transaction phải đánh dấu revision cũ hết hiệu lực và đóng admission mới. Sau commit mới yêu cầu các module hủy theo `(channelId, oldRevision)`. Reconnect cùng identity cấp revision mới bền vững. Tại HEAD, `manualRefresh` vẫn có thể enqueue trực tiếp. `coordinator.restore` đọc lại Channel trước HTTP, trả `FAILED` với `CHANNEL_DISCONNECTED/RECONNECT` cho job cũ. Chưa có hàng rào enqueue phía SP-024. [Social §7](social-contract.md) · [Room §4](room-schema-contract.md)
2. **Nghị sở hữu collection.** Chưa có caller disconnect trong app. `AppContainer(context)` truyền coordinator `null`, Worker mặc định trả `DEPENDENCIES_UNAVAILABLE`. Test inject coordinator, gọi `MetricCollectionWork.cancel(A,7)` → `coordinator.cancel` interrupt đúng control → `await` thoát và `finally` gọi adapter handle → `Pending.cancel` hủy OkHttp `Call`. Cùng hàm `cancel` yêu cầu WorkManager `cancelAllWorkByTag("sp045/7/A")` (A là ID fixture), trả `Operation` bất đồng bộ. `onStopped` cũng hủy control. Tag đồng thời là tên unique chain khi enqueue. [Work](../android/app/src/main/java/com/socialpilot/app/core/MetricCollectionWork.java) · [Worker](../android/app/src/main/java/com/socialpilot/app/core/MetricCollectionWorker.java) · [Coordinator](../android/app/src/main/java/com/socialpilot/app/data/repository/metrics/MetricCollectionCoordinator.java)
3. **Ranh giới ghi là thứ tự commit.** `Control.deliver` chuyển batch tới `SnapshotWriter.write`. Bên trong `dao.transaction`, writer so identity, `CONNECTED`, revision và lookup local post đúng scope trước khi kiểm replay, đăng ký definition, ghi snapshot/sample và attempt. Callback đã commit trước invalidation có dữ liệu riêng của A cần cleanup. Callback ghi sau invalidation bị loại kể cả khi đã nhận response trước đó hoặc provider vẫn giao sau cancel. Fixture dùng cùng monitor cho `bind` và transaction copy-on-write. [Writer](../android/app/src/main/java/com/socialpilot/app/data/local/snapshot/SnapshotWriter.java) · [DAO fixture](../android/app/src/test/java/com/socialpilot/app/data/local/snapshot/SimulatedSnapshotDao.java)
4. **Ownership khi dọn.** Nghị dọn snapshot, sample con, attempt, cache và cursor/comment riêng của A theo policy bàn giao. Giữ dữ liệu B, definition dùng chung, draft/nguồn chung và link còn owner. Minh xử lý token/ref, OAuth state, job chưa gửi và đối soát publish. Đạt hủy Future/HTTP của AI, chốt với Minh phần chi phí tổng hợp và thứ tự migration. Module sở hữu file kiểm lại tham chiếu trước khi xóa. Bài đã đăng trên provider vẫn tồn tại. TTL tombstone 30 ngày và ngoại lệ chi phí còn chờ quyết định. [Analytics §8](analytics-data-contract.md) · [Room §§4/7](room-schema-contract.md) · [Social §7](social-contract.md)
5. **Recovery cleanup còn chờ bàn giao.** Nếu chết sau commit và trước khi cancel/dọn, revision phải tiếp tục chặn job và ghi cũ. Minh/Đạt cần pin cách tìm lại phần còn dở, bằng trạng thái bền ghi cùng invalidation hoặc phép quét an toàn theo contract. Mỗi owner chạy lại theo A/revision cũ, cả khi A9 đã có dữ liệu, kiểm lại ownership/reference và chỉ đánh dấu xong sau khi dọn thành công. Nghị cần kiểm ngắt giữa chừng và chạy hai lần cho cùng kết quả, so hàng vật lý A7/A9/B và definition. Hiện chưa có marker/scan hoặc DAO cleanup production. Marker trong test hai PID chỉ mang revision fixture để kiểm admission sau restart.

## Kết quả thực chạy trên bản local

| Phép kiểm | Quan sát |
|---|---|
| JVM · HTTP/cancel | `cancelStopsOnlyTheMatchingInFlightHttpCall` giữ request ở MockWebServer. Cancel B7 hoặc A8 vẫn để Call A7 chạy, cancel A7 trả `CANCELLED`, chính Call đó có `isCanceled=true`, 0 snapshot/attempt. Test collector/Worker `overlappingScopeAndLateAfterCancellation` tiếp tục giao callback thành công từ provider cố tình bỏ qua cancel, kết quả vẫn 0 snapshot/attempt. Test follower/post kiểm callback suppression ở adapter. |
| JVM · thứ tự ghi, replay, B | `disconnectOrdersWritesAndPreservesOtherChannelAcrossReconnect` commit A7=3 và B=11, xác nhận snapshot/attempt trước invalidation. Sau khi bind A8 DISCONNECTED, batch A7=99 bị từ chối. Writer mới trên cùng DAO từ chối giao lại ở A9. Tra khóa vật lý của collection muộn trả 0 snapshot/attempt, A9 chỉ có một snapshot mới giá trị 5. Toàn bộ History B gồm cả ID, sample, provenance và attempt bằng trước khi ngắt, registry vẫn một definition. Test collector/Worker đổi revision sau dispatch rồi thả callback, writer từ chối và coordinator trả `CANCELLED`. Đây là hai thứ tự tuần tự hóa trên DAO trong RAM. |
| Device · WorkManager TestDriver, Worker thật, DAO fixture | A7 ở `RUNNING`, A7 thứ hai ở `BLOCKED`. Sau invalidation, reconnect và gọi cancel A7 khi A9 cũng đã xếp hàng, cả hai WorkInfo A7 là `CANCELLED`, handle đang chờ nhận cancel. History B7 trước/sau bằng nhau, có một giá trị 0. Job B7 kế và A9 đều `COMPLETE`, cuối lượt B7 có hai snapshot, A9 có một. Test disconnect 1/1 và ba regression constraint/WorkData/retry 3/3. |
| Device · WorkManager thật, chết sau invalidation trước cancel | Hai pha 2/2 trên AVD riêng. PID `8042` queue A7 offline, work `d3b0bb25-5ce1-489b-9db9-e2346eb7fd23`, collection `d2acdfa3-7ab7-4f39-b827-ba7466c9a771`, calls=0. Fixture lưu mốc invalidation 8, force-stop. PID `8183` còn thấy đúng work ENQUEUED offline, bind A9 từ mốc fixture rồi bật mạng. A7 kết thúc với domain `FAILED`, `CHANNEL_DISCONNECTED/RECONNECT`, calls=0, A9 có 0 snapshot/attempt trước refresh. Job A9 mới hoàn tất với 1 call và đúng collection. DAO mới trong PID mới, kết quả này kiểm hàng đợi bền của WorkManager và admission. |
| Device · đối chứng cùng revision cho lượt T2/recovery | Hai pha 2/2 chạy lại trong phiên disconnect/recovery. PID `8671` → `8822`, work `a488ad2c-c1ef-48a6-89c9-40ccc08fdcab`, collection `f53d7bcf-8efe-435c-b859-ee7ac07fb782`, start `1795101732745` giữ qua force-stop. Calls 0 lúc offline, 1 sau resume, snapshot `619e5ab0-7d89-470f-891a-f908ea133754` trong DAO fixture của tiến trình mới. Evidence API 37 cũ của collector/Worker chỉ để tham chiếu. |
| Room production · NOT_RUN | Thiếu Channel/PublishedPost, production SnapshotDao, hook SP-024 và slot. Chưa thể kiểm ghi đồng thời trên SQLite, read-back cache sau process death hoặc xóa snapshot/sample/attempt A7 giữa chừng rồi chạy hai lần và đối chiếu FK/definition/A9/B. Chưa có migration đích để so upgrade từ version nguồn, bảo toàn Draft v1 và so fresh install API 23/36. |
| Live follower và metric bài · NOT_RUN | Phiên disconnect/recovery dùng A/B mô phỏng. Mẫu T1/T2 thật, credential/grant và mapping post vẫn cần được kiểm trong ứng dụng. |

## Lệnh và định danh bản kiểm

Windows, PowerShell, JDK 21.0.13+9, Gradle wrapper 8.13, compile SDK 36. AVD `Pixel_9` read-only, `emulator-5580`, API 37 x86_64. Các lệnh từ worktree trừ Gradle chạy trong `android/`.

```powershell
node docs/contracts/verify-analytics-examples.cjs
node docs/api-verification/facebook/verify-fixtures.cjs
node ../Social_Pilot/docs/api-verification/linkedin/verify-fixtures.cjs
.\gradlew.bat --offline --no-daemon --console=plain '-PdeviceTestRunner=com.socialpilot.app.core.MetricDeviceRunner' :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Fixtures đạt **75/75, 55/55, 128/128** (LinkedIn ở worktree `7931f4d`). Gradle lượt đầu bị chặn khóa cache wrapper trong sandbox. Chạy lại offline đạt **BUILD SUCCESSFUL**, 28 giây, **163/163 JVM**, 0 fail/error/skip, gồm 15 collector và 12 storage. Lint 0 lỗi, 6 cảnh báo. [JUnit](../android/app/build/reports/tests/testDebugUnitTest/index.html) · [Lint](../android/app/build/reports/lint-results-debug.html)

```powershell
$dev = 'emulator-5580'
$runner = 'com.socialpilot.app.test/com.socialpilot.app.core.MetricDeviceRunner'
$t = 'com.socialpilot.app.core.MetricWorkDeviceTest'
adb -s $dev install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s $dev install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s $dev shell am instrument -w -e class "$t#disconnectCancelsRunningAndBlockedRevisionWhileOtherWorkSurvives" $runner
adb -s $dev shell am instrument -w -e class "$t#driverQueuesTwoManualCollectionsAndRunsWorkerOnlyWhenConnected,$t#restoresPostTargetsAndVerifiedPeriodFromWorkData,$t#recreatedWorkersReuseInputAndOnlyRetryWhenDue" $runner
adb -s $dev shell svc wifi disable
adb -s $dev shell svc data disable
adb -s $dev shell am instrument -w -e class "$t#offlineProcessRestart" -e sp045Phase queue -e sp045Disconnect true $runner
adb -s $dev shell am force-stop com.socialpilot.app
adb -s $dev shell am instrument -w -e class "$t#offlineProcessRestart" -e sp045Phase resume -e sp045Disconnect true $runner
```

Đối chứng cùng revision lặp từ bước tắt mạng, bỏ `-e sp045Disconnect true` ở cả hai pha. Pha resume bật lại Wi-Fi/data. Đã đóng AVD test sau lượt cuối.

| Test local chưa stage | Git blob trên bản đã chạy |
|---|---|
| [MetricCollectionTest.java](../android/app/src/test/java/com/socialpilot/app/data/remote/social/MetricCollectionTest.java) | `a3feb55a11a997d83de097da264b706447281855` |
| [SnapshotStorageTest.java](../android/app/src/test/java/com/socialpilot/app/data/remote/social/SnapshotStorageTest.java) | `e546fcfa6d9943fc6121f5bea90bd2beb3ef9895` |
| [MetricWorkDeviceTest.java](../android/app/src/androidTest/java/com/socialpilot/app/core/MetricWorkDeviceTest.java) | `0568c1f0424207f42228fec398fb5e0073ee4b8c` |

Kiểm cuối bằng `git diff`, `git diff --check`, `git diff --cached`, `git status --short --branch --untracked-files=all` và `git diff --no-index` đối với evidence mới. Master plan chỉ cập nhật dòng tiến độ SP-045 (disconnect/recovery). Để hoàn tất, Minh giao SHA hook/policy và Channel root, Đạt giao slot/version/ownership, Nghị nối vào app và chạy lại các ca trên Room thật, gồm cả cleanup lặp và recovery cache B trước refresh.
