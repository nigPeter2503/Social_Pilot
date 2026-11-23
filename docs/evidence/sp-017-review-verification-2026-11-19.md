# SP-017 — Kiểm chứng đối soát WorkManager, 19/11/2026

## Phản hồi P2 trong review PR #70

`reconcileWorkOnOpen` có thể đọc hàng Room khi checkpoint còn `NOT_STARTED`, sau đó Worker ghi checkpoint rồi WorkManager trả `SUCCEEDED`. Bản trước dùng ảnh chụp cũ để đánh dấu enqueue lỗi. Khi WorkManager dọn WorkSpec của job đã hoàn tất, `getWorkInfoById` trả `null` và cũng có thể báo lỗi nhầm. Checkpoint từ job cũ không đủ để xác định job mới đã hoàn tất.

Room v4 lưu `completedWorkRequestId` trong cùng câu `UPDATE` ghi checkpoint. Câu `UPDATE` báo enqueue lỗi chỉ tác động nếu `runId` và `workRequestId` vẫn khớp, row còn `COMPLETE`, và UUID này chưa được ghi hoàn tất. Điều kiện được kiểm tra tại thời điểm ghi, nên ảnh chụp Room cũ không làm mất kết quả vừa được Worker ghi. Nếu một UUID mới không có WorkSpec, checkpoint cũ vẫn được giữ và cờ lỗi được bật để xếp lại.

Migration v1→v4, v2→v4 và v3→v4 giữ dữ liệu cũ. Hàng hoàn tất trước v4 không có định danh job hoàn tất; sau khi WorkSpec đã bị dọn, ứng dụng không thể suy ra chính xác checkpoint thuộc UUID nào. Trường hợp này hiển thị lỗi cần xếp lại job mẫu nhưng giữ checkpoint đã có. Job hoàn tất từ v4 được bảo vệ theo UUID.

## Kết quả kiểm tra

| Kiểm tra | Kết quả |
| --- | --- |
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` | Đạt; 5 JVM test, lint 0 lỗi/13 cảnh báo. |
| Worker hoàn tất sau ảnh chụp đối soát, rồi WorkSpec không còn | Test xác nhận cờ lỗi không bật, checkpoint và UUID hoàn tất được giữ. |
| Job cũ có checkpoint, UUID job mới không có WorkSpec | Test xác nhận chỉ job mới báo lỗi, checkpoint cũ vẫn được giữ. |
| Migration v1/v2/v3 lên v4 | Test xác nhận text, checkpoint, run/job và cờ lỗi cũ được giữ theo từng phiên bản. |
| `:app:connectedDebugAndroidTest` trên AVD API 36 và API 23 | Đạt 10/10 ca trên mỗi AVD. |
