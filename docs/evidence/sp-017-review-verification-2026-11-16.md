# SP-017 — kiểm chứng sửa review vòng hai, 16/11/2026

Nguồn: PR #70, base của lượt sửa `a3836e9`; Windows, Android SDK Platform 36, Gradle Wrapper 8.13, AVD `SocialPilot_API23` (API 23) và `SocialPilot_API36` (API 36). Test sử dụng dữ liệu mẫu, không gọi AI hay social provider.

| Ca | Cách kiểm | Kết quả |
| --- | --- | --- |
| Hủy job sau khi mở ViewModel mới | Xếp WorkRequest có network constraint, tạo ViewModel mới không giữ run trong bộ nhớ, bấm `cancelB()`; đối chiếu Room và WorkManager | ID job bị xóa trong Room, WorkInfo `CANCELLED`, checkpoint `NOT_STARTED`; checkpoint đã ghi của A không đổi |
| Operation enqueue lỗi | Tiêm future thất bại vào cổng enqueue, kiểm row và xếp lại | Row chỉ bị dọn khi run/job khớp; cờ lỗi bật, ID xóa, checkpoint trước đó giữ nguyên; lần xếp lại xóa cờ lỗi |
| Process dừng giữa Room và WorkManager | Tạo đúng trạng thái Room đã gán ID nhưng WorkManager không có WorkSpec, gọi đối soát khi mở | ID mồ côi bị xóa, cờ lỗi bật, checkpoint cũ giữ nguyên; UI báo có thể xếp lại. Đây là mô phỏng trạng thái sau gián đoạn, chưa là thao tác kill đúng giữa hai lệnh ghi |
| Hai ViewModel cùng process | VM1 chạy A, VM2 attach rồi hủy A | Cả hai điều khiển cùng coordinator trong AppContainer; row A chuyển `CANCELLED`, run ID không đổi |
| Migration | Raw seed v1 và v2; Room nâng lên v3 | Text/state/checkpoint/run/job ID lịch sử còn nguyên; cờ lỗi mặc định false |

Lệnh chạy tại `android/` với `ANDROID_HOME` trỏ tới SDK trên máy:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Kết quả trước khi push: `assembleDebug`, 5/5 JVM test, `lintDebug` (0 lỗi, 13 cảnh báo) và APK instrumentation đạt. `connectedDebugAndroidTest` đạt **7/7 trên API 36** và **7/7 trên API 23**. Lần chạy API 23 đầu thất bại do test mới dùng `java.util.function.BooleanSupplier`, không có trên Android 6; test đã đổi sang interface nội bộ và chạy lại đạt. `git diff --check` đạt. CI đúng head mới cần xác nhận sau push. Backend không đổi.
