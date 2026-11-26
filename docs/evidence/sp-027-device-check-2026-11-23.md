# SP-027 — Kiểm tra trên emulator, 23/11/2026

- Thực hiện: Nguyễn Đức Đạt (TV2). Thời điểm: 2026-11-23 khoảng 16:00–16:15 +07:00.
- Nhánh `feat/sp-027-secure-byok-store`, base `main` `ac38888`, mã nguồn chưa commit tại lúc chạy (đúng nội dung của commit đầu tiên trên nhánh).
- Môi trường: Windows 11, JDK 21.0.10 (JBR của Android Studio), Android SDK 36; emulator AVD `Pixel_9_Pro_XL`, system image Android 17 (API 37). Chưa có thiết bị API 23.
- Key dùng thử là chuỗi giả do người thực hiện tự gõ (`sk-fake-sp027-…`, `AIzaFakeSp027…`), không phải credential thật. Không có key thật nào được dùng hoặc ghi lại.

## Instrumentation

Lệnh tại `android/`: `.\gradlew.bat :app:connectedDebugAndroidTest`. Kết quả: 5/5 đạt, 0 lỗi.

| Test | Kết quả |
|---|---|
| `EncryptedSecretStoreDeviceTest.keystoreRoundTripSurvivesNewInstanceAndStoresNoPlaintext` | Đạt |
| `EncryptedSecretStoreDeviceTest.deletedKeystoreKeyGivesUnreadableThenRecoversOnWrite` | Đạt |
| `EncryptedSecretStoreDeviceTest.socialNamespaceIsSeparate` | Đạt |
| `EncryptedOAuthStoreDeviceTest` (2 ca có sẵn, hồi quy sau khi tách `KeystoreRecordCipher`) | Đạt |

## Luồng UI thủ công (đọc bằng `uiautomator dump`)

Màn hình đặt `FLAG_SECURE`, nên không chụp ảnh màn hình; trạng thái được đọc từ cây view.

| Bước | Kỳ vọng | Thực tế |
|---|---|---|
| Mở **Configure AI keys (BYOK)** | Hai thẻ OpenAI/Gemini, "No key set." | Đúng |
| Nhập key OpenAI giả, **Save and check** | Ô nhập được xóa; trạng thái "Checking…", sau đó provider từ chối | "Key ••••0000 was rejected: wrong or revoked." (1 request `GET /v1/models` tới `api.openai.com`) |
| `am force-stop` rồi mở lại | Trạng thái giữ sau restart | Giữ nguyên trạng thái bị từ chối với hint `••••0000` |
| Bật airplane mode, lưu key Gemini giả | Lưu được, không gửi request, báo offline | "saved, not yet confirmed" + "The device is offline…" |
| Tắt airplane mode, **Check again** Gemini | Provider từ chối key giả | "Key ••••0000 was rejected: wrong or revoked." (1 request tới `generativelanguage.googleapis.com`) |
| **Delete key** OpenAI | Chỉ xóa OpenAI | OpenAI "No key set."; `secrets_ai_v1.xml` chỉ còn `secret.llm.gemini`, status chỉ còn `status.GEMINI` |
| `run-as … grep -rl` hai chuỗi key trong toàn bộ dữ liệu app | Không thấy plaintext | Không có kết quả (exit 1) |
| `ai_key_status_v1.xml` | Chỉ có revision/state/checkedAt | `{"checkedAt":…,"revision":"…","state":"INVALID"}` |

## Chưa chạy

- Key hợp lệ thật cho OpenAI/Gemini (trạng thái `VALID`): **NOT_RUN**, chưa được cấp key/quota cho phép thử.
- Thiết bị/emulator API 23: **NOT_RUN**.
- Phân biệt key bị thu hồi với key sai trên provider thật: cả hai đều là 401/`API_KEY_INVALID`, chỉ kiểm bằng fixture.
