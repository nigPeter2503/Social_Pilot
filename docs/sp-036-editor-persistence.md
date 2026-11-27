# SP-036 — Persistence cho version và approval

Nhánh này tiếp nối SP-035/PR #82; schema v3 trong PR đó là đầu vào cho migration v3→v4. Chưa mở đường đăng hoặc tạo AI trực tiếp từ Activity.

## Schema và quy tắc

`brief_targets` giữ `currentVersionId` và `approvedVersionId` riêng cho từng cặp `(briefId, channelId)`. `content_versions` lưu version bất biến với text, image reference, parent, `briefUpdatedAt` nguồn và thời điểm tạo. FK composite giữ version trong đúng link brief/kênh. `version_approvals` lưu dấu đã từng duyệt để lịch sử không biến bản cũ thành bản có thể đăng.

`EditorDao.saveVersion` và `restoreVersion` chạy transaction: kiểm target, revision brief, expected current, parent cùng brief/kênh, thêm version rồi chuyển current và xóa approval. Kết quả AI đến muộn không qua expected-current guard. `approve` chỉ nhận version hiện hành của đúng link; `requireEnqueueable` chỉ trả payload khi version vừa là current vừa là approved. Queue production vẫn phải lặp lại kiểm tra trong transaction admission/dispatch của chính queue; một lần đọc ở editor chưa đủ để chặn sửa sau đó.

## Bằng chứng và phần chưa đạt

- Instrumentation kiểm sửa Facebook không xóa approval LinkedIn, callback muộn bị từ chối, version sai kênh bị từ chối, restore tạo version mới chưa duyệt, v3→v4 và restart vẫn giữ approval.
- Ngày 19/11/2026: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` đạt với 63 JVM test, lint 0 lỗi; `:app:connectedDebugAndroidTest` đạt 9/9 trên AVD API 23 và 9/9 trên AVD API 36. Bộ test gồm cả ca SP-035 để xác nhận đường nâng v1→v2→v3→v4 và UI brief không hồi quy.
- Editor/ViewModel/preview và giới hạn text theo capability đã xác minh còn thiếu. Picker kênh thật đã có trong PR #82 dựa trên SP-022; phần editor chưa nối vào picker. Không dùng số giới hạn tự đặt hoặc fixture làm bằng chứng đăng thật. SP-026 queue và SP-031 output DTO chưa được tích hợp; resolver hiện cung cấp điều kiện cần cho Minh, chưa chứng minh admission online/offline.

Ngày 26/11/2026: đồng bộ SP-022 và SP-035; Version/approval chuyển sang migration v3→v4. Android assemble/unit/lint đạt; 7/7 ca migration và persistence trên AVD API 36 đạt. PR #83 vẫn phụ thuộc PR #82 và PR #96 theo thứ tự.
