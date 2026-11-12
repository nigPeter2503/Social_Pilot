# SP-017 — Bằng chứng xử lý review

- Ngày: 12/11/2026 (Asia/Ho_Chi_Minh); người chạy: Codex.
- Môi trường: Windows, JDK 21.0.13, Android SDK Platform 36, AVD SocialPilot_API36 (Android 16/API 36).
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain --no-daemon`: BUILD SUCCESSFUL, 5 JVM unit test, lint 0 lỗi/13 cảnh báo.
- `./gradlew.bat :app:connectedDebugAndroidTest --console=plain --no-daemon`: BUILD SUCCESSFUL, 5/5 instrumented test đạt, 0 skipped/failed.
- Ca hồi quy: `cancelledOrReplacedRunCannotReceiveOldCheckpoint` xác nhận UPDATE của worker cũ ghi 0 hàng sau invalidate hoặc thay run; `secondViewModelInSameProcessDoesNotInterruptActiveStream` xác nhận ViewModel thứ hai giữ A ở RUNNING; `versionOnePrototypeDatabaseMigratesWithoutLosingResult` xác nhận text/state/checkpoint v1 được giữ và thêm run ID.
- Thử thủ công: mở prototype, bấm Start A rồi force-stop app trước chunk đầu, mở lại qua MainActivity. `stateA` hiển thị “Interrupted; tap Start to run again”, `resultA` chưa có kết quả và không tự tạo stream mới. Bắt đầu A mới sau job cũ; `workStatusA` hiển thị “No sample job yet.” cho lượt mới.
- `git diff --check`: đạt.

Chưa chạy API 23 hoặc điện thoại thật trong lượt sửa review này. Worker sau force-stop và tích hợp AI/SP-014 vẫn ngoài phạm vi kiểm chứng.
