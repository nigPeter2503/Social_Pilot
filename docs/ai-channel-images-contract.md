# SP-032 — Ảnh theo kênh và template dự phòng

Issue [#34](https://github.com/nigPeter2503/Social_Pilot/issues/34) · F2.B3, F8.B3, F8.B4 · Owner Nguyễn Đức Đạt (TV2). Phụ thuộc SP-028 (lựa chọn model/BYOK), SP-029 (ledger). Bên nhận: Thiện (gắn ảnh vào version), Minh SP-025 (publish), pipeline SP-031 tuần 4.

## 1. Cấu hình theo kênh

`ImageSpec.defaults(platform)`:

| Kênh | Kích thước template | Phong cách sinh ảnh | Màu template |
|---|---|---|---|
| Facebook | 1080 × 1080 | ảnh vuông gọn cho bài Page, không chữ trong ảnh | xanh đậm → xanh, vạch vàng |
| Instagram | 1080 × 1350 | ảnh feed dọc, chủ thể nổi bật, không chữ trong ảnh | tím → cam, vạch trắng |

- **Tất cả là `PROVISIONAL`.** Repo chưa có thông số nền tảng đã chốt; các số trên chỉ để template chạy. `ImageAsset.specVerified = false` đi theo mọi ảnh để SP-025 chặn hoặc xác nhận trước khi đăng.
- `providerSize` (kích thước gửi API ảnh) mặc định là `null` vì chưa có size được xác minh cho model nào. Khi `null`, service không gọi API và dùng template (`IMAGE_SIZE_UNVERIFIED`).

## 2. Luồng `ImageService`

```text
generate(request) → model IMAGE đã chọn → blockFor(IMAGE): capability image SUPPORTED, VERIFIED, có giá perImage
  → provider có adapter → key BYOK usable → spec có providerSize → ledger có trần
  → đăng ký job đang chạy → reserve(perImage × 1) → nếu bị dừng vì vượt trần ngay sau reserve thì không gửi, release một lần, FAILED + budgetStopped
  → một lần gọi API → AttemptLedger.complete(...): quyết toán và usage trong cùng transaction, kỳ ghim lúc reserve; lỗi ghi → Result.ledgerWriteFailed, settlement null, khoản giữ vẫn tính
  → PNG hợp lệ → kiểm revision kênh → lưu file → kiểm revision lại → ImageAsset(GENERATED)
```

- **Không gọi được (trước dispatch) → template ngay**, không tốn phí, ghi `fallbackReason` (`NO_MODEL_SELECTED`, `MODEL_BLOCKED:<block>`, `KEY_NOT_USABLE`, `IMAGE_SIZE_UNVERIFIED`, `BUDGET_NOT_SET`, `BUDGET_LIMIT_REACHED`...). Capability `UNKNOWN` hay thiếu giá theo ảnh không bao giờ được coi là hỗ trợ.
- Khi provider báo lỗi và xác nhận chưa dispatch, service release khoản giữ rồi dùng template. Lỗi tải credential ghi `KEY_NOT_USABLE`, lỗi chuẩn bị khác ghi `PROVIDER_PRE_DISPATCH_FAILED`. Result giữ request ID và trạng thái quyết toán của lượt sinh; asset template chỉ mang template ID. Nếu ghi ledger thất bại, Result báo `ledgerWriteFailed` và khoản giữ vẫn được theo dõi.
- **Lỗi provider có thể đã dispatch → `FAILED`**, reservation giữ `UNCERTAIN`. Người dùng/pipeline có thể gọi `template()` bằng thao tác riêng. Text đã sinh được giữ nguyên.
- **Chi phí riêng khi không có token:** `PriceSheet.perImage` (đơn vị tiền/ảnh), `AiCostEstimate.image()`. Token usage nếu provider trả vẫn được lưu nhưng không dùng để tính tiền.
- **Hủy:** trước khi chạy → không gửi. Trong lúc chạy → hủy HTTP Call. Nếu ảnh vẫn về thì vẫn quyết toán (đã bị tính phí) nhưng **không lưu file**. Vượt trần ngân sách giữa chừng → `FAILED`, `budgetStopped = true`.
- **Revision:** kiểm `RevisionCheck` trước và sau khi lưu; nếu kênh đã ngắt/đổi revision thì xóa file và trả `STALE`, không bao giờ thêm ảnh vào kênh cũ. Bản trong `AppContainer` tạm dùng `CALLER_REVISION` (tin revision của caller) cho tới khi nối kết nối kênh SP-022.

## 3. Lưu trữ

- `ImageStore` trong `files/ai_images` (thư mục riêng của app, đã loại khỏi backup). Ghi file tạm `.tmp`, `fsync`, rồi rename; crash không để lại ảnh dở dưới một reference hợp lệ.
- Reference là tên mờ `img_<uuid>.png`; `file(ref)` từ chối mọi chuỗi khác (không path traversal, không absolute path).
- `ImageAsset` chỉ có: channel + revision, loại (`GENERATED`/`TEMPLATE`), `ref`, MIME, kích thước, số byte, SHA-256, `specVerified`, `requestId` (chỉ ảnh sinh), `templateId`, `fallbackReason`. Không URL ký tạm, không credential. Adapter OpenAI từ chối phản hồi chỉ có URL.
- **Xóa theo reference:** dùng một `ImageStore` cho mỗi thư mục trong suốt vòng đời process. `cleanup(referenced)` chỉ xét file đã có lúc mở store, nên file đang ghi hoặc vừa trả cho editor được giữ đến lượt dọn sau khi mở lại app. Khi nối SP-036, lấy tập ref của mọi version/kênh và phối hợp việc đổi ref cũ với lượt dọn. Lượt bị hủy hoặc stale xóa file do chính lượt đó tạo.
- Gắn ảnh vào version tạo version mới chưa duyệt theo contract SP-036 (approval cũ mất hiệu lực); việc này thuộc Thiện.

## 4. Adapter và registry

- `OpenAiImageProvider`: `POST /v1/images/generations` với `model`, `prompt`, `size`, `n = 1`; đọc `data[0].b64_json`. Định dạng wire chỉ mới kiểm bằng MockWebServer. Giới hạn body 40 MB, không theo redirect, không retry.
- Registry thêm ứng viên `gpt-image-2.5-flare` (`IMAGE`), `UNVERIFIED`, capability `UNKNOWN`, `price = null`, cần xác minh model/size/định dạng phản hồi/giá theo ảnh theo nguồn ghi trong file. Hiện mọi đường sinh ảnh đều bị chặn nên app luôn dùng template.
- Màn hình AI hiện có thêm dòng chọn model cho "Ảnh theo kênh".

## 5. Kiểm thử

| Test | Nội dung | Kết quả |
|---|---|---|
| `ImageServiceTest` (provider/renderer giả có nhãn, store thật trên thư mục tạm, `RecordingBudget`) | Template khi chưa có model/bị chặn (không reserve); capability UNKNOWN/thiếu giá ảnh bị chặn; sinh ảnh reserve 40 000 micros, settle một lần, chỉ lưu ref; lỗi sau dispatch giữ uncertain và không tự fallback; lỗi trước dispatch release và dùng template; hết trần/thiếu size/chưa đặt trần → template; hủy giữa chừng vẫn tính phí nhưng không file; vượt trần dừng job; revision cũ → `STALE` không file; hai kênh khác spec; template hỏng không lưu; dừng vì trần giữa reserve và dispatch không gửi và release một lần; lỗi ghi ledger báo `ledgerWriteFailed`, không hiện là đã quyết toán; usage dùng kỳ ledger ghim qua nửa đêm | Pass |
| `ImageStoreAndProviderTest` | Commit nguyên tử, cleanup giữ ref đang dùng, từ chối path lạ; PNG header; layout co chữ/xuống dòng/cắt "…"; MockWebServer: request đúng size/n/model/Bearer, base64 → bytes, URL-only/malformed → `PROTOCOL_ERROR`, HTTP 401/403/429/500/400, key sai không gửi, hủy Call | Pass |
| `TemplateRendererDeviceTest` (emulator) | Canvas thật: hai kênh đúng kích thước và màu; template qua service lưu ref trong thư mục riêng | Pass, xem [evidence](evidence/sp-032-device-check-2026-11-23.md) |
| API ảnh thật | **NOT_RUN**: chưa có model ảnh đã xác minh, size, giá theo ảnh, key/quota/trần thử | — |
| Alt-text nghiệp vụ | Thuộc SP-039 của Thiện, chưa làm ở đây | — |
