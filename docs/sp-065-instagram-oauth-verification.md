# Kiểm chứng OAuth và đăng ảnh Instagram — SP-065

[Issue #90](https://github.com/nigPeter2503/Social_Pilot/issues/90) · [OAuth Facebook SP-010](https://github.com/nigPeter2503/Social_Pilot/pull/74) · [Hợp đồng OAuth](social-contract.md)

## Lấy bằng chứng có thể đối chiếu

Ngày 20/11/2026, Graph API Explorer v27.0 trả về Page ID `1427548503779474` và
Instagram user ID `17841459976957663` (`@gm_md253`). Việc đăng ảnh trong Explorer
tạo container `17973019892999232`, trả media ID `18139634560638070` và đọc lại
đúng nội dung. Người thực hiện đối chiếu ảnh và caption trên Instagram.
[Bài thử đăng bằng Explorer](https://www.instagram.com/p/Df9R4oomV49/)

Bản gốc nằm trong `temp/sp-019-instagram-2026-11-20` của worktree `main` cục bộ.
Các ảnh và JSON ở đó xác nhận quyền trên tài khoản thử với Explorer. Khi thử từ
SocialPilot, ghi SHA commit, thiết bị, ngày giờ, kết quả thực tế và ảnh đã che
token, client secret, authorization code, session verifier, mã bàn giao và URL ảnh có chữ ký.

## Chuẩn bị ca thử trên thiết bị

- Dùng Meta app và Page đã kiểm chứng. Nạp các biến của backend như mục SP-010
  trong [hướng dẫn phát triển](development.md). `FACEBOOK_TARGET_PAGE_ID` là ID Facebook Page.
  URL callback HTTPS trong Meta Dashboard và giá trị `FACEBOOK_REDIRECT_URI` cùng có đường dẫn
  `/api/oauth/facebook/callback`. Chạy backend với `SERVER_ADDRESS=127.0.0.1` trên cổng của môi trường thử.
- Truyền base URL HTTPS của backend vào app bằng
  `-PsocialpilotOauthApiBaseUrl=https://<host>/`. Cài APK debug vào thiết bị, mở app
  rồi bấm **Kết nối Instagram**. Dùng tài khoản chỉ được phép thử trong app Meta của nhóm.
- Chuẩn bị URL HTTPS trỏ trực tiếp vào ảnh JPEG mà Meta tải được và caption nhận biết riêng.
  Chọn nội dung được phép đăng trên tài khoản thử.

## Ma trận kiểm chứng

| Ca | Cách thử | Kết quả cần ghi |
| --- | --- | --- |
| OAuth Android → backend → Meta | Bấm **Kết nối Instagram**, cấp các quyền đã chọn và quay về app. | Đúng `@gm_md253`, ID Instagram và Page riêng, scope thực cấp, phiên bản Graph và hạn token nếu có. |
| Hủy hoặc từ chối | Thực hiện một lượt từ chối khi Meta hỏi cấp quyền. | App không tạo credential mới và báo lỗi phù hợp. |
| Hết hạn và thu hồi | Dùng phiên đã hết hạn hoặc đã bị thu hồi, bấm **Kiểm tra kết nối và đối soát**. | App yêu cầu kết nối lại đúng kênh. Kiểm tra dữ liệu và kết nối Facebook riêng. Nếu phía Meta thu hồi grant chung, ghi tác động thực tế lên cả hai kênh. |
| Gia hạn và kết nối lại | Xác định cơ chế gia hạn thực cấp. Kết nối lại qua trình duyệt sau khi token hết hiệu lực. | Ghi rõ thao tác gia hạn được kiểm chứng hoặc giới hạn của cấu hình, và kết quả phiên mới. |
| Đăng từ bản duyệt | Nhập URL và caption, bấm **Duyệt ảnh và caption này**, rồi **Đăng thật lên Instagram**. | Bài mới có media ID và permalink. Bấm **Mở bài gốc** để xác nhận. Đổi nội dung sau khi duyệt phải tắt quyền đăng của bản đó. |
| Mất phản hồi khi xuất bản | Đối soát bằng container đã lưu. Kiểm thử transport dùng phản hồi giả để tái hiện mất phản hồi có kiểm soát. | Chỉ đọc trạng thái container. Khi Meta trả `PUBLISHED` mà chưa nhận media ID, app giữ kết quả đã xuất bản chưa rõ ID để người thử đối chiếu thủ công. |

Spike yêu cầu kết nối lại khi token hết hạn, hoặc Graph báo token không còn hiệu lực.
App giữ riêng credential theo kênh; không thực hiện refresh tự động trong thay đổi này.
Hạn nhận được lấy từ ngày hết hạn token hoặc ngày hết quyền truy cập dữ liệu sớm hơn nếu Meta cung cấp cả hai.
Kết quả mô phỏng kiểm tra hành vi app. Kết quả thiết bị và Meta thật được ghi riêng theo mỗi lần chạy.
