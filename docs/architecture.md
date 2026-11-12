# Kiến trúc SocialPilot

Android Java/XML → ViewModel/LiveData → nghiệp vụ Java → Repository → Room/API.
ExecutorService dùng chung cho tác vụ nền trong process. WorkManager dành cho công việc bền vững có thể trì hoãn.
AI gọi trực tiếp từ thiết bị đến provider. Backend Spring Boot chỉ phục vụ OAuth có client secret.

Hiện đã nối luồng topic → kiểm tra → lưu Room → quan sát danh sách. Room chưa chứa key/token.
Các interface trong domain/contract là điểm mở rộng, chưa có thực thi AI, đăng bài, ngân sách hoặc đồng bộ.

Hợp đồng social v1 (SP-009) có DTO kênh/capability, request/result đăng,
lỗi chuẩn hóa và DTO OAuth đồng bộ Android/backend. Xem [hợp đồng social](social-contract.md)
cho ví dụ, ràng buộc phiên, ownership và ca kiểm chứng. DTO/interface không thay
adapter OAuth, queue hoặc kiểm chứng API thật; endpoint OAuth vẫn trả 501.

## Quy tắc cần giữ khi triển khai tiếp

- Bài được duyệt theo phiên bản; chỉnh sửa làm mất hiệu lực duyệt.
- PublishQueue cần transaction, khóa duy nhất và đối soát; chưa rõ kết quả thì dừng để kiểm tra.
- Giữ trước ngân sách nguyên tử cho hai nhánh; hủy Future phải đi kèm hủy HTTP Call.
- API key/token được mã hóa bằng Keystore, không lưu plaintext trong Room/log/backup.
- OAuth cần state, ràng buộc phiên, PKCE khi hỗ trợ và mã bàn giao một lần có TTL.
- URL/comment là dữ liệu đầu vào; không được ghi đè quy tắc duyệt và ngân sách.
- Quyền Facebook/LinkedIn phải kiểm chứng thật ở tuần 1; mock không thay bằng chứng nghiệm thu.

## Phân công dữ liệu và schema chung

TV4 — Tô Lãng Nghị sở hữu API, snapshot, repository, công thức, lưu mục tiêu/tính tiến độ và F7.N2 (nghiệp vụ cùng giao diện báo cáo AI). TV3 — Đỗ Phước Thiện sở hữu XML/ViewModel F7.B1 (số liệu từng bài), F7.B2 (đường tăng trưởng), F7.B3 (dashboard), F7.N1 (phân tích theo nhóm), F7.N3 (mục tiêu); không tính lại chỉ số trong UI. Snapshot thật bắt đầu từ tuần 2–3 khi có quyền API, tuần 4 dùng lịch sử đã tích lũy; thiếu quyền phải ghi blocker, giữ khoảng trống dữ liệu.

[Hợp đồng dữ liệu Room SP-012](room-schema-contract.md) ghi quy ước ID, quan hệ, ownership, khóa duy nhất, xóa theo kênh và kế hoạch migration từ v1. Các quyết định retention và reconnect còn mở được ghi cùng người phụ trách trong hợp đồng.
Nguyễn Đức Đạt (TV2) điều phối schema Room, phiên bản database và chuỗi migration theo [CONTRIBUTING mục 6](../CONTRIBUTING.md#6-phối-hợp-phần-dùng-chung). Chủ module đề xuất entity/DAO, migration và kiểm thử phần mình; Đạt chốt thứ tự tích hợp phần chung. Quy ước chi tiết cần được nhóm chốt trong tuần 1; phân công này chưa đồng nghĩa thiết kế schema đã hoàn tất.

## Bản đề xuất dữ liệu F6/F7 để review

[Hợp đồng analytics SP-020](analytics-data-contract.md) mô tả comment,
pagination/dedup, snapshot, công thức, mục tiêu và đầu ra dashboard. Bản này dùng
hợp đồng Social v1 đã merge. Thiện đã xác nhận phạm vi UI của draft-2. Mapping
capability/error và đề xuất lưu trữ còn chờ Minh và Đạt xác nhận.
[Fixture có nhãn MO_PHONG](contracts/analytics-examples.json) và
[hồ sơ kiểm tra](sp-020-verification.md) phục vụ review, không phải bằng chứng API thật.
Chưa thêm Java DTO/repository, thay SocialConnector hoặc đổi Room version/migration.
