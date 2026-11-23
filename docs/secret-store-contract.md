# SP-027 — Kho bí mật Keystore và kiểm tra BYOK

Issue [#29](https://github.com/nigPeter2503/Social_Pilot/issues/29) · F8.B2 · Owner Nguyễn Đức Đạt (TV2). Bên tiêu thụ: Minh (SP-022, social token), Thiện (UI state), SP-028 (credential source cho adapter).

## 1. Phạm vi đã làm

- Kho bí mật dùng chung theo **namespace**: `AI_PROVIDER` cho key LLM, `SOCIAL_TOKEN` cho token mạng xã hội. Mỗi namespace có file SharedPreferences riêng (`secrets_ai_v1`, `secrets_social_v1`) và khóa AES-256-GCM riêng trong Android Keystore (`socialpilot.secret.ai.v1`, `socialpilot.secret.social.v1`). Associated data gồm namespace và tên record, nên ciphertext chép sang record hoặc namespace khác bị từ chối.
- Cơ chế mã hóa là `KeystoreRecordCipher`, tách nguyên trạng từ `EncryptedOAuthStore` (SP-010). Kho OAuth hiện tại giữ alias, file và hành vi cũ; không refactor luồng OAuth.
- Màn hình **Khóa API AI của bạn**: nhập/che (password toggle), lưu và kiểm tra, kiểm tra lại, xóa từng provider. Chuỗi VI/EN đầy đủ.
- Kiểm tra key gọi endpoint liệt kê model (không sinh token): OpenAI `GET https://api.openai.com/v1/models`, Gemini `GET https://generativelanguage.googleapis.com/v1beta/models?pageSize=1`.

## 2. Hợp đồng credential reference cho Minh (SP-022)

Gói `com.socialpilot.app.domain.contract.secret`. Dùng `AppContainer.socialSecrets` (namespace `SOCIAL_TOKEN`); kho này không dùng chung file/khóa với AI.

| API | Hành vi |
|---|---|
| `new SecretRef(SOCIAL_TOKEN, id)` | `id` khớp `[A-Za-z0-9._-]{1,64}`, nên dùng UUID sinh trên thiết bị. Không chứa token, tên Page hay external ID |
| `ref.opaque()` / `SecretRef.parse(text)` | Dạng `secret://social/<id>`. Đây là giá trị duy nhất được lưu trong Room (cột credential ref) |
| `write(ref, secret)` → `revision` | Thay thế nguyên tử. Lỗi mã hóa: `SecretStoreException(ENCRYPTION_FAILED)`, giá trị cũ giữ nguyên. Lỗi commit: `WRITE_FAILED`, reference trả `UNREADABLE` trong process cho đến lần ghi thành công; sau restart đọc lại giá trị đã bền vững trên đĩa |
| `read(ref)` | `SUCCESS` (value + revision), `MISSING` (chưa có hoặc đã xóa), `UNREADABLE` (mất/vô hiệu khóa Keystore, ciphertext hỏng, version lạ, ghi chưa xác nhận). Không ném lỗi, không tự xóa record |
| `delete(ref)` | Chỉ xóa reference đó; idempotent. Lỗi commit → `WRITE_FAILED` |

- `UNREADABLE` nghĩa là cần người dùng kết nối/nhập lại, **không** phải token bị provider thu hồi. Phân loại thu hồi/hết hạn vẫn thuộc SP-022.
- `revision` dùng để bỏ kết quả muộn gắn với giá trị cũ (ví dụ callback refresh token về sau khi đã reconnect).
- Mọi method làm I/O Keystore/đĩa, gọi trên worker. Không log value; `toString()` của `SecretRef`/`SecretRead` không chứa secret.
- Khi khóa Keystore bị vô hiệu vĩnh viễn, lần **ghi** kế tiếp tạo khóa mới cho namespace đó; các record cũ của namespace vốn đã `UNREADABLE`.

## 3. BYOK cho SP-028 và UI

`AiKeyRepository` (trong `AppContainer.aiKeys`) giữ key mỗi provider tại `secret://ai/llm.openai` và `secret://ai/llm.gemini`. File `ai_key_status_v1` chỉ lưu `{revision, state, checkedAt}`, không chứa key hay phần nào của key.

| Trạng thái key | Ý nghĩa | Cho phép gọi provider (`usable()`) |
|---|---|---|
| `NOT_SET` | Chưa nhập | Không |
| `UNVERIFIED` | Đã lưu, chưa có kết quả xác định cho đúng revision | Không |
| `VALID` | Provider trả danh sách model (200 + JSON đúng dạng) | Có |
| `INVALID` | 401, hoặc Gemini 400 `API_KEY_INVALID`/`API_KEY_EXPIRED`: sai hoặc đã thu hồi | Không |
| `PERMISSION_DENIED` | 403: key đúng nhưng thiếu quyền API/model/vùng | Không |
| `UNREADABLE` | Kho bảo mật không đọc được; hướng dẫn nhập lại hoặc xóa | Không |

Kết quả kiểm tra tạm thời **không** đổi trạng thái key và không bị báo là key sai: `RATE_LIMITED` (429), `PROVIDER_UNAVAILABLE` (5xx), `TIMEOUT`, `NETWORK_ERROR`, `UNEXPECTED_RESPONSE` (redirect, trang captive portal, 400 khác), `OFFLINE` (không gửi request), `CANCELLED`, `SUPERSEDED` (key đã đổi/xóa trong lúc kiểm tra).

- Chặn ghi đè cũ: kết quả chỉ được ghi khi revision của key lúc bắt đầu kiểm tra vẫn là revision hiện tại (kiểm trong cùng lock với ghi). ViewModel thêm generation theo provider; đổi key, xóa hoặc rời màn hình hủy HTTP Call đang chạy.
- `credentialSource(provider)` đọc key tại thời điểm dispatch: `SUCCESS` → key, `MISSING` → `null`, `UNREADABLE` → ném `IllegalStateException`. Adapter SP-013 đã map cả hai trường hợp thành `INVALID_CREDENTIAL` mà không gửi mạng. SP-028 phải kiểm `status(provider).usable()` trước khi cho dispatch production.
- Key chỉ nằm trong header của đúng provider (`Authorization: Bearer` cho OpenAI, `x-goog-api-key` cho Gemini), tới host HTTPS cố định, tắt redirect và retry kết nối. Không đi qua backend OAuth, URL query hay redirect.

## 4. Lưu trữ, backup, log và UI

- Manifest giữ `allowBackup=false`; `backup_rules.xml` và `data_extraction_rules.xml` loại mọi domain (kể cả `sharedpref`) cho cloud backup và device transfer. `BackupExclusionTest` khóa điều kiện này.
- Ô nhập dùng `textPassword|textNoSuggestions`, `importantForAutofill=no`, `saveEnabled=false` (key không vào saved-instance-state). Màn hình đặt `FLAG_SECURE`. Sau khi bấm lưu, ô nhập được xóa; UI chỉ thấy `••••` + 4 ký tự cuối khi key dài từ 12 ký tự.
- Không có log/`println` nào in key. Lỗi chỉ mang enum, không mang message provider.
- I/O, Keystore và mạng chạy trên `AppContainer.io`; kết quả về main thread qua `Handler`.

## 5. Kiểm thử

| Ca | Kết quả |
|---|---|
| `EncryptedSecretStoreTest` (12): round trip sau restart, không plaintext trên đĩa/tên record, revision mới mỗi lần ghi, lỗi mã hóa giữ key cũ, commit lỗi không trả giá trị chưa xác nhận, mất khóa Keystore → `UNREADABLE` rồi ghi lại được, ciphertext hỏng/version lạ, hoán đổi ciphertext, xóa đúng reference, cô lập namespace, parse reference | Chạy JVM, đạt |
| `ByokKeyVerifierTest` (12, MockWebServer): host/scheme production, header đúng provider và không có key trong URL/body, 401/Gemini 400 → sai key, 400 khác không bị coi là sai key, 403, 429/5xx/504, ngắt kết nối/timeout, không theo redirect, 200 không phải JSON provider, hủy giữa chừng | Chạy JVM, đạt |
| `AiKeyRepositoryTest` (15): lưu UNVERIFIED không gọi mạng, từ chối key sai định dạng, VALID bền sau restart, INVALID/PERMISSION_DENIED tách biệt, lỗi tạm giữ trạng thái cũ, offline không gửi request, kiểm tra cũ không đánh dấu key mới hợp lệ, hủy, xóa một provider không đụng provider/social token khác, lỗi kho giữ key cũ | Chạy JVM, đạt |
| `BackupExclusionTest` (3): manifest và hai file rule | Chạy JVM, đạt |
| `EncryptedSecretStoreDeviceTest` (3): Keystore thật, mất alias → `UNREADABLE` → ghi lại, namespace tách | Đạt trên emulator API 37, xem [bằng chứng](evidence/sp-027-device-check-2026-11-23.md). API 23 **NOT_RUN** |
| Luồng UI thủ công: lưu/kiểm tra/restart/offline/xóa với key giả | Đạt trên emulator API 37, cùng file bằng chứng |
| Key hợp lệ thật với OpenAI/Gemini (`VALID`) | **NOT_RUN** — chưa có key được cấp cho phép thử. Key giả đã được hai provider thật từ chối đúng (`INVALID`). Không gửi key qua PR/chat |

Lệnh: tại `android/` chạy `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; thiết bị/emulator: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.socialpilot.app.data.security.EncryptedSecretStoreDeviceTest`.

## 6. Còn mở

- Minh nối `socialSecrets` vào SP-022 và quyết định có chuyển `EncryptedOAuthStore` sang kho namespace hay không; PR này không đổi luồng OAuth.
- Room chưa có cột credential ref; khi Channel root được cấp lượt schema, cột chỉ lưu `SecretRef.opaque()`.
- SP-028 dùng `credentialSource` và `usable()`; dispatch production còn chờ ledger SP-029.
