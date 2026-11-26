# SP-028 — Kiểm tra trên emulator, 23/11/2026

- Thực hiện: Nguyễn Đức Đạt (TV2), khoảng 17:00 +07:00.
- Nhánh `feat/sp-028-ai-model-usage-integration` (xếp trên `feat/sp-027-secure-byok-store` `d62b834`), mã nguồn đúng nội dung commit đầu tiên của nhánh.
- Môi trường: JDK 21.0.10 (JBR Android Studio), Android SDK 36; emulator `Pixel_9_Pro_XL`, Android 17 (API 37).
- Không có lượt gọi AI nào được gửi: dispatch production đang khóa (`BUDGET_NOT_CONFIGURED`).

| Bước | Kỳ vọng | Thực tế (đọc bằng `uiautomator dump`) |
|---|---|---|
| Mở màn hình AI, cuộn tới **Model per task** | Thông báo khóa dispatch và revision registry | "AI calls are locked until shared budget control is enabled…" + "Model registry: 2026-11-23-candidates-unverified" |
| Bốn tác vụ text | Mỗi tác vụ "Not selected"; không có dòng ảnh | Đúng (viết nội dung, phân loại, gợi ý trả lời, báo cáo) |
| Mở dropdown **Write and edit content** | Hai ứng viên từ registry | "OpenAI · gpt-4.1-mini", "Google Gemini · gemini-2.5-flash" |
| Chọn Gemini | Lưu lựa chọn, báo vì sao chưa dùng được | "Streaming text support is not confirmed; the model cannot be used yet." |
| `am force-stop` rồi mở lại | Lựa chọn còn | Vẫn "Google Gemini · gemini-2.5-flash" và cùng lý do chặn |
| `ai_model_selection_v1.xml` | Chỉ có lựa chọn, không key | `task.CONTENT_TEXT = GEMINI|gemini-2.5-flash` |
| **Estimated cost** | Chưa có ledger | "No usage ledger yet. No AI call has been made." |

Chưa chạy: lượt text thật OpenAI/Gemini (NOT_RUN, chưa được cấp quyền thử và chưa có ledger); API 23.
