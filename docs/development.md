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
`.env.example` là mẫu tham khảo; Spring không tự nạp file `.env`, cần đưa biến vào shell/hosting. Backend yêu cầu cấu hình OAuth Facebook hợp lệ và sẽ dừng ngay khi thiếu hoặc sai cấu hình.

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
{"status":"UP","service":"socialpilot-oauth","oauth":"CONFIGURED"}
```

`UP` nghĩa là server đang hoạt động. Backend chỉ khởi động khi cấu hình Facebook hợp lệ; `CONFIGURED` chưa chứng minh callback Meta chạy thành công. Nếu cấu hình thiếu/sai, thông báo fail-fast nêu tên biến cần sửa. Nhấn **Ctrl+C** ở terminal đang chạy `bootRun` để dừng server.

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
| Backend dừng lúc khởi động với lỗi Facebook OAuth configuration | Kiểm tra tên biến trong thông báo fail-fast và nguồn cấu hình trong dòng log chẩn đoán an toàn |
| Không tìm thấy JDK/toolchain 21 | Chọn JDK 21 cho terminal và Gradle |

Đổi cổng cho phiên chạy hiện tại:

```powershell
$env:PORT = '8081'
.\gradlew.bat bootRun --console=plain
```

Sau đó kiểm tra `http://127.0.0.1:8081/api/health`.

Tham khảo: [Spring Boot về tác vụ bootRun](https://docs.spring.io/spring-boot/3.5/gradle-plugin/running.html).

## Thử nghiệm Facebook OAuth (SP-010)

Backend đọc cấu hình từ biến môi trường; không đưa App Secret hoặc access token vào source, `application.properties`, log hay Git:

- `FACEBOOK_APP_ID`
- `FACEBOOK_APP_SECRET`
- `FACEBOOK_REDIRECT_URI` — URI HTTPS chính xác `https://<tunnel-host>/api/oauth/facebook/callback`, phải trùng URI trong Meta dashboard.
- `FACEBOOK_TARGET_PAGE_ID` — Page được chọn để thử nghiệm.
- `FACEBOOK_GRAPH_API_VERSION` — mặc định `v27.0`.

Đặt đủ các biến trên trong môi trường tiến trình backend, rồi khởi chạy backend tại cổng 8080 và tạo Cloudflare Quick Tunnel tới `http://localhost:8080`. Mỗi lần hostname Quick Tunnel đổi, cập nhật `FACEBOOK_REDIRECT_URI` và Valid OAuth Redirect URI của Meta cùng lúc rồi khởi động lại backend. Tunnel phải còn chạy trong toàn bộ lượt đăng nhập. Dòng log khởi động cho biết app ID, redirect URI, Graph version, độ dài secret, cờ secret từng có khoảng trắng đầu/cuối và tên property source; secret, authorization code, token và URL request không được ghi log. Lỗi Graph tại `code_exchange` chỉ ghi các trường lỗi đã lọc và dấu vân tay SHA-256 8 ký tự của code.

Android nhận base URL qua biến môi trường `SOCIALPILOT_OAUTH_API_BASE_URL` hoặc Gradle property `-PsocialpilotOauthApiBaseUrl=https://<tunnel-host>/`. App chỉ chấp nhận HTTPS. Callback ứng dụng là `socialpilot://oauth/callback`; backend chỉ cho phép `redirectId=android-dev` và route cố định này.

Phiên đăng nhập nằm trong bộ nhớ backend (10 phút), handoff dùng một lần (60 giây). Khởi động lại backend sẽ làm mất các phiên đang chờ. OAuth token và verifier trên Android được mã hóa bằng Android Keystore, không lưu trong Room. SP-010 đã ghi nhận callback OAuth quay lại app trên điện thoại thật và một bài Page thật qua Graph API Explorer (ID/permalink được ghi trong hồ sơ SP-010); bài đăng chưa đi qua hàng đợi/publish flow của ứng dụng. State/replay/TTL/race đã có test với provider giả. Ngày 12/11, owner đã thử [callback sau khi dừng tiến trình app](evidence/sp-010-cold-start-callback-2026-11-12.md): ảnh terminal không còn PID trước xác nhận, kết quả quay lại app đúng Page do owner báo cáo. Ca [restart backend giữa phiên OAuth](evidence/sp-010-backend-restart-callback-2026-11-12.md) có ảnh `INVALID_STATE` cho phiên cũ và thông báo kết nối đúng Page cho phiên mới, theo trình tự owner xác nhận. Khi chạy ca restart, giữ Cloudflare chạy và chỉ restart backend để hostname không đổi. App tự refresh/xử lý token hết hạn hoặc thu hồi và ảnh/alt text vẫn chưa được kiểm chứng; metadata/mở rộng token thủ công có hồ sơ riêng.

Bản sửa review SP-010 chuyển OAuth secure storage sang hai payload `connected_v2` và `pending_v2`. Lần đọc dữ liệu OAuth kiểu v1 sẽ xóa các trường OAuth cũ và yêu cầu kết nối lại; không xóa Room hay bản nháp. Không tự ghép/migrate các trường cũ vì có thể đã chứa Page/token không cùng lượt kết nối. Lỗi ghi không báo kết nối thành công; bản ghi hỏng được vô hiệu toàn bộ.

Kiểm thử Keystore bổ sung: tại `android`, chạy `.\gradlew.bat :app:connectedDebugAndroidTest` trên emulator/thiết bị thử. Test dùng credential giả và file preferences riêng có tiền tố `sp010_test_`, không sửa file OAuth thông thường, không gọi Meta hoặc đăng bài. `:app:assembleDebugAndroidTest` chỉ xác nhận build APK test, không chứng minh test đã chạy trên thiết bị.
