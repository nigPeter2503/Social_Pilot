# Kết quả kiểm tra ngày 30/10/2026

- Android: assembleDebug, testDebugUnitTest và lintDebug thành công.
- Backend: test và bootJar thành công.
- Tổng cộng 5 kiểm thử đạt, không có lỗi hoặc thất bại.
- Android lint: 0 lỗi, 14 cảnh báo về phiên bản thư viện/công cụ và target SDK mới hơn.
- Đã kiểm tra đủ 46 dòng truy vết yêu cầu và hai Gradle Wrapper.

Máy kiểm tra dùng JDK 21 và Android SDK 36. Chưa chạy trên emulator/thiết bị thật;
chưa kiểm chứng OAuth, AI hay các luồng F1–F9 đầy đủ. CI đã có cấu hình, chưa chạy trên GitHub.

APK được tạo tại android/app/build/outputs/apk/debug/app-debug.apk.
Backend được tạo tại backend/build/libs/socialpilot-backend-0.1.0.jar.
Các file build có trên máy này nhưng được loại khỏi ZIP mã nguồn.

## Kiểm tra baseline SP-015 ngày 10/11/2026

- Người thực hiện: Đỗ Phước Thiện (`PhuocThien06`).
- Revision mã nguồn được kiểm tra: `0348982c8f295d946815da91eae491bd9645ac94` (`main` trước thay đổi tài liệu/CI của SP-015).
- Môi trường: Windows, Temurin JDK 21.0.13, Android SDK Platform 36, Android Build Tools 36.0.0. `ANDROID_HOME` trỏ tới SDK của máy kiểm tra.

| Kiểm tra | Kỳ vọng | Kết quả thực tế | Bằng chứng |
| --- | --- | --- | --- |
| Tại `android/`: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain` | Build, unit test và lint thành công | `BUILD SUCCESSFUL`; APK debug được tạo | `android/app/build/outputs/apk/debug/app-debug.apk`; báo cáo `android/app/build/reports/lint-results-debug.html` trên máy kiểm tra |
| GitHub Actions của revision `0348982` | Job Android và backend thành công | Cả hai job thành công; lượt chạy này có 0 artifact | [Workflow run #13](https://github.com/nigPeter2503/Social_Pilot/actions/runs/41795456566) |
| GitHub Actions của commit SP-015 `c5f4d84` | Job Android và backend thành công; lưu APK debug thành artifact | Cả hai job thành công; có artifact `socialpilot-debug-apk` | [Workflow run của SP-015](https://github.com/nigPeter2503/Social_Pilot/actions/runs/42857432928) |
| Cài APK, lưu topic và mở lại ứng dụng | Topic vẫn còn sau khi mở lại | Chưa chạy: máy kiểm tra chưa có AVD và ADB chưa nhận thiết bị | Cần bổ sung tên thiết bị, Android API, ảnh/video và kết quả thực tế |

Workflow của SP-015 đã bổ sung bước tải APK debug thành artifact. Sau mọi commit tiếp theo, cần kiểm tra CI của chính commit đó trước khi bàn giao.
