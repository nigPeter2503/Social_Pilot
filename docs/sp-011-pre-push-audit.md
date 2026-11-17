> Cập nhật 17/11/2026: bản sửa provenance và tích hợp main nằm trong [báo cáo hiện hành](sp-011-final-integration.md). Các head, kết quả test và trạng thái dependency dưới đây là lịch sử ngày 15/11.

# SP-011 — Audit trước push ngày 15/11/2026

Owner Minh, yêu cầu F1.B1/F1.B3/F5.B1/F5.B5. Reviewers Đạt và Nghị.
Phạm vi là head đã push 97e9dadd2a4c9915ce0c71af2377ef84bf9232fd và bản sửa
credential recovery local. Audit đã đọc code đi qua UI → ViewModel → publisher/
ledger → store/API, đối chiếu tiêu chí Issue #13, reviews, CI, Project và main.
Code production giữ nguyên trong lượt audit này. Tài liệu được đồng bộ lại theo
trạng thái mới nhất. [Report JSON](evidence/sp-011-pre-push-audit-2026-11-15.json)
ghi lệnh, source hash, báo cáo test và artifact hash.

## Kết quả để owner quyết định push

Bản local đủ để commit/push cho review tiếp. Trong phạm vi code và các regression
đã rà, chưa ghi nhận thêm lỗi P1/P2 chặn lượt push. Mô phỏng merge cùng các sửa local
vào main 7def84d không có conflict. Các điều kiện merge/đóng ticket vẫn còn chờ
review và dependency. Kết luận audit áp dụng cho source hash trong report, không
bảo đảm mọi tình huống provider hoặc production đã được thử.

| Kiểm tra | Kết quả |
|---|---|
| JVM Android chạy lại | 98 tests PASS, 0 failures/errors/skipped |
| Instrumentation chạy lại trên SM-G990E / Android 16 | 8 tests PASS, 0 failures/errors/skipped |
| Backend test chạy lại | 57 tests PASS, 0 failures/errors/skipped |
| assembleDebug, lintDebug, bootJar | PASS; lint 0 errors/6 warnings |
| Diff/index | diff --check đạt, không có unmerged entries |
| Mô phỏng merge main và sửa local | Exit 0; main hơn một commit SP-016, không có conflict |
| Assets | 11 ảnh Postman và 3 ảnh owner khớp hash |
| Hợp đồng dữ liệu | OAuth wire DTO và Room entity/schema giữ nguyên so với mốc 15875c4 |

## Đối chiếu dữ liệu và luồng lỗi

| Bất biến được rà | Đường code / regression |
|---|---|
| Provider/session và handoff một lần | Backend session engine; callback queue/provider binding, replay/TTL tests |
| Credential cần đúng channel/revision còn hiệu lực | Production publisher dùng tokenFor, cờ reauth ràng buộc channel/revision; expiry/reconnect tests |
| Marker chỉ giữ lịch sử kết nối | Nhánh v2 còn sót có marker chặn connection/accessToken/tokenFor; fresh OAuth tăng revision |
| Nâng cấp đầu và phục hồi có marker xử lý riêng | Migration v2 lần đầu vẫn hoạt động; missing/corrupt v3, lỗi dọn nguồn và lỗi ghi được kiểm tra |
| Dữ liệu nhất quán khi commit thất bại | Khôi phục snapshot ở writeConnected, giữ marker, yêu cầu OAuth khi recovery chưa có credential |
| Một lần duyệt không tự gửi POST tiếp | One-shot body, retry/redirect tắt; 503/timeout/duplicate/cancel tests |
| Chống đăng trùng qua reload/recovery | Account/content key, giữ UNKNOWN/PENDING/SUCCESS, kiểm migration ledger |
| Duyệt lại lỗi đã biết | Giữ error/nextAction, requestId cũ, revision mới cho 401 và Retry-After cho 403/429 |
| Xác nhận bài cũ không cập nhật nhầm bài mới | Target attempt/requestId/Post ID; confirm A trả State A và giữ pointer/record B |
| OAuth callback đến gần nhau | Queue theo session, kiểm lại TTL, duplicate chỉ exchange một lần; Intent test trên thiết bị |

Các tests dùng credential giả, MockWebServer hoặc API giả trong process. Bằng chứng
OAuth và bài thật đã có trước đó được giữ tại hồ sơ thiết bị. Audit không tạo thêm
bài hoặc thay kết quả fixtures thành nghiệm thu API thật.

## Đồng bộ tài liệu và GitHub

Audit phát hiện báo cáo triển khai còn ghi Draft/head cũ và các đoạn PR draft trộn
số tests của các lần sửa. Đã cập nhật báo cáo triển khai, viết lại description local
quanh hành vi cuối cùng và kết quả 98/8/57, đồng thời liên kết report audit hiện tại.
Các JSON 93/7, 96/8 và 98/8 trước audit giữ vai trò snapshot lịch sử. Source và report
98/8 trước audit đã được kiểm tra hash trước khi chạy lại, XML cũ được lưu trong
thư mục build phục vụ đối chiếu local.

GitHub hiện có head 97e9dad với description 96 tests và bốn CI checks SUCCESS.
Bản local có thêm credential recovery và tài liệu audit. Lượt push kế tiếp cần
đưa đủ code/tests/docs/evidence rồi cập nhật description từ bản soạn mới. CI của
head mới chỉ có sau push. Issue #13 OPEN, Project In Progress, owner nigPeter2503.
Issue body còn các đoạn khởi tạo lịch sử; tình trạng thực thi hiện tại được đối
chiếu bằng PR/evidence và Project.

## Điều kiện còn chờ

- SP-010/#74 vẫn OPEN/CHANGES_REQUESTED tại d1d55a0. Sau khi tích hợp/squash cần
  rà lại main/diff riêng SP-011 và chạy checks bị ảnh hưởng.
- Các reviews của Đạt là COMMENTED và còn yêu cầu xác nhận bản recovery local.
  Nghị đang được yêu cầu review. Chưa có đủ hai human approvals.
- Refresh giữ giới hạn/đạt một phần F1.B3 theo app/product đang thử. Hết hạn/thu hồi
  thật, cold start giữa OAuth và hai account thật cùng kết nối vẫn chưa thử riêng.
- Lint có 6 warnings đã ghi nhận, gồm commit đồng bộ, resource chưa dùng và autofill.

Audit chuẩn bị bản cho owner duyệt trước commit/push. Chưa thực hiện commit, push,
merge, chuyển trạng thái Project hoặc đăng phản hồi reviewer trong lượt này.

Sau audit, owner yêu cầu commit/push và đồng bộ description. Source đã kiểm chứng
được commit ở a69b8db, hồ sơ nằm trong commit tài liệu đi kèm. Kết quả và GitHub
snapshot ở trên giữ nguyên thời điểm audit trước thao tác push.
