# Bằng chứng kiểm thử SP-024

[Issue #26](https://github.com/nigPeter2503/Social_Pilot/issues/26) · [PR #108](https://github.com/nigPeter2503/Social_Pilot/pull/108)

Ngày 27/11/2026, chạy trên AVD `sp024_api36` (Android API 36, x86_64, ngôn ngữ VI) với mã và bộ kiểm thử tại `b736181b6c6d635e0d46230f01b6e77124a8b8d0`. Tài khoản Facebook Page và Instagram là dữ liệu giả. Các ca dùng điểm chặn Graph kiểm thử và không gửi bài thật.

| Ca | Kết quả |
|---|---|
| Đóng hộp thoại bằng Hủy | Hai kênh giữ nguyên credential. |
| Xác nhận ngắt Instagram khi Facebook Page còn kết nối | Credential Instagram không còn dùng được. Facebook Page vẫn kết nối. Mở lại màn hình vẫn thấy đúng hai trạng thái. |
| Mở lại Room sau khi ngắt | Kênh B và bản nháp còn nguyên. Kết quả OAuth cũ không khôi phục kênh A. |
| Kết nối lại khi lượt dọn còn dang dở | App chặn kết nối lại khi lượt dọn vẫn lỗi. Sau khi thử lại và dọn thành công, kết nối mới dùng revision cao hơn. |
| Xóa ngay dấu vết | Dấu vết và chi tiết gửi cũ được dọn. Kết nối lại vẫn yêu cầu người dùng kiểm tra bài. |
| Dấu vết chạm mốc 30 ngày | Ngay trước mốc, phiên bản cũ vẫn bị chặn. Tại mốc, cả dọn lúc mở app và dọn lúc bấm đăng đều xóa dấu vết, yêu cầu xác nhận lại và chặn Graph trong lúc chờ. |

**Kết quả chạy:** 31/31 ca trên emulator. Build Android, 283 unit test, lint và build APK kiểm thử đạt. Các điểm tích hợp queue production, sync/auto-reply, editor/ảnh và checkpoint Room được kiểm lại trong SP-052 khi đủ đầu vào.

## Ảnh UI

- [Hộp thoại xác nhận, có tùy chọn xóa ngay dấu vết](confirm-disconnect.png)
- [Sau khi ngắt Instagram, Facebook Page vẫn kết nối](after-disconnect.png)
