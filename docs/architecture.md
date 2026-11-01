# Kiến trúc SocialPilot

Android Java/XML → ViewModel/LiveData → nghiệp vụ Java → Repository → Room/API.
ExecutorService dùng chung cho tác vụ nền trong process. WorkManager dành cho công việc bền vững có thể trì hoãn.
AI gọi trực tiếp từ thiết bị đến provider. Backend Spring Boot chỉ phục vụ OAuth có client secret.

Hiện đã nối luồng topic → kiểm tra → lưu Room → quan sát danh sách. Room chưa chứa key/token.
Các interface trong domain/contract là điểm mở rộng, chưa có thực thi AI, đăng bài, ngân sách hoặc đồng bộ.

## Quy tắc cần giữ khi triển khai tiếp

- Bài được duyệt theo phiên bản; chỉnh sửa làm mất hiệu lực duyệt.
- PublishQueue cần transaction, khóa duy nhất và đối soát; chưa rõ kết quả thì dừng để kiểm tra.
- Giữ trước ngân sách nguyên tử cho hai nhánh; hủy Future phải đi kèm hủy HTTP Call.
- API key/token được mã hóa bằng Keystore, không lưu plaintext trong Room/log/backup.
- OAuth cần state, ràng buộc phiên, PKCE khi hỗ trợ và mã bàn giao một lần có TTL.
- URL/comment là dữ liệu đầu vào; không được ghi đè quy tắc duyệt và ngân sách.
- Quyền Facebook/LinkedIn phải kiểm chứng thật ở tuần 1; mock không thay bằng chứng nghiệm thu.
