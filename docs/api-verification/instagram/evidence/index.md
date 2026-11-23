# SP-066 · Evidence index và quy tắc chia sẻ

Nguồn: gói evidence phiên 21/11/2026 do người dùng cung cấp. Các file `responses/` là JSON response đã làm sạch, **không phải log HTTP gốc**. Bản tái phát trong test là `FIXTURE_TU_API_DA_CHE`; nguồn quan sát là `API_THAT`. `fixtures.json` ở thư mục cha là `MO_PHONG`; parser là `PROTOTYPE`. Binding IG-12 và giờ request T1/T2 ngày 21/11/2026 do người trực tiếp thực hiện xác nhận; ảnh/JSON đã publish không tự chứa hai dữ kiện này.

| Ca | Response JSON publish được | Screenshot publish được | Ghi chú & Trạng thái |
|---|---|---|---|
| Bối cảnh | — | [post](screenshots/IG_context_01_post.png), [media](screenshots/IG_context_02_media.png) | Xác nhận bối cảnh tài khoản và bài đăng Instagram |
| IG-01 | [identity](responses/IG-01_identity_response.json) | [debugger](screenshots/IG01_identity_debugger.png), [explorer](screenshots/IG01_identity_explorer.png) | PASS: Page `1427548503779474` liên kết đúng IG user `17841459976957663` |
| IG-02 | [comments](responses/IG-02_comments_response.json) | [ui](screenshots/IG02_comments_ui.png), [explorer](screenshots/IG02_comments_explorer.png) | PASS: Đọc 4 comment gốc C01–C04 trên media `18111630677338097` |
| IG-03 | [page1](responses/IG-03_page1_response.json), [page2](responses/IG-03_page2_response.json), [refresh](responses/IG-03_refresh_page1_response.json), [terminal](responses/IG-03_terminal_page_response.json) | [page1](screenshots/IG03_paging_page1_redacted.png), [page2](screenshots/IG03_paging_page2_redacted.png), [terminal](screenshots/IG03_terminal_page_redacted.png) | PASS trong phạm vi ID/page: C04→C03, refresh C04 và terminal C01 (paging vắng mặt, after=null); `Map` dedup mô phỏng, Room/upsert chờ SP-041; cursor đã che |
| IG-04 | [before](responses/IG-04_replies_before_response.json) | [c01_ui](screenshots/IG04_replies_c01_ui.png), [empty](screenshots/IG04_replies_before_empty.png) | PASS: C04 replies rỗng trước khi POST |
| IG-05 | [POST](responses/IG05_post_reply.json), [GET](responses/IG05_get_replies_readback.json) | [post](screenshots/IG05_post_reply.png), [readback](screenshots/IG05_get_replies_readback.png), [ui](screenshots/IG05_reply_ui.png) | PASS: Tạo reply `18120004997517397` và readback đúng dưới C04. Không POST lại |
| IG-06 | [follower](responses/IG06_followers.json) | [T1](screenshots/IG06_followers_T1.png) | PASS: `followers_count = 1` tại T1 `21/11/2026 16:50 +07:00` theo owner; ảnh/JSON không chứa giờ GET |
| IG-07 | [reach](responses/IG07_reach.json) | [T1](screenshots/IG07_reach_T1.png) | PASS: `reach = 0` (lifetime) tại T1 `21/11/2026 16:52 +07:00` theo owner; ảnh/JSON không chứa giờ GET |
| IG-08 | [like_count](responses/IG08_like_count.json) | [explorer](screenshots/IG08_like_count.png) | PASS: Media field `like_count = 1` |
| IG-09 | [comments_count](responses/IG09_comments_count.json) | [explorer](screenshots/IG09_comments_count.png) | PASS: Media field `comments_count = 6` |
| IG-10 | [shares_count](responses/IG10_shares_count.json) | [explorer](screenshots/IG10_shares_count.png) | PASS: Media field `shares_count = 0` |
| IG-11 | [impressions error](responses/IG11_impressions_error.json) | [time](screenshots/IG11_media_publish_time.png), [error](screenshots/IG11_impressions_error.png) | PASS/UNSUPPORTED: Media tạo 2026, lỗi code 100 ngưng hỗ trợ từ v22.0+; value=null |
| IG-12 | [A success](responses/IG12_token_A_success.json), [B denied](responses/IG12_token_B_denied.json) | [deb_A](screenshots/IG12_debugger_token_A.png), [resp_A](screenshots/IG12_reach_token_A_success.png), [deb_B](screenshots/IG12_debugger_token_B.png), [resp_B](screenshots/IG12_reach_token_B_denied.png) | PASS theo xác nhận owner: Token A→reach 0, Token B→code 10; role/target production chờ Minh |
| IG-13 | [follower T2](responses/IG13_followers_recheck.json), [reach T2](responses/IG13_reach_recheck.json) | [follower_T2](screenshots/IG13_followers_T2.png), [reach_T2](screenshots/IG13_reach_T2.png) | PASS theo xác nhận owner: T2 `21/11/2026 17:02–17:03 +07:00` sau T1 IG-06/07; follower 1, reach 0; snapshot/readback production chờ SP-045 |

Tổng cộng **27 ảnh minh chứng đã được chuẩn hóa tên file** theo tiền tố mã ca thử (`IG01_`–`IG13_` và `IG_context_`). 24 ảnh gốc an toàn, 3 ảnh phân trang nhạy cảm (`IG03_paging_page1_redacted.png`, `IG03_paging_page2_redacted.png`, `IG03_terminal_page_redacted.png`) được phủ pixel đen che toàn bộ cursor/URL. JSON IG-03 giữ marker `[REDACTED_BY_USER]`; không lưu token, URL chứa token hoặc cursor thật. Không bổ sung bản gốc vào repo hoặc PR.

## Lệnh xác minh

Chạy từ repo root trên nhánh `feat/sp-066-instagram-comments-insights` sau khi cập nhật hồ sơ:

```text
node docs/api-verification/instagram/verify-fixtures.cjs
node docs/api-verification/instagram/verify-api-fixtures.cjs
node docs/contracts/verify-analytics-examples.cjs
git diff --check
```

Baseline trước gói evidence: `0c42899`.
