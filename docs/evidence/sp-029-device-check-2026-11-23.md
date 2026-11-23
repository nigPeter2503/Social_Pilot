# SP-029 — Kiểm tra trên emulator, 23/11/2026

- Thực hiện: Nguyễn Đức Đạt (TV2), khoảng 16:30 +07:00.
- Nhánh `feat/sp-029-atomic-ai-budget` (xếp trên `feat/sp-028-ai-model-usage-integration` `5711e31`), mã nguồn đúng nội dung commit đầu tiên của nhánh.
- Môi trường: JDK 21 (JBR Android Studio), Android SDK 36; emulator `Pixel_9_Pro_XL`, Android 17 (API 37), zone thiết bị `Asia/Bangkok`.
- Không có lượt gọi AI nào được gửi: registry chỉ có ứng viên chưa xác minh (`MODEL_BLOCKED`).

## Test thiết bị

`.\gradlew.bat :app:connectedDebugAndroidTest`: 8 test, 0 lỗi. Trong đó `LedgerDatabaseDeviceTest` (Room thật, file DB tạm):

| Test | Kết quả |
|---|---|
| `freshInstallHasNoLimitsAndAdmitsNothing` | Pass |
| `concurrentReservesFromTwoLedgersNeverPassTheCap` | Pass |
| `reopeningAfterAProcessDiedKeepsReservationsHeld` | Pass |

## Kiểm tra tay màn hình AI

| Bước | Kỳ vọng | Thực tế (đọc bằng `uiautomator dump`; màn hình có `FLAG_SECURE` nên không chụp ảnh) |
|---|---|---|
| Mở **AI cost caps** lần đầu | Chưa có trần, mọi lượt bị chặn | "No caps set. Every AI call is blocked until you set a daily and a monthly cap." |
| Nhập ngày 10, tháng 5, lưu | Từ chối, không lưu | Trạng thái vẫn "No caps set…" (toast không có trong dump) |
| Nhập ngày 2.5, tháng 30, lưu | Lưu và hiển thị chi tiêu | "Today 2026-11-23: used/held 0 / 2.5 USD, 2.5 left. Month 2026-11: used/held 0 / 30 USD, 30 left. Time zone: Asia/Bangkok · attempts with unknown cost: 0" |
| `am force-stop` rồi mở lại | Trần còn | Cùng nội dung như trên; ô nhập điền sẵn 2.5 và 30 |
| `run-as … ls databases` | Ledger nằm file riêng | `ai_ledger.db` (+ `-shm`, `-wal`) bên cạnh `socialpilot.db` |
| **Estimated cost** | Ledger đã mở, chưa có lượt | "No AI calls in this period." |

Chưa chạy: lượt AI thật qua ledger (NOT_RUN, chưa có model đã xác minh, key/quota/trần thử được cấp); API 23; vượt trần với provider thật.
