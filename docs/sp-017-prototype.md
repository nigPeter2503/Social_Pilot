# SP-017 — Bản mẫu UI, Room và WorkManager

## Phạm vi

Màn hình “Bản mẫu hai luồng” mở từ màn hình topic. A và B là hai luồng **dữ liệu giả** cho Facebook và LinkedIn; không gọi AI, OAuth, API mạng xã hội hoặc queue đăng bài. Mỗi luồng có bắt đầu, hủy, tiến độ và kết quả riêng. Nút “Gây lỗi” trên A cho phép xem trạng thái lỗi và thử lại.

`DemoStreamCoordinator` dùng generation ID để bỏ callback đến sau khi hủy, lỗi hoặc khởi chạy lại. Bắt đầu lượt mới hoặc bấm Hủy sẽ hủy unique WorkManager job mẫu đang chờ của cùng kênh; nếu job đã hoàn tất, checkpoint đã ghi không được rollback. `PrototypeViewModel` sống qua xoay màn hình nên không khởi tạo lại luồng. Nếu process chết, hàng `RUNNING` trong Room được đổi thành `INTERRUPTED` khi mở lại; chỉ thao tác Bắt đầu của người dùng mới tạo luồng mẫu mới.

Kết quả mẫu nằm trong Room database `socialpilot-prototype.db`, tách khỏi `socialpilot.db` của topic v1. Không sửa schema topic và không dùng migration phá hủy. `SampleCheckpointWorker` chỉ tính SHA-256 cho kết quả mẫu đã hoàn tất và lưu checkpoint vào Room. WorkRequest có ràng buộc `NetworkType.CONNECTED`; đây là công việc mẫu hữu hạn, **không phải job AI nền hay queue đăng thật**. Worker trả thành công sau khi checkpoint được ghi. Sau force-stop, thời điểm hệ điều hành cho Worker chạy lại không được bảo đảm.

SP-012 vẫn cần chốt schema chung; SP-014 cần bàn giao runtime hai luồng trước khi thay luồng giả bằng adapter thật. Payload mẫu lấy ý tưởng từ SP-016 nhưng không import contract chưa merge. Những điểm này là phụ thuộc tích hợp, không được báo là API thật đã đạt.

## Kịch bản demo cho Minh

1. Build và cài APK debug. Mở SocialPilot, chọn **Mở bản mẫu UI · Room · Worker**. Chỉ ra nhãn “Dữ liệu mẫu”.
2. Bấm Bắt đầu A và B, hủy A, xoay màn hình rồi xoay về. A ở trạng thái đã hủy, B vẫn hoàn tất; mở lại màn hình để thấy kết quả B từ Room.
3. Bấm Bắt đầu B, dừng process trước khi kết quả hoàn tất, rồi mở lại. B hiển thị gián đoạn và không tự chạy lại.
4. Bấm Bắt đầu A, chờ hoàn tất. Tắt Wi-Fi và dữ liệu di động trên emulator, xếp job kiểm tra mẫu. Job chờ, checkpoint chưa ghi. Bật mạng; job hoàn tất và checkpoint được lưu. Đóng/mở app để kiểm tra trạng thái còn đó.
5. Bấm Bắt đầu A rồi Gây lỗi; B vẫn giữ kết quả. Bấm Bắt đầu A để thử lại. Không giới thiệu những thao tác này như AI/API thật.

## Kiểm chứng ngày 11/11/2026

Máy kiểm tra: Windows, Temurin JDK 21.0.13, Android SDK Platform 36, Build Tools 36.0.0. Bộ build cục bộ dùng Gradle Wrapper và init script tạm để đặt output ngoài OneDrive do thư mục kết quả test cũ bị OneDrive khóa. Init script không được đưa vào repo; CI chạy workflow chuẩn.

| Lệnh / ca thử | Môi trường | Kỳ vọng | Thực tế | Bằng chứng |
| --- | --- | --- | --- | --- |
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | JDK 21, SDK 36 | APK build, unit test và lint đạt | `BUILD SUCCESSFUL`; 5 unit test đạt, lint 0 lỗi, 13 cảnh báo | [Log thiết bị và build](evidence/sp-017-device-check-2026-11-11.txt) |
| `:app:connectedDebugAndroidTest` | AVD `SocialPilot_API23`, Android 6.0 / API 23 | Room giữ dữ liệu; Worker chờ mạng, ghi checkpoint và hủy job đang chờ | 2/2 instrumented test đạt | [Log thiết bị và build](evidence/sp-017-device-check-2026-11-11.txt) |
| `:app:connectedDebugAndroidTest` | AVD `SocialPilot_API36`, Android 16 / API 36 | Cùng ca Room và Worker đạt trên API 36 | 2/2 instrumented test đạt | [Log thiết bị và build](evidence/sp-017-device-check-2026-11-11.txt) |
| Bắt đầu A/B, hủy A, xoay và mở lại | API 23 và API 36 | A không nhận callback muộn; B vẫn hoàn tất và còn sau mở lại | Đạt trên cả hai AVD | [Video API 23](evidence/sp-017-api23-demo.mp4) · [Ảnh API 23](evidence/sp-017-api23-cancel-a-b-complete.png) · [Ảnh API 36](evidence/sp-017-api36-cancel-a-b-complete.png) |
| Dừng process khi B đang chạy, mở lại | API 23 | B hiển thị gián đoạn, không tự tạo kết quả mới | Đạt; A và checkpoint đã lưu vẫn còn | [Ảnh sau mở lại](evidence/sp-017-api23-interrupted.png) |
| Tắt Wi-Fi/data, xếp job, bật data | API 23 | Job chờ khi offline, sau đó hoàn tất và lưu checkpoint | Đạt | [Ảnh sau khi job hoàn tất](evidence/sp-017-api23-worker-done.png) |
| Gây lỗi A và thử lại | API 36 | Lỗi A không xóa B; A thử lại được | Đạt qua UI dump | [Log thiết bị và build](evidence/sp-017-device-check-2026-11-11.txt) |

Chưa thử trên điện thoại thật trong SP-017. Chưa tích hợp runtime SP-014, schema chung SP-012, AI thật, queue đăng bài, hoặc hành vi Worker chạy ngay sau force-stop. CI của commit bàn giao phải được dẫn riêng trên PR sau khi push.
