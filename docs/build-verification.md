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
