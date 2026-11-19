# SP-053 · Runbook triển khai và kiểm chứng OAuth qua HTTPS

**Revision đã thử:** `230c2a6` · **Ngày:** 19/11/2026 (Asia/Bangkok) · **Người thử trên điện thoại:** Ngô Gia Minh. Môi trường thử gồm JDK 21.0.7, một tiến trình Spring Boot trên Windows, Cloudflare Quick Tunnel và Samsung SM-G990E. Phạm vi kiểm chứng thực tế là OAuth Facebook Page. [Issue #55](https://github.com/nigPeter2503/Social_Pilot/issues/55)

## Kết quả ngày 19/11

| Hạng mục | Kết quả | Bằng chứng |
|---|---|---|
| Build backend | `test bootJar` thành công trong 27 giây. 39 test đạt, 0 lỗi, 0 bỏ qua | Log Gradle và báo cáo XML của worktree SP-053 |
| Cài app Android | `assembleDebug` thành công. ADB báo `Success` khi cài APK lên Samsung SM-G990E | Kết quả build và cài qua ADB trong phiên kiểm thử |
| HTTPS health | `UP`, `CONFIGURED`, `socialpilot-oauth` | Minh gửi phản hồi từ URL HTTPS |
| OAuth Facebook mới trước restart | Kết nối thành công | Minh xác nhận trong phiên kiểm thử |
| Callback của phiên cũ sau restart | Trình duyệt hiển thị `{"code":"INVALID_STATE"}` | [Ảnh Minh cung cấp](evidence/sp-053-restart-invalid-state-2026-11-19.jpg). Trình tự restart dựa trên xác nhận của Minh |
| Phiên mới sau restart | Mở lại SocialPilot và kết nối thành công | Minh xác nhận ứng dụng báo thành công. Chưa có ảnh riêng của lần kết nối này |
| Health sau restart | `UP`, `CONFIGURED` tại `127.0.0.1:8080` | Đo ngày 19/11 lúc 14:14:29, PID 27140 |
| Thời gian khởi động | 3,108 giây theo log Spring của lần khởi động 13:49. JVM đã chạy 3,624 giây khi ghi dòng này | Log do Minh cung cấp, PID 28956. Chưa đo thời gian đến lần health đầu tiên qua HTTPS |
| Bộ nhớ sau restart | Working set **186,2 MiB**. Private memory **237,2 MiB** | Đo bằng PowerShell lúc 14:14:29, JVM PID 27140 khởi động lúc 14:11:39 |
| Wake sau khi host ngủ | Chưa kiểm thử | Lượt này dùng laptop và Quick Tunnel. Ca wake phụ thuộc môi trường triển khai |

Bằng chứng SP-010 dùng để đối chiếu ca restart trước đó. [Hồ sơ SP-010](https://github.com/nigPeter2503/Social_Pilot/blob/230c2a6/docs/evidence/sp-010-backend-restart-callback-2026-11-12.md)

## Tái lập môi trường

1. Checkout revision cần triển khai. Mở PowerShell tại thư mục `backend` của worktree. Dùng JDK 21, chạy `.\gradlew.bat test bootJar` và xác nhận có `build\libs\socialpilot-backend-0.1.0.jar`. Ghi commit và kết quả test.
2. Với lượt thử trên laptop, giữ backend chỉ lắng nghe tại loopback. Trong cửa sổ riêng chạy `cloudflared tunnel --url http://127.0.0.1:8080` và giữ tunnel mở trong toàn bộ ca kiểm thử. Với host đã có HTTPS, dùng hostname của host và cấu hình secret tại host. Xác nhận môi trường và trần chi trước khi đăng ký dịch vụ có phí. [Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
3. Tại app Facebook đã dùng cho SP-010, thêm `https://<hostname>/api/oauth/facebook/callback` vào **Valid OAuth Redirect URIs**. Đặt cùng giá trị cho `FACEBOOK_REDIRECT_URI` trong môi trường backend. Đặt `FACEBOOK_APP_ID`, `FACEBOOK_TARGET_PAGE_ID` và `FACEBOOK_GRAPH_API_VERSION` theo app, Page thử và revision. Nạp `FACEBOOK_APP_SECRET` từ nơi quản lý secret. Kiểm tra cấu hình trong `backend/.env.example` của revision đã checkout. Spring đọc biến của tiến trình.
4. Tại `backend`, chạy JAR và chờ `Started OAuthApplication`.

   ```powershell
   & "$env:JAVA_HOME\bin\java.exe" -jar '.\build\libs\socialpilot-backend-0.1.0.jar' --server.address=127.0.0.1 --server.port=8080
   ```

5. Trong cửa sổ khác, kiểm `https://<hostname>/api/health`. Kỳ vọng `status=UP` và `oauth=CONFIGURED`. Ghi giờ khởi động, giờ nhận health đầu tiên và revision. Để đọc RAM, dùng PID Java từ dòng khởi động của JAR hoặc PID đang lắng nghe cổng 8080. Ghi WorkingSet64 và PrivateMemorySize64 theo MiB và thời điểm lấy mẫu.
6. Ở thư mục `android`, đặt `ANDROID_HOME` thành đường dẫn SDK cài Platform 36 nếu worktree chưa có `local.properties`. Kết nối thiết bị qua USB, cấp quyền USB debugging, build và cài với đúng hostname.

   ```powershell
   .\gradlew.bat '-PsocialpilotOauthApiBaseUrl=https://<hostname>/' :app:installDebug
   ```

7. Mở SocialPilot, chọn **Kết nối Facebook Page**, dùng tài khoản thử đã được cấp quyền và xác nhận đúng Page đích. Ghi kết quả sau khi trình duyệt trả về app. Nếu có `stage=page_list code=ACCESS_DENIED`, so `FACEBOOK_TARGET_PAGE_ID` với Page được chọn và kiểm quyền tạo nội dung của tài khoản trên Page.

## Kiểm restart và wake

1. Bắt đầu một phiên OAuth từ app và dừng tại màn hình Facebook trước khi xác nhận. Giữ nguyên tunnel và hostname.
2. Dừng **backend** và chạy lại đúng JAR với cùng biến môi trường. Kiểm `/api/health` trả `UP` rồi hoàn tất màn hình Facebook đang mở. Ghi kết quả `INVALID_STATE`.
3. Mở lại app và bắt đầu một phiên mới. Hoàn tất xác nhận. Ghi toast hoặc trạng thái kết nối mới và Page đích. Tạo phiên mới nếu phiên trước đã mất.
4. Khi có host với cơ chế sleep, để dịch vụ đi vào trạng thái ngủ và ghi thời gian nhàn rỗi. Gọi health qua HTTPS để đánh thức dịch vụ, đo thời gian tới `UP`, sau đó kiểm callback OAuth với phiên mới. Lưu log, revision và điều kiện sleep của chính host.

Backend hiện giữ phiên OAuth tạm thời trong bộ nhớ một instance. Khi restart, phiên đang chờ hết hiệu lực. Client secret nằm phía backend và Android lưu credential nhận được trong kho mã hóa. Nếu triển khai nhiều instance, phải chốt cách chia sẻ phiên và TTL trước khi đưa lên host.

## Rollback và bàn giao

- Chọn JAR của revision đã kiểm chứng, giữ cấu hình môi trường phù hợp với revision đó và dùng lại hostname đã đăng ký. Khởi động JAR và gọi health qua HTTPS. Tạo phiên OAuth mới từ app rồi xác nhận callback thành công. Quy trình rollback ở trên chưa chạy trong lượt 19/11.
- Giao Thiện URL HTTPS của môi trường dùng chung và Gradle property cần đặt. Trong PR đính kèm bảng lệnh, revision và ảnh đã lược bỏ dữ liệu nhạy cảm. Reviewer dự kiến là Đạt và Thiện. Quick Tunnel là môi trường kiểm thử có URL tạm. Host ổn định, ca sleep/wake, phạm vi LinkedIn và lần thử rollback cần ghi trạng thái theo kết quả thực tế trên ticket.
