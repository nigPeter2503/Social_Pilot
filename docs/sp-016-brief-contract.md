# SP-016 — Hợp đồng brief (bản đề xuất)

Brief là đầu vào chung để tạo nội dung cho Facebook và LinkedIn. Đây là hợp đồng đề xuất để review với Đạt và Minh; chưa thay đổi schema Room hoặc interface dùng chung.

| Trường | Kiểu | Quy tắc |
| --- | --- | --- |
| briefId | String | ID của brief; định dạng cuối cùng chờ quy ước SP-012 |
| topic | String | Bắt buộc, không được rỗng |
| goal | String | Mục tiêu của bài viết, bắt buộc |
| audience | String | Đối tượng đọc, bắt buộc |
| tone | String | Giọng văn, bắt buộc |
| language | String | Chỉ nhận `vi` hoặc `en` |
| targets | Danh sách kênh | Mỗi kênh có `channelId` nội bộ và `platform` (FACEBOOK/LINKEDIN); không được rỗng |
| sources | Danh sách nguồn | Có thể rỗng; nguồn có loại và giá trị |

## Payload mẫu gửi cho phần tạo nội dung

Dữ liệu sau là ví dụ giả, không chứa token hoặc dữ liệu người dùng thật.

~~~json
{
  "briefId": "brief-demo-001",
  "topic": "Workshop kỹ năng số cho sinh viên",
  "goal": "Giới thiệu sự kiện và mời sinh viên đăng ký",
  "audience": "Sinh viên năm nhất",
  "tone": "Gần gũi, rõ ràng",
  "language": "vi",
  "targets": [
    { "channelId": "demo-facebook", "platform": "FACEBOOK" },
    { "channelId": "demo-linkedin", "platform": "LINKEDIN" }
  ],
  "sources": [
    { "kind": "URL", "value": "https://example.org/workshop" }
  ]
}
~~~

Payload này dùng để tạo nội dung mẫu. Nó chưa phải yêu cầu đăng bài. Mỗi kênh sẽ có phiên bản nội dung riêng; chỉ ID phiên bản đã duyệt mới được chuyển thành `approvedVersionId` khi tạo yêu cầu đưa vào queue.

## Phiên bản và duyệt

- Một brief có thể tạo nội dung cho hai kênh. Mỗi kênh giữ `currentVersionId` và `approvedVersionId` riêng.
- Phiên bản nội dung là bất biến: `versionId`, `briefId`, kênh, văn bản, tham chiếu ảnh, phiên bản nguồn và thời điểm tạo không đổi sau khi lưu. Bản cũ được giữ để xem lịch sử.
- Tạo kết quả mới, sửa văn bản/ảnh hoặc khôi phục bản cũ đều tạo `versionId` mới. Đặt phiên bản mới làm `currentVersionId` và xóa `approvedVersionId` đang có của kênh đó. Khôi phục là sao chép nội dung cũ sang phiên bản mới, không dùng lại ID cũ.
- Lịch sử cho xem các phiên bản của từng kênh theo thứ tự tạo và ghi nhận phiên bản nào đã từng được duyệt. Trạng thái “đã từng duyệt” chỉ để xem lại, không cho phép enqueue một bản cũ.
- Chỉ duyệt phiên bản hiện tại có thật của đúng kênh. Sau duyệt, `approvedVersionId = currentVersionId`. Việc duyệt Facebook không duyệt LinkedIn.
- Chỉ cho vào queue khi phiên bản có thật, thuộc đúng brief/kênh và đồng thời bằng cả `currentVersionId` lẫn `approvedVersionId`. Kiểm tra lại ngay trước khi gửi. Bản đã duyệt trong lịch sử không còn hợp lệ nếu kênh đã có bản mới.
- Hợp đồng social ở SP-009 tạo một `PublishRequest` cho mỗi kênh, gồm `requestId`, `channelId`, `connectionRevision` và `approvedVersionId`. SP-016 chỉ định nghĩa điều kiện đủ để bàn giao ID; chưa triển khai queue hoặc đăng thật.
- `VersionApprovalState` hiện giữ dữ liệu trong bộ nhớ để kiểm thử quy tắc. Việc lưu Room và kiểm tra approval trong transaction bền vững thuộc bước tích hợp sau; chưa được chứng minh bằng model này.

| Ca kiểm tra | Kết quả mong đợi |
| --- | --- |
| Duyệt Facebook rồi sửa chữ hoặc ảnh Facebook | Bản mới chưa duyệt; cả bản cũ và bản mới đều không được enqueue cho đến khi duyệt bản mới |
| Duyệt Facebook, LinkedIn chưa duyệt | Chỉ Facebook có thể được xét enqueue |
| Khôi phục một phiên bản cũ | Tạo ID mới, chưa duyệt; phải duyệt lại |
| Gửi ID phiên bản không tồn tại hoặc thuộc kênh khác | Từ chối, không tạo yêu cầu đăng |
