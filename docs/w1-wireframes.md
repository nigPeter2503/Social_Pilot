# SP-016 — Wireframe và hợp đồng brief

> Đây là bản thiết kế Tuần 1. Kết quả AI và thao tác đăng bài trong wireframe là mô phỏng, chưa phải tích hợp thật.

## Luồng chính

Nhập brief → Tạo hai kết quả → Xem trước → Sửa nội dung/ảnh

→ Duyệt một phiên bản → Đưa phiên bản đã duyệt vào queue

Nếu sửa sau khi duyệt: tạo phiên bản mới → cần duyệt lại.

Nếu phiên bản chưa được duyệt hoặc không tồn tại: không cho vào queue.

## Màn hình 1: Nhập brief

- Topic: nội dung muốn viết.

- Mục tiêu: bài viết cần đạt điều gì.

- Đối tượng: người đọc hướng tới.

- Giọng văn: ví dụ chuyên nghiệp hoặc gần gũi.

- Ngôn ngữ: VI hoặc EN.

- Kênh đích: Facebook, LinkedIn hoặc cả hai; tối đa một kênh mỗi nền tảng trong một brief.

- Nguồn tham khảo: văn bản hoặc URL, nếu có.

- Nút tiếp tục: Tạo kết quả mẫu.

## Màn hình 2: Xem trước hai kênh

Brief chung: [topic] · [mục tiêu] · [đối tượng] · [ngôn ngữ]

| Facebook | LinkedIn |
| --- | --- |
| Trạng thái tạo kết quả A | Trạng thái tạo kết quả B |
| Nội dung bài viết mẫu | Nội dung bài viết mẫu |
| Ảnh mẫu, nếu có | Ảnh mẫu, nếu có |
| Sửa · Hủy A · Xem phiên bản | Sửa · Hủy B · Xem phiên bản |

Hủy một kết quả không hủy kết quả còn lại. Mỗi kết quả có trạng thái và lỗi riêng. Nội dung ở đây là mẫu cho wireframe, chưa phải phản hồi AI thật.

## Màn hình 3: Sửa và duyệt

- Hiển thị kênh, nội dung, ảnh và ID phiên bản đang xem.
- Sửa nội dung hoặc ảnh → tạo phiên bản mới, trạng thái `CHƯA DUYỆT`.
- Khôi phục phiên bản cũ → tạo phiên bản mới từ nội dung cũ, vẫn `CHƯA DUYỆT`.
- Bấm Duyệt → chỉ phiên bản hiện tại chuyển thành `ĐÃ DUYỆT`.
- Bấm Đưa vào queue → kiểm tra đúng ID phiên bản tồn tại và đã duyệt.
- Giới hạn ký tự của từng kênh sẽ lấy từ capability đã kiểm chứng; wireframe chưa ghi một con số cố định.

## Trạng thái và thao tác (VI/EN)

| Vị trí | Trạng thái | Thông báo VI | Thông báo EN | Thao tác |
| --- | --- | --- | --- | --- |
| Brief | Rỗng | Chưa có brief. Hãy nhập topic. | No brief yet. Enter a topic. | Cho nhập brief |
| Tạo kết quả | Đang tải | Đang tạo kết quả mẫu… | Generating sample results… | Hiển thị tiến độ riêng cho A và B |
| Xem trước | Rỗng | Chưa có kết quả để xem trước. | No result to preview yet. | Quay lại brief |
| Xem trước | Lỗi một nhánh | Không tạo được kết quả này. | Could not generate this result. | Cho thử lại riêng nhánh lỗi |
| Tạo kết quả | Offline | Không có mạng để tạo kết quả. | Connect to generate results. | Giữ brief; không gửi yêu cầu AI |
| Kênh/queue | Thiếu quyền | Kênh chưa có quyền đăng. | Channel publishing permission is missing. | Dừng thao tác; dẫn tới cài đặt kết nối |
| Duyệt | Phiên bản không hợp lệ | Phiên bản này không tồn tại hoặc chưa được duyệt. | This version is missing or not approved. | Không đưa vào queue |

Tạo kết quả cần mạng. Sửa nội dung và xem bản nháp là thao tác trong app. Trước khi đăng thật cần kiểm tra mạng, quyền kênh và phiên bản được duyệt. Wireframe này chưa thực hiện đăng bài.

## Điều hướng bản mẫu

[Brief] [Kênh / Channels] [Hộp thư / Inbox] [Báo cáo / Dashboard] [Cài đặt / Settings]

| Điểm vào | Nội dung wireframe | Trạng thái cần thể hiện |
| --- | --- | --- |
| Brief | Nhập brief và xem hai kết quả theo kênh | Rỗng, đang tải, lỗi, offline |
| Kênh / Channels | Facebook và LinkedIn; trạng thái kết nối và quyền | Chưa kết nối, thiếu quyền, offline |
| Hộp thư / Inbox | Vị trí dự kiến hiển thị bình luận | Rỗng, đang tải, lỗi, thiếu quyền |
| Báo cáo / Dashboard | Vị trí dự kiến hiển thị số liệu | Chưa có dữ liệu, đang tải, lỗi, offline |
| Cài đặt / Settings | Ngôn ngữ VI/EN và trạng thái kết nối kênh | Chưa cấu hình, lỗi kết nối |

Các điểm vào Inbox và Dashboard ở đây chỉ là wireframe. Chưa có bình luận hoặc số liệu thật. Khi thiếu quyền kênh, hiển thị lý do và đường tới Cài đặt; không hiển thị dữ liệu mẫu như dữ liệu thật.

### Kênh / Channels

Danh sách Facebook và LinkedIn cho biết tên kênh, trạng thái kết nối và quyền có thể dùng. Chọn một kênh để xem chi tiết; nút kết nối/chỉnh quyền dẫn tới Cài đặt. Wireframe không thực hiện OAuth thật.

### Hộp thư / Inbox

Chọn kênh và xem vùng danh sách bình luận cùng nút tải lại. Khi chưa có quyền hoặc chưa có dữ liệu, hiển thị trạng thái tương ứng; không đặt bình luận mẫu vào danh sách như bình luận thật.

### Báo cáo / Dashboard

Chọn kênh và khoảng thời gian; vùng số liệu và biểu đồ để trống cho tới khi có snapshot được kiểm chứng. Số liệu minh họa phải mang nhãn “Mẫu / Sample”.

### Cài đặt / Settings

Chọn ngôn ngữ VI/EN và xem trạng thái kết nối từng kênh. Nút kết nối lại chỉ là vị trí dự kiến của luồng OAuth; không hứa kênh đã dùng được khi quyền chưa được xác minh.

| Trạng thái dùng cho các điểm vào | Thông báo VI | Thông báo EN | Hành động wireframe |
| --- | --- | --- | --- |
| Đang tải | Đang tải dữ liệu… | Loading data… | Hiển thị tiến độ, không thay bằng dữ liệu mẫu |
| Rỗng | Chưa có dữ liệu cho mục này. | No data for this section yet. | Giữ vùng nội dung rỗng |
| Lỗi | Không tải được dữ liệu. | Could not load data. | Cho thử lại thao tác tải |
| Offline | Đang ngoại tuyến. | You are offline. | Không gọi tác vụ cần mạng |
| Thiếu quyền | Chưa có quyền truy cập kênh. | Channel access is not available. | Dẫn tới trạng thái kết nối trong Cài đặt |
