# SP-041.04/.05 · Bàn giao Worker và cache comment

Nghị · PR #87 · 21/11/2026 · Base `5cebef2` · Fixture `MO_PHONG`

Pull và WorkManager dùng chung `CommentRepository`. Work Data giữ fingerprint, connection revision và số chuỗi đã hoàn tất khi enqueue. Worker tra query trong registry và đọc cursor từ checkpoint của đúng kênh, post, fingerprint và revision. Sau khi owner tăng revision, hook `CommentSyncWorker.afterInvalidation` hủy unique work của revision cũ, hủy request đang chạy và dọn dữ liệu comment của revision đó. Có thể gọi lại hook với cùng cặp kênh/revision khi cleanup lỗi.

| Phép thử | Kết quả quan sát |
|---|---|
| .04 · Pull trang đầu, dựng lại coordinator rồi chạy Worker | Bốn query IG có cursor riêng, khác kênh, media hoặc mốc lịch sử. Key sai revision trả `OBSOLETE`. Bốn Worker đọc trang hai theo thứ tự ngược với đúng cursor của mình. Mỗi trang hai lặp ID trang một. Tám request để lại bốn row, mỗi query đọc đúng một ID. |
| .04 · Đứt kết nối trong request trang hai | Cache IG trả `OFFLINE_CACHED`, giữ ID trang đầu, checkpoint `MORE` và lỗi `NETWORK_ERROR`. Resume trước hạn không gửi HTTP. Sau 30 giây trên clock fixture, Worker dùng lại cursor, thêm một ID mới và xóa lỗi. |
| .04 · HTTP 429 ở trang hai | Giữ cache, lần sync hoàn tất trước đó và checkpoint. Retry-After 120 giây áp dụng cho cả query khác cùng kênh/revision. Trước hạn không có request mới. Đến hạn tiếp tục cùng cursor. Worker cho retry ở ba lượt đầu, trả `EXHAUSTED` ở lượt thứ tư nếu vẫn phải chờ. |
| .05 · IG mất revision trong lúc nhận trang hai | Latch giữ response thành công tới sau invalidation và trước cancel. Worker trả `OBSOLETE`. ID đến muộn vắng mặt, checkpoint và attempt giữ nguyên. Hai lượt cleanup chỉ hủy fingerprint IG một lần, cả sáu tập dữ liệu của revision cũ về 0. Facebook cùng revision, external post ID và comment ID vẫn đọc được và job đã chuẩn bị nhận thêm comment mới. |
| .05 · Hủy job thật và chạy lại cleanup | WorkManager TestDriver hủy cả job IG đang chạy lẫn job IG đang chờ mạng. Job Facebook vẫn `ENQUEUED`. Lỗi được tiêm giữa transaction dọn dữ liệu. IG reconnect và dùng lại ID, sau đó chạy lại cleanup hai lần. Cache và handled của IG mới và Facebook đều còn. Job Facebook hoàn tất. |

Các ca JVM dùng adapter, repository và writer của ứng dụng với MockWebServer, clock fixture và store giao dịch trong RAM. Trên AVD, WorkManager chạy với HTTP interceptor và cùng store fixture. Room production, migration/fresh install API 23/36 và đối soát provider thật SP-041.06 còn chờ Channel/revision, hook disconnect, slot schema và grant đọc comment.

## Chạy lại

Từ `android` trên Windows với JDK 21, Android SDK và cache Gradle offline đã chuẩn bị:

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Plan ghi 216 unit test ở base. Worktree Worker trước khi bổ sung ba ca đạt 236/236. Worktree bàn giao đạt **239/239** trong 13 suite JUnit, 0 failure/error/skip. Assemble debug đạt. Lint offline đạt, 0 lỗi và 6 cảnh báo. Có thể kiểm số đếm trong XML tại `app/build/test-results/testDebugUnitTest` và `app/build/reports/lint-results-debug.xml`.

**Instrumentation riêng** đạt **3/3** trên AVD cấu hình Pixel 9, Android API 37 x86_64. Chạy từng phương thức trong process instrumentation riêng trên AVD thử nghiệm có kết nối ban đầu. Ca mất mạng tự tắt rồi khôi phục Wi-Fi/data của AVD.

```powershell
.\gradlew.bat --offline '-PdeviceTestRunner=com.socialpilot.app.core.CommentDeviceRunner' :app:assembleDebug :app:assembleDebugAndroidTest
$device = 'emulator-5588'
adb -s $device install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$runner = 'com.socialpilot.app.test/com.socialpilot.app.core.CommentDeviceRunner'
$class = 'com.socialpilot.app.core.CommentWorkDeviceTest'
foreach ($method in 'retryAfterCannotExceedStartBudgetOrSendEarlyHttp','networkLossDuringSecondPageKeepsCheckpointAndResumes','invalidatedWorkCancelsRunningAndNetworkConstrainedQueries') {
    adb -s $device shell am instrument -w -e class "$class#$method" $runner
}
```
