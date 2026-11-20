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

## Kết quả OAuth trên thiết bị

Ngày 20/11/2026 khoảng 17:24 (UTC+07:00), trên Samsung SM-G990E, APK debug
revision `1d9978a` mở Facebook Login từ nút **Connect Instagram**. Sau khi người dùng
cấp quyền Meta, app quay lại với `@gm_md253`, Instagram user ID `17841459976957663`
và Page ID `1427548503779474`. Graph API là `v27.0`. Hạn hiệu lực hiển thị từ metadata Meta
là `2027-02-18T10:22:39Z`.

Meta trả các scope của grant hiện tại gồm `pages_show_list`, `business_management`,
`instagram_basic`, `instagram_manage_comments`, `instagram_manage_insights`,
`instagram_content_publish`, `pages_read_engagement`, `pages_read_user_content`,
`pages_manage_posts`, `pages_manage_engagement` và `public_profile`.
[Ảnh SocialPilot sau callback](evidence/sp-065-instagram-oauth-device-2026-11-20.png)

## Kết quả đăng ảnh từ SocialPilot

Ngày 20/11/2026 khoảng 18:06 (UTC+07:00), cùng APK và tài khoản ở trên.
Dùng lại ảnh JPEG demo của media `18028517477875672`. URL đầu tiên có
khoảng trắng nên app chặn ở bước duyệt. Sau khi thay bằng URL JPEG đúng, kiểm
tra nội dung và duyệt caption `Kiem tra OAuth va dang anh tu SocialPilot [SP-065]`
trong app, rồi bấm **Publish to Instagram** một lần.

- Container ID lưu trước khi xuất bản `17973051317999232`.
- Media ID đã xuất bản `18439504675178856`.
- **Open original post** mở đúng bài của `@gm_md253`. Đã đối chiếu ảnh và caption.
  [Bài đã đăng](https://www.instagram.com/p/Df9gdnCkuI0/) · [Ảnh bài trên điện thoại](evidence/sp-065-instagram-published-from-app-2026-11-20.png)
- Đóng tiến trình SocialPilot và mở lại. **Check connection and post status** lấy
  lại đúng media ID và bật **Open original post**. Phép thử này đọc lại kết quả
  đã lưu của bài thật. Ca mất phản hồi khi xuất bản được kiểm thử riêng với phản
  hồi mô phỏng. [Ảnh đối soát sau khi mở lại](evidence/sp-065-instagram-reconcile-after-restart-2026-11-20.png)

Spike yêu cầu kết nối lại khi token hết hạn, hoặc Graph báo token không còn hiệu lực.
App giữ riêng credential theo kênh; không thực hiện refresh tự động trong thay đổi này.
Hạn nhận được lấy từ ngày hết hạn token hoặc ngày hết quyền truy cập dữ liệu sớm hơn nếu Meta cung cấp cả hai.
Kết quả mô phỏng kiểm tra hành vi app. Kết quả thiết bị và Meta thật được ghi riêng theo mỗi lần chạy.

## Ca mô phỏng có kiểm soát trên thiết bị

Người dùng chọn giữ nguyên grant Meta thật. AndroidJUnitRunner chạy trên Samsung
SM-G990E trong vùng SharedPreferences riêng với credential giả, response Graph mô
phỏng và không gửi bài mới. Kết quả `OK (3 tests)`:

- Hết hạn: app yêu cầu kết nối lại, chỉ vô hiệu bản ghi Instagram thử và dừng trước khi gọi Graph.
- Thu hồi: phản hồi Graph mô phỏng với mã `190` yêu cầu kết nối lại. Sau khi nạp phiên
  Instagram giả mới, phép kiểm tra trả `READY`. Credential Facebook thử vẫn dùng được ở mỗi bước.
- Mất phản hồi sau publish: lưu container, kiểm tra trạng thái bằng GET sau khi mở lại
  repository và giữ trạng thái đã đăng dù thiếu ID. Lệnh publish lần nữa bị chặn.

[Kết quả AndroidJUnitRunner](evidence/sp-065-controlled-device-tests-2026-11-20.txt)

Mốc hết hạn hiển thị trong ca Meta thật được cung cấp từ metadata. Ca thu hồi thật
và cơ chế đổi sang token dài hạn chưa chạy trong các phép thử trên.

## Kiểm chứng đổi User Access Token dài hạn

Khi bắt đầu kết nối Instagram, backend dùng access token nhận từ authorization code
để gọi `fb_exchange_token` trên Graph API. Backend dùng User Access Token dài hạn lấy
Page Access Token và kiểm tra hiệu lực, scope và metadata hạn của Page token.
Log bằng chứng chỉ ghi thời hạn, không ghi token. Page token dài hạn vẫn cần tuân thủ
ngày hết quyền truy cập dữ liệu nếu Meta cung cấp. App tiếp tục yêu cầu kết nối lại
khi credential đã hết hiệu lực hoặc bị thu hồi.
[Hướng dẫn token dài hạn của Meta](https://developers.facebook.com/documentation/facebook-login/guides/access-tokens/get-long-lived)
