# SP-011 — Bản nháp kiểm chứng LinkedIn OAuth, quyền đăng và refresh

> **DRAFT — CHỜ MINH KIỂM TRA.** Soạn ngày 08/11/2026 (Asia/Bangkok).
> Đây là kế hoạch và biểu mẫu kiểm chứng, chưa phải kết quả thực thi.
> Mọi phép thử đang **NOT TESTED**; chưa truy cập LinkedIn app/tài khoản của nhóm để kiểm tra quyền.

## 1. Thông tin task và căn cứ

| Mục | Nội dung |
|---|---|
| Ticket | [SP-011 / Issue #13](https://github.com/nigPeter2503/Social_Pilot/issues/13) |
| Issue cha / Project | [SP-002 / #4](https://github.com/nigPeter2503/Social_Pilot/issues/4) / [Project #5](https://github.com/users/nigPeter2503/projects/5) |
| Owner | Ngô Gia Minh — TV1 — `nigPeter2503` |
| Hai reviewer theo brief | Nguyễn Đức Đạt — `dat532006`; Tô Lãng Nghị — `bob35306` |
| Yêu cầu | F1.B1, F1.B3, F5.B1, F5.B5; kiểm kê khả năng phục vụ SP-019 |
| Mốc gốc | Hết ngày làm việc 4 từ đầu Tuần 1; gửi thông tin sớm cho Nghị ngày làm việc 3; ước lượng owner 8 giờ |
| Nhánh triển khai dự kiến | `feat/sp-011-linkedin-oauth-spike`; PR riêng về `main` |
| Trạng thái bản nháp | Tài liệu để Minh duyệt; chưa nhận/cập nhật card hoặc tạo nhánh/PR SP-011 trong lượt này |
| Trạng thái tracker | Brief ngày 04/11 ghi OPEN/Todo. Ngày 08/11 lỗi kết nối GitHub nên chưa xác minh lại; không dùng trạng thái cũ như xác nhận hiện tại |
| Baseline | Local `289e4d137b5d006c16d257fae413e0d5687fc472`, nhánh `feat/sp-009-social-contract`; chưa xác minh SP-009 đã merge remote |

Nguồn: [CONTRIBUTING](../../CONTRIBUTING.md), [taskbrief SP-011](../TaskBrief/W1/tv1-minh-taskbrief.md#sp-011), [social contract](../social-contract.md), [hồ sơ SP-009](../sp-009-verification.md), [kiến trúc](../architecture.md), [requirements](../requirements.md), [proposal](../SocialPilot_Proposal_Java.md), [development](../development.md), [bản nháp SP-010](sp-010-facebook-oauth-draft.md).

Mã hiện tại mới có DTO/hợp đồng. Endpoint OAuth skeleton trả 501, chưa có LinkedIn adapter hoặc luồng callback Android. Thành công test DTO không chứng minh state/TTL/replay, OAuth provider, refresh hay bài đăng thật.

## 2. Các quyết định và quyền Minh cần kiểm tra

Nhãn: **[MINH QUYẾT ĐỊNH]** là chọn phương án; **[MINH KIỂM TRA]** là xác minh trên tài khoản/dashboard thật; **[NHÓM REVIEW]** là phối hợp kỹ thuật; **[ĐÃ QUY ĐỊNH]** là giữ hợp đồng hiện hành.

| ID | Việc cần xử lý | Hướng xử lý / lý do | Ô Minh điền | Cần trước |
|---|---|---|---|---|
| LI-D01 | [MINH QUYẾT ĐỊNH] Demo bằng cá nhân hay tổ chức | Chọn theo app/product/quyền thực có **và** khả năng F6/F7 với Nghị; không chọn chỉ vì đăng bài dễ | `PERSON` hoặc `ORGANIZATION`, lý do: **CHỜ** | Chốt author và scope |
| LI-C01 | [MINH KIỂM TRA] App và quyền quản lý | Mở Developer Portal, ghi Client ID, người sở hữu, Products, Auth, trạng thái product/access tier nếu có | App/product/tier: **CHỜ** | OAuth thật |
| LI-C02 | [MINH KIỂM TRA] Tài khoản author | Cá nhân: đúng người đồng ý đăng; tổ chức: đúng Organization và vai trò có quyền tương ứng. Phân biệt quyền quản lý developer app với quyền đăng trên tổ chức | Loại/ID/URN/role: **CHỜ** | Đăng thử |
| LI-C03 | [MINH KIỂM TRA] Scope thật | Phân biệt scope hiển thị trong app, scope yêu cầu, scope được cấp và endpoint chạy được | Điền mục 4: **CHỜ** | Gọi API |
| LI-C04 | [MINH KIỂM TRA] Programmatic refresh | Kiểm tra entitlement của app và token response có refresh token hay không; ghi metadata, không ghi token | Có/không/chưa rõ + bằng chứng: **CHỜ** | Đánh giá F1.B3 |
| LI-D02 | [MINH QUYẾT ĐỊNH] HTTPS và redirect | Tái dùng môi trường SP-010 nếu đã chốt; callback LinkedIn đăng ký riêng và đúng allow-list. Không chờ Facebook được cấp quyền | Base URL/provider callback/app redirectId: **CHỜ** | Cấu hình Auth |
| LI-D03 | [MINH QUYẾT ĐỊNH] Bài đăng thật | Xác nhận author, nội dung, đối tượng xem, số bài, ảnh nếu có và thời gian giữ cho Nghị kiểm thử | Nội dung/phạm vi/lịch giữ-xóa: **CHỜ** | POST tạo bài |
| LI-C05 | [MINH KIỂM TRA] API version | Chọn version còn hỗ trợ cho endpoint và app; ghi header thực gửi. Không tự lấy tháng hiện tại làm version hợp lệ | API/version/ngày kiểm tra: **CHỜ** | Chốt request |
| LI-D04 | [MINH QUYẾT ĐỊNH] Xử lý nếu thiếu quyền/refresh | Đề xuất giữ BLOCKED/đạt một phần và ghi việc xin quyền; nếu cần đổi phạm vi phải có chấp thuận theo quy trình. Không tự coi reauthorize là refresh đạt | Người xử lý/hạn/quyết định: **CHỜ** | Chốt kết quả spike |
| LI-C06 | [MINH KIỂM TRA] SP-009, callback chung, tracker và lịch | Xác minh revision đã review/merge; card thực tế; mốc ngày 3–4 của W1 có nguy cơ đã trễ | Revision/link PR/trạng thái/hạn mới nếu được chốt: **CHỜ** | Bắt đầu triển khai |
| LI-D05 | [MINH QUYẾT ĐỊNH] Người thử và bàn giao | Chọn thiết bị, trình duyệt, người thu bằng chứng; thống nhất với Nghị nơi lấy token an toàn | Thiết bị/thời điểm/người giữ bí mật: **CHỜ** | Test/bàn giao |

Không đưa secret/token/code/verifier vào tài liệu hoặc ảnh. Chỉ ghi “được cấp/chưa cấp”, expiry, scope, token reference và người quản lý. Bản nháp không ủy quyền đăng lên tài khoản chưa được Minh xác định.

### So sánh hai loại tài khoản để Minh chốt LI-D01

| Lựa chọn | Cần chứng minh | Điều không được suy ra |
|---|---|---|
| `PERSON` | Đúng member; app được cấp quyền đăng thay member; author URN lấy bằng cơ chế hợp lệ với product đang dùng | Không mặc định được đọc bài/comment, lấy follower/analytics hoặc programmatic refresh |
| `ORGANIZATION` | Đúng organization URN, vai trò member trên tổ chức, product/access tier và scope ứng với thao tác | Quyền admin trên Page hoặc developer app không tự chứng minh mọi API được cấp |

Tài liệu Share on LinkedIn nêu scope `w_member_social` cho đăng thay member [L1]. Posts API phân biệt `w_member_social` và `w_organization_social`; quyền đọc member có hạn chế riêng [L3]. Đây là căn cứ chọn phép thử, không phải xác nhận app của nhóm đã đủ quyền.

## 3. Phạm vi và thiết kế dự kiến

**Trong task:** kiểm kê app/account/product/quyền; LinkedIn OAuth adapter; tái dùng khung callback đã kiểm chứng; đăng bài thật có ID/URL; thử refresh khi được cấp và ghi rõ phần bị chặn; bàn giao thông tin cho Nghị làm SP-019.

**Chưa triển khai ở đây:** mọi API LinkedIn, inbox/analytics đầy đủ, My Channels production, hàng đợi hoàn chỉnh hoặc toàn bộ lifecycle token. Không chuyển AI sang backend, không nâng thư viện, không tự thay schema Room chung.

| Hạng mục | Thiết kế đề xuất / ràng buộc | Người review / chỗ chờ |
|---|---|---|
| Dependency | Dùng SP-009 và khung callback SP-010. Kiểm kê quyền LinkedIn tiến hành ngay; không phụ thuộc Meta xét duyệt. Nếu callback chưa sẵn sàng, thử API bằng công cụ riêng và ghi end-to-end còn thiếu | Minh + Đạt |
| Endpoint backend | Tái dùng route chung được chốt ở SP-010; route LinkedIn cụ thể/HTTP mapping **chưa triển khai**. Không tạo wire DTO riêng khác Android/backend | Đạt |
| OAuth provider | Authorization code flow qua backend giữ client secret; state ràng buộc provider/phiên/redirect; kiểm tra provider hỗ trợ PKCE cho product/flow đang dùng trước khi bật | Minh + Đạt; **CHỜ KIỂM TRA** |
| Handoff | Theo SP-009: mã dùng một lần TTL 60 giây; sessionChallenge/verifier; consume nguyên tử; chỉ phiên đúng được nhận token. Verifier bàn giao không thay provider PKCE | Đạt |
| Callback Android | HTTPS exchange; xác minh session đang chờ; redirect không mang token. App cold start dùng chính sách đã chốt cùng Thiện ở SP-010 | Đạt + Thiện phối hợp; reviewer chính vẫn Đạt/Nghị |
| Author identity | `accountType` PERSON/ORGANIZATION, `externalAccountId` lấy từ nguồn hợp lệ, mapping author URN được kiểm chứng. Không dùng tên hiển thị hoặc URL profile tự đoán ID | Minh + Nghị |
| Product định danh | Kiểm tra cơ chế định danh mà product được cấp hỗ trợ; nếu dùng OIDC phải chốt scope cần thiết và mapping sang author ID. Không xin email nếu không có mục đích sử dụng | Minh + Đạt |
| Publish adapter | Android gọi SocialConnector/Repository; backend phục vụ OAuth. Nội dung bài thử là snapshot được Minh duyệt; không trình bày approval thử là F4 production | Minh + Nghị |
| Kết quả bài đăng | Theo contract: SUCCESS cần ID và URL đã xác minh; có ID chưa URL giữ PUBLISHED_URL_PENDING; timeout sau có thể đã gửi → UNKNOWN, không tự retry | Minh + Nghị |
| Token/refresh | Refresh token có thể null; expiry thiếu không có nghĩa vô hạn. Backend chỉ giữ token tạm cho handoff; Android lưu an toàn. Nếu refresh cần client secret, chốt đường backend exchange với Đạt, không nhét secret vào APK | Minh + Đạt; **CHỜ CHỐT KHI BIẾT ENTITLEMENT** |

Tên biến **đề xuất, chưa được mã nguồn sử dụng**: `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET`, `LINKEDIN_OAUTH_REDIRECT_URI`, `LINKEDIN_API_VERSION`. Không đặt refresh/access token trong `.env.example`, fixture hoặc repo. Cập nhật cấu hình mẫu khi triển khai thực và giải thích cách nạp; hiện Spring không tự nạp `.env`.

## 4. Ma trận kiểm tra API/product/quyền

Mở [LinkedIn Developer Portal](https://www.linkedin.com/developers/apps), chọn đúng app. Kiểm tra Products và Auth; ghi product đang requested/approved/rejected nếu hiển thị, scope khả dụng, redirect đã đăng ký và quyền quản lý app. Sau đó kiểm tra scope/token thực nhận và từng endpoint. Không kết luận từ một ảnh consent duy nhất.

| ID | Khả năng | Đầu vào/quyền cần xác minh | App cấp / endpoint / kết quả |
|---|---|---|---|
| LI-P01 | OAuth và định danh | Product định danh, scope tối thiểu, đúng member/organization; nguồn author ID/URN hợp lệ | **CHỜ / CHỜ / NOT TESTED** |
| LI-P02 | Đăng cá nhân nếu chọn PERSON | `w_member_social`; product đã cấp, author đúng member [L1][L3] | **CHỜ / CHỜ / NOT TESTED** |
| LI-P03 | Đăng tổ chức nếu chọn ORGANIZATION | `w_organization_social`, product và vai trò tổ chức đáp ứng endpoint [L3] | **CHỜ / CHỜ / NOT TESTED** |
| LI-P04 | Tạo bài | Ứng viên `POST https://api.linkedin.com/rest/posts`; ghi `LinkedIn-Version: <đã xác minh>` và `X-Restli-Protocol-Version: 2.0.0` [L3] | **CHỜ / CHỜ / NOT TESTED** |
| LI-P05 | Đọc/xác minh bài và URL | Quyền đọc tương ứng; Post ID/URN từ response, URL mở đúng bài; không suy từ quyền tạo | **CHỜ / CHỜ / NOT TESTED** |
| LI-P06 | Programmatic refresh | App được bật cơ chế này; refresh token thực được cấp; endpoint/expiry và cách xử lý revoked/expired [L2][L4] | **CHỜ / CHỜ / NOT TESTED** |
| LI-P07 | Ảnh/alt text | Endpoint upload/đăng, media ID, trường alt text và giới hạn của version được chọn; kiểm kê khả năng, không tự mở rộng thành mọi loại media | **CHỜ / CHỜ / NOT TESTED** |
| LI-P08 | Đọc/trả lời comment | Nghị kiểm tra riêng trong SP-019 với loại tài khoản đã chọn; scope/product phụ thuộc từng endpoint | **CHỜ / CHỜ / NOT TESTED** |
| LI-P09 | Metrics/follower | Nghị kiểm tra endpoint/product/role theo author; không dùng connections thay follower hoặc điền 0 khi API không cung cấp | **CHỜ / CHỜ / NOT TESTED** |

Tài liệu Posts API mô tả response tạo bài có ID trong header `x-restli-id`; adapter cần kiểm tra response thực và tiếp tục xác minh URL [L3]. Không ghi một URL tự ghép là đã kiểm chứng chỉ vì đúng cú pháp HTTPS.

Ghi riêng khả năng tài liệu mô tả và quyền app thực có. Capability chưa thử là `UNVERIFIED`; chỉ `AVAILABLE` khi có bằng chứng và thời điểm kiểm chứng. Thiếu quyền có căn cứ → `PERMISSION_REQUIRED`; không hỗ trợ có căn cứ → `UNSUPPORTED`. Hai trạng thái này không thay cho lỗi mạng/chưa kiểm tra.

## 5. Refresh — điểm cần Minh kiểm tra kỹ nhất

LinkedIn mô tả programmatic refresh cho nhóm ứng dụng/đối tác được cấp quyền; không thể giả định mọi app OAuth đều có refresh token [L2][L4]. Tài liệu công khai không xác định app của Minh thuộc nhóm nào.

| Quan sát thực tế | Cách thử và ghi nhận | Kết luận được phép |
|---|---|---|
| Có refresh token và được phép dùng | Dùng refresh grant theo tài liệu/product; bảo vệ secret ở backend nếu yêu cầu. Kiểm tra access token mới gọi API được, ghi expiry và lỗi đã che bí mật | Chỉ ghi refresh PASS sau phép thử thành công; token mới không bắt buộc phải có chuỗi khác mới được coi hợp lệ |
| Không có refresh token | Kiểm tra product/entitlement và response; ghi “không được cấp” chỉ khi có căn cứ, nếu chưa rõ ghi “chưa xác minh” | Refresh BLOCKED hoặc NOT TESTED với lý do; không PASS |
| Token hết hạn/bị thu hồi | Kiểm tra phản hồi xác thực và luồng yêu cầu kết nối lại, giữ bản nháp; phân biệt thử giả lập và provider thật | Reconnect có thể PASS riêng; không thay refresh PASS |
| Làm lại OAuth/reauthorize thành công | Ghi phép thử kết nối lại và tài khoản/phiên mới | Không coi là programmatic refresh thành công theo tiêu chí dự án |

**F1.B3:** proposal yêu cầu ghi “Bị chặn” hoặc “Đạt một phần” nếu thiếu cơ chế refresh cần thiết; chỉ chuyển Đạt khi đã kiểm chứng hoặc có điều chỉnh yêu cầu được chấp thuận. Không tự đánh dấu N/A để vượt điều kiện. Hoàn tất kiểm kê không có nghĩa hoàn tất tiêu chí refresh.

**[MINH QUYẾT ĐỊNH — LI-D04]** Nếu thiếu quyền: người xin quyền **…**; hạn kiểm tra lại **…**; ảnh hưởng SP-019/F1.B3 **…**; nội dung cần trao đổi giảng viên/nhóm **…**; quyết định chính thức và người chấp thuận **…**. Tài liệu này không tự gửi thông báo hoặc thay đổi phạm vi.

## 6. Trình tự triển khai sau duyệt

1. Xác minh Issue/card/SP-009, nhận task và tạo nhánh SP-011 riêng từ `main` đã cập nhật. Nếu callback chung còn ở PR SP-010 chưa merge, ghi dependency; tránh copy fork khung phiên.
2. Minh kiểm kê Products/Auth, account role, scope, refresh và API version. Cùng Nghị chọn PERSON/ORGANIZATION dựa trên khả năng downstream thực tế.
3. Đăng ký redirect HTTPS và chốt cơ chế định danh author. Giữ phân biệt provider redirect backend với redirect quay lại Android.
4. Triển khai LinkedIn adapter và tái chạy bộ test phiên chung; thử sai state/provider/phiên, replay và lỗi adapter. Không sửa TTL handoff 60 giây tùy ý.
5. Chạy OAuth browser end-to-end, đối chiếu danh tính thật. Nếu chỉ thử bằng công cụ API thì ghi rõ callback app chưa được kiểm chứng.
6. Sau khi Minh xác nhận author/nội dung: đăng một bài, lưu Post ID/URN, mở URL đối chiếu. Mất response thì đối soát; không POST lại mù quáng.
7. Thử refresh nếu có quyền/token; nếu không, ghi blocker và thử reconnect riêng. Kiểm kê media và chuyển đầu vào sớm cho Nghị.
8. Chạy checks, ghi bằng chứng/giới hạn; mở PR riêng #13 cho Đạt/Nghị review. Không đóng task vì chỉ có bảng kiểm kê khi tiêu chí thật còn thiếu.

## 7. Kế hoạch kiểm thử — tất cả NOT TESTED

| ID | Ca thử | Kết quả mong đợi / bằng chứng |
|---|---|---|
| LI-T01 | App/product/scope và loại tài khoản | Bảng quyền có thời điểm/version/endpoint; author ID/URN khớp account thật |
| LI-T02 | OAuth browser → backend → Android | Nhận đúng provider và tài khoản; HTTPS, token không trong redirect/log; có video đã che bí mật |
| LI-T03 | Sai/mất state, sai phiên/provider/verifier, redirect không được phép | Từ chối, không trả token; không nhận nhầm phiên Facebook thành LinkedIn |
| LI-T04 | Handoff trước hạn, đúng 60 giây, replay và race đồng thời | Theo bộ test chung: expiry đúng boundary; tối đa một request thành công |
| LI-T05 | Từ chối consent; code lỗi/đã dùng; response provider không đủ dữ liệu | Mapping lỗi theo contract; không tạo ChannelInfo sai hoặc leak response nhạy cảm |
| LI-T06 | App đóng, backend restart, sleep/cold start | Khôi phục đúng chính sách hoặc kết nối lại; không bỏ xác minh phiên để xử lý lỗi |
| LI-T07 | Tạo bài thật trên author đã chọn | ID/URN thật, URL mở đúng author/nội dung; đầy đủ F5.B5 mới ghi SUCCESS |
| LI-T08 | Có ID chưa URL; timeout sau gửi | PUBLISHED_URL_PENDING hoặc UNKNOWN đúng contract; dừng/đối soát, không gửi trùng |
| LI-T09 | Refresh hợp lệ khi được cấp | Access token sau refresh dùng được; ghi metadata expiry và quyền; không lộ token |
| LI-T10 | Không được cấp refresh / refresh hết hạn hoặc bị thu hồi | Trạng thái chặn/lỗi đúng; yêu cầu kết nối lại và giữ bản nháp; không báo refresh PASS |
| LI-T11 | Thiếu quyền đăng hoặc sai role tổ chức | Thất bại rõ nguyên nhân; không lấy quyền PERSON làm bằng chứng ORGANIZATION |
| LI-T12 | Ảnh/alt text và quyền comment/metrics/follower | Ghi khả năng riêng; SP-019 nhận phần chưa xác minh, không suy từ đăng thành công |
| LI-T13 | Token/secret/code/verifier trong log/repo/backup | Không lộ bí mật; response test chỉ fixture giả; token reference không chứa token |

Các ca gây lỗi có thể chạy bằng fake provider/MockMvc và ghi SIMULATED; không dùng chúng thay test OAuth/post/refresh thật. Không thử đăng lặp hoặc thu hồi tài khoản ngoài tài khoản thử đã thống nhất.

Khi có code, chạy tại `android`: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; tại `backend`: `.\gradlew.bat test bootJar`; root: `git diff --check`. Thêm thiết bị/emulator cho callback và lifecycle. Chưa chạy build/test trong lượt soạn tài liệu này.

## 8. Hồ sơ bằng chứng và bàn giao

Mỗi phép thử ghi: `test ID | revision | người thử | ngày giờ + múi giờ | thiết bị | app ID | accountType/author ID | product/access tier | scope thực cấp | API version/endpoint | REAL/SIMULATED | PASS/FAIL/BLOCKED/NOT TESTED | link bằng chứng đã che bí mật | việc tiếp theo`.

| Đầu ra | Trạng thái ban đầu | Bằng chứng cần bổ sung |
|---|---|---|
| App/product/role/permission/version | NOT TESTED | Dashboard đã che bí mật và kết quả endpoint |
| OAuth và danh tính author | NOT TESTED | Callback thiết bị, ID/URN nguồn hợp lệ |
| Bảo vệ phiên/handoff | NOT TESTED | Test implementation chung + ca riêng LinkedIn |
| Bài đăng và link gốc | NOT TESTED | Post ID/URN, URL và đối chiếu tác giả/nội dung |
| Programmatic refresh | NOT TESTED | Entitlement, metadata token và API sau refresh; hoặc blocker |
| Reconnect/hết hạn/thu hồi | NOT TESTED | Kết quả riêng, không thay refresh |
| Comment/metrics/follower | NOT TESTED | Bàn giao SP-019, Nghị xác minh riêng |

Nghị nhận **accountType, author ID/URN, Post ID/URL, product/tier, scope/version, token reference, expiry, cách lấy token riêng, quyền còn thiếu**. Không gửi token trong Issue/PR. Bàn giao sớm ngày làm việc 3 theo mốc gốc, hoàn thiện ngày 4; nếu trễ phải ghi mốc thực tế và ảnh hưởng SP-019. Fixture thật có sẵn được dùng mở chặn nhưng không thay bằng chứng callback Android.

| Chỗ chờ/rủi ro | Người xử lý | Hạn | Phần tiếp tục được |
|---|---|---|---|
| Chưa chọn PERSON/ORGANIZATION | Minh + Nghị | **CHỜ MINH ĐIỀN** | Kiểm kê product và scope cả phương án |
| Chưa xác minh quyền đăng/refresh | Minh | **CHỜ MINH ĐIỀN** | Test adapter giả; chưa đạt phép thử thật |
| Callback SP-010 chưa được kiểm chứng | Minh + Đạt | **CHỜ mốc/revision** | Kiểm kê LinkedIn, thử endpoint bằng công cụ có nhãn |
| API comment/metrics/follower bị giới hạn | Minh + Nghị | **CHỜ thời điểm quyết định** | Parser/fixture; ghi dữ liệu thiếu thay vì 0 |
| Mốc W1/remote tracker chưa xác minh | Minh | Trước triển khai; **CHỜ lịch cụ thể** | Review bản nháp; không tự dời deadline |

## 9. Checklist Minh duyệt

- [ ] Chốt LI-D01–D05, nhất là loại tài khoản, bài thử và cách xử lý thiếu refresh.
- [ ] Ghi kết quả LI-C01–C06 với bằng chứng đã che bí mật; mục chưa xác minh vẫn để CHỜ.
- [ ] Đạt xác nhận bảo vệ phiên, secret, đường refresh nếu cần; Nghị xác nhận author/capability phục vụ SP-019.
- [ ] Chốt API version còn hỗ trợ; không sao chép version cũ từ ví dụ một cách mặc định.
- [ ] Có mốc bàn giao thực tế, người xử lý blocker, nơi thu bằng chứng và người giữ bí mật.
- [ ] Khi đưa vào repo chính thức, dùng PR riêng SP-011; không commit chung SP-009/SP-010. Hai approval thật, checks và merge theo CONTRIBUTING.

**Minh ghi lựa chọn PERSON/ORGANIZATION và lý do:** …

**Kết quả kiểm tra refresh/product/scope:** …

**Quyết định còn chờ / ngày xác nhận:** …

## 10. Nguồn provider đã đối chiếu khi soạn

- **[L1]** [Share on LinkedIn](https://learn.microsoft.com/en-us/linkedin/consumer/integrations/self-serve/share-on-linkedin) — quyền đăng member.
- **[L2]** [Programmatic Refresh Tokens](https://learn.microsoft.com/en-us/linkedin/shared/authentication/programmatic-refresh-tokens) — điều kiện cấp cơ chế refresh.
- **[L3]** [Posts API](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/posts-api?view=li-lms-2026-06) — endpoint, scope, version header và ID response. Version trong URL tài liệu là trang đã tham khảo, **không phải version đã chọn cho app**.
- **[L4]** [Authorization Code Flow](https://learn.microsoft.com/en-us/linkedin/shared/authentication/authorization-code-flow) — OAuth và phân biệt tái cấp quyền với programmatic refresh.
- **[L5]** [Getting Access to LinkedIn APIs](https://learn.microsoft.com/en-us/linkedin/shared/authentication/getting-access) — product và permission.

Đối chiếu tài liệu công khai ngày 08/11/2026; chưa xác minh entitlement tài khoản của nhóm. Khi thực thi phải ghi version còn hỗ trợ, scope thực cấp và kết quả endpoint trên app thật; tài liệu công khai không tự chứng minh quyền đó đã được cấp.
