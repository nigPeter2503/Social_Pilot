# Hướng dẫn phát triển SocialPilot

## Mở và chạy Android

1. Mở thư mục `android` trong Android Studio. Dùng Gradle JDK 21.
2. Cài Android SDK Platform 36 và chấp nhận SDK licenses. Android Studio tự tạo `local.properties`.
3. Sync Gradle, chọn emulator/thiết bị Android API 23 trở lên, bấm Run.
4. Nhập topic và lưu. Danh sách lưu trong Room trên thiết bị, có thể xem lại sau khi mở lại app.

PowerShell tại `android`:
```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Build và chạy backend

Cần JDK 21 được Gradle tìm thấy (có thể là JDK riêng với IDE). PowerShell tại `backend`:
```powershell
.\gradlew.bat test bootJar
```
Mở `http://127.0.0.1:8080/api/health`. Backend mặc định chỉ lắng nghe localhost.
`.env.example` là mẫu tham khảo; cần tự đưa biến vào shell/hosting. Chưa cần API key để chạy bộ khung.

## Phiên bản đã khóa

Gradle 8.13; AGP 8.13.2; Android Java source 17, backend JDK 21; compile/target SDK 36; minSdk 23;
Room 2.8.5 với annotationProcessor; Spring Boot 3.5.16. Mã nguồn ứng dụng dùng Java.
Phiên bản khai báo cố định, không dùng `+`; wrapper được đưa vào cả hai build độc lập.


## Khi bootRun hiển thị 80% EXECUTING

Để khởi động server phát triển, chạy riêng tại thư mục `backend/`:

```powershell
.\gradlew.bat bootRun
```

Khi log hiện `Tomcat started on port 8080` và `Started OAuthApplication`, server đã khởi động thành công. `bootRun` giữ tiến trình chạy để phục vụ yêu cầu nên Gradle có thể tiếp tục hiển thị `80% EXECUTING` trong suốt thời gian server hoạt động. Không cần chờ lên 100% để sử dụng backend.

Giữ terminal này mở, dùng trình duyệt hoặc terminal thứ hai để kiểm tra:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Kết quả mong đợi (thứ tự các trường có thể khác):

```json
{"status":"UP","service":"socialpilot-oauth","oauth":"NOT_CONFIGURED"}
```

`UP` nghĩa là server đang hoạt động. `NOT_CONFIGURED` nghĩa là phần tích hợp OAuth chưa triển khai. Nhấn **Ctrl+C** ở terminal đang chạy `bootRun` để dừng server.

Nếu muốn bỏ thanh phần trăm của Gradle và chỉ xem log:

```powershell
.\gradlew.bat bootRun --console=plain
```

Lệnh `test bootJar` kết thúc sau khi build và kiểm thử, báo `BUILD SUCCESSFUL` nếu đạt. Lệnh `bootRun` chạy liên tục đến khi được dừng. Backend hiện là dịch vụ API; đường dẫn `/` chưa có website giao diện. Ứng dụng Android được mở riêng qua Android Studio; chạy backend không tự mở ứng dụng trên điện thoại.

## Xử lý lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| `80% EXECUTING` sau dòng `Started OAuthApplication` | Bình thường; mở `/api/health` để xác nhận server sẵn sàng |
| Cổng 8080 đã được sử dụng | Kiểm tra instance cũ đang chạy; dừng đúng instance đó hoặc đổi cổng |
| Đường dẫn `/` trả 404 | Dùng `/api/health`; backend chưa có trang chủ |
| OAuth trả `501 OAUTH_NOT_CONFIGURED` | Adapter OAuth chưa triển khai |
| Không tìm thấy JDK/toolchain 21 | Chọn JDK 21 cho terminal và Gradle |

Đổi cổng cho phiên chạy hiện tại:

```powershell
$env:PORT = '8081'
.\gradlew.bat bootRun --console=plain
```

Sau đó kiểm tra `http://127.0.0.1:8081/api/health`.

Tham khảo: [Spring Boot về tác vụ bootRun](https://docs.spring.io/spring-boot/3.5/gradle-plugin/running.html).
