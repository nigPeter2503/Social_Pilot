# SP-051 — Ngôn ngữ ứng dụng và trạng thái mạng

Ứng dụng có bộ chọn VI/EN trong màn hình chính. Lựa chọn dùng `AppCompatDelegate.setApplicationLocales` và `AppLocalesMetadataHolderService` với `autoStoreLocales`, theo [tài liệu AndroidX](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate#setApplicationLocales(androidx.core.os.LocaleListCompat)). Nhờ vậy app locale lưu qua restart trên API <33 và API 33+ dùng cơ chế hệ thống. Bộ chọn ngôn ngữ nội dung của brief là state riêng; đổi UI locale không đổi payload `Brief.contentLanguage`.

`MainActivity` nghe thay đổi network khi ở foreground và cập nhật thông báo online/offline. Form brief vẫn ghi Room và đọc cache local khi offline; màn hình chưa có thao tác AI/publish/reply để tự chạy lại khi mạng trở về. Callback mạng được gỡ khi Activity dừng để tránh giữ Activity cũ.

## Kiểm chứng và handoff

- Instrumentation đổi app locale đối diện locale hiện tại, xác nhận chuỗi UI đổi ngôn ngữ, content language đã chọn không đổi qua tái tạo Activity; chạy trên API 23/36.
- Build, JVM, lint và instrumentation trên AVD API 23/36 được ghi theo commit trên PR. Chưa thử trên điện thoại thật.
- Queue state, cache analytics/comment và lần sync thành công cần dữ liệu thật từ SP-026 (Minh) và module Nghị. Không hiển thị fixture như job production; không đóng ca enqueue offline/restart của SP-052 bằng bản framework này. Các màn hình khác vẫn do owner module thêm resource VI/EN và cache tương ứng.
