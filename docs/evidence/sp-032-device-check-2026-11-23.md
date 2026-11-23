# SP-032 — Kiểm tra template ảnh trên emulator, 23/11/2026

- Thực hiện: Nguyễn Đức Đạt (TV2), khoảng 17:00 +07:00.
- Nhánh `feat/sp-032-channel-images-templates` (xếp trên `feat/sp-031-channel-agent-pipeline` `04ddf3e`), mã nguồn đúng nội dung commit đầu tiên của nhánh.
- Môi trường: JDK 21 (JBR Android Studio), Android SDK 36; emulator `Pixel_9_Pro_XL`, Android 17 (API 37).
- Không có lượt gọi API ảnh nào: registry chỉ có ứng viên ảnh chưa xác minh.

## Test thiết bị

`.\gradlew.bat :app:connectedDebugAndroidTest`: 10 test, 0 lỗi, gồm `TemplateRendererDeviceTest`:

| Test | Kết quả |
|---|---|
| `bothChannelsRenderDistinctTemplatesAtTheirSpecSize`: `BitmapTemplateRenderer` thật, decode lại bằng `BitmapFactory`, kiểm kích thước và màu nền từng kênh | Pass |
| `serviceTemplatePathStoresAPrivateReference`: `ImageService.generate` khi chưa chọn model → template `NO_MODEL_SELECTED`, ảnh 1080 × 1350 lưu dưới ref trong thư mục riêng của app | Pass |

## Ảnh template do thiết bị tạo

Chạy lại riêng test trên bằng `am instrument`, rồi lấy file bằng `adb exec-out run-as … cat`. Đây là ảnh template cục bộ (fixture tiêu đề tiếng Việt), không phải ảnh sinh bởi API.

| Kênh | Kích thước | File |
|---|---|---|
| Facebook | 1080 × 1080 (provisional) | [sp-032-template-facebook-2026-11-23.png](sp-032-template-facebook-2026-11-23.png) |
| Instagram | 1080 × 1350 (provisional) | [sp-032-template-instagram-2026-11-23.png](sp-032-template-instagram-2026-11-23.png) |

Kiểm bằng mắt: dấu tiếng Việt hiển thị đúng, chữ không tràn lề, màu và tỉ lệ khác nhau giữa hai kênh.

Chưa chạy: API ảnh thật (NOT_RUN, chưa có model/size/giá theo ảnh được xác minh và chưa được cấp quyền thử); API 23.
