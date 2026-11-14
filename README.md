# SocialPilot

SocialPilot là ứng dụng Android sử dụng AI Agent để hỗ trợ xây dựng nội dung và quản lý kênh mạng xã hội. Từ một ý tưởng chung, ứng dụng hướng đến việc tạo nội dung riêng cho **Facebook Page và LinkedIn**, cho phép người dùng chỉnh sửa, duyệt và đăng bài, sau đó theo dõi bình luận và hiệu quả hoạt động.

Dự án được thực hiện dưới hình thức đồ án môn học bởi nhóm **4 thành viên** trong **6 tuần**. Ngôn ngữ phát triển chính là **Java** cho ứng dụng Android và backend OAuth.

## Bối cảnh và đối tượng sử dụng

Người sáng tạo nội dung và người quản lý truyền thông phải tìm chủ đề, viết bài, chuẩn bị ảnh, điều chỉnh nội dung cho từng nền tảng, xử lý bình luận và tổng hợp số liệu bằng nhiều công cụ khác nhau. SocialPilot tập trung các bước đó vào một quy trình, đồng thời giữ quyền quyết định xuất bản ở người dùng.

Ứng dụng hướng đến người sáng tạo nội dung cá nhân, câu lạc bộ, nhóm dự án và đơn vị nhỏ. Phiên bản đồ án phục vụ một người dùng trên mỗi bản cài đặt, quản lý các kênh mà người đó có quyền truy cập.

## Mục tiêu

- Tạo bài viết và hình ảnh theo từng kênh từ một brief chung.
- Cho phép chỉnh sửa và duyệt từng phiên bản trước khi đăng.
- Quản lý bình luận, hỗ trợ soạn câu trả lời và tự trả lời trong phạm vi được xác nhận.
- Theo dõi tăng trưởng và gợi ý nội dung dựa trên dữ liệu đã lưu.
- Hỗ trợ API key cá nhân, lựa chọn nhà cung cấp AI và kiểm soát chi phí.
- Lưu bản nháp, dữ liệu đã đồng bộ và hàng đợi đăng để hỗ trợ sử dụng khi mất mạng.

## Quy trình sử dụng dự kiến

Nhập topic hoặc URL → tạo brief → AI tạo nội dung riêng cho hai kênh → người dùng chỉnh sửa và duyệt → đăng bài → theo dõi bình luận và số liệu → nhận gợi ý cho nội dung tiếp theo.

Bài viết phải được người dùng duyệt trước khi đưa vào hàng đợi đăng; thay đổi nội dung hoặc ảnh sau khi duyệt sẽ yêu cầu duyệt lại.

## Phạm vi chức năng

| Mã | Nhóm chức năng | Nội dung dự kiến |
|---|---|---|
| F1 | Kết nối mạng xã hội | OAuth, danh sách kênh, quyền truy cập và vòng đời token |
| F2 | AI Agent | Hai nhánh theo kênh, viết bài, sinh ảnh, stream, hủy và nhật ký |
| F3 | Topic và nguồn nội dung | Nhập brief, URL, nhận link chia sẻ và gợi ý topic |
| F4 | Duyệt và chỉnh sửa | Preview, sửa bài, phiên bản, ba phương án mở đầu và alt text |
| F5 | Đăng đa kênh | Hàng đợi offline, chống trùng, thử lại và đối soát kết quả |
| F6 | Bình luận và trả lời | Inbox hợp nhất, phân loại, gợi ý trả lời và tự trả lời có giới hạn |
| F7 | Phân tích tăng trưởng | Snapshot, dashboard, báo cáo tuần và mục tiêu |
| F8 | BYOK và chi phí AI | OpenAI/Gemini, bảo vệ key, chọn model, ngân sách và provider dự phòng |
| F9 | Cài đặt và offline | Tiếng Việt/Anh, bản nháp và dữ liệu đã lưu |

Phạm vi nghiệm thu gồm **44 yêu cầu bắt buộc**: 33 yêu cầu `[B]` và 11 yêu cầu `[N]` không có tag Optional. Hai hạng mục mở rộng là nhập topic bằng ảnh và thông báo comment mới kèm mở đúng comment. Xem [ma trận yêu cầu](docs/requirements.md).

Phiên bản đồ án không bao gồm iOS, video/Reels, quảng cáo trả phí, phân quyền nhóm hoặc đồng bộ nhiều thiết bị. Các khả năng tích hợp thật phụ thuộc quyền API được Facebook và LinkedIn cấp; cần kiểm chứng từ tuần đầu.

## Công nghệ và kiến trúc

| Thành phần | Công nghệ |
|---|---|
| Android | Java, XML Views, Material Components, View Binding |
| Kiến trúc giao diện | ViewModel, LiveData, Repository |
| Bất đồng bộ | ExecutorService, Future và callback |
| Dữ liệu và tác vụ nền | Room 2.x, WorkManager |
| Mạng | Retrofit, OkHttp |
| Backend OAuth | Java, Spring Boot, Spring Web |
| Kiểm thử | JUnit, MockWebServer, Espresso, Spring Boot Test/MockMvc |
| Phát triển | Git, Gradle Wrapper, GitHub Actions |

Android quản lý giao diện, nghiệp vụ và dữ liệu cục bộ. API AI được gọi trực tiếp từ thiết bị đến nhà cung cấp; backend chỉ hỗ trợ OAuth cần giữ client secret phía server. Xem [tài liệu kiến trúc](docs/architecture.md).

## Trạng thái hiện tại

Project đang ở giai đoạn khởi tạo nền tảng:

- Đã có cấu trúc Android/backend, cấu hình build và Gradle Wrapper riêng.
- Đã có màn hình nhập topic, kiểm tra đầu vào, lưu bản nháp bằng Room và danh sách gần đây.
- Đã có tài nguyên giao diện VI/EN theo ngôn ngữ thiết bị và các interface nghiệp vụ để mở rộng.
- Backend có health check. SP-010 đã ghi nhận kết nối Facebook OAuth trên điện thoại thật và một bài Page thật qua Graph API Explorer; xem [trạng thái và bằng chứng SP-010](docs/drafts/sp-010-implementation-status.md). State/replay/TTL/race có test hồi quy với provider giả; vòng đời token và publish từ hàng đợi ứng dụng còn chưa nghiệm thu.
- Đã có bộ kiểm thử nền tảng và cấu hình CI; xem [kết quả kiểm tra](docs/build-verification.md).

SP-011 bổ sung code OAuth LinkedIn cá nhân, kho hai kết nối và harness duyệt/đăng chữ, với gate local đã đạt; kiểm thử trên thiết bị và API thật qua ứng dụng còn chờ. Xem [hồ sơ SP-011](docs/sp-011-implementation.md).

OAuth Facebook cơ bản đã được kiểm chứng trong spike SP-010; luồng publish production trong app, AI/BYOK, sinh ảnh, duyệt phiên bản, hàng đợi đăng, inbox, analytics và lựa chọn ngôn ngữ còn cần triển khai. Các nhóm chức năng F1–F9 chưa được nghiệm thu đầy đủ.

## Cấu trúc thư mục

```text
SocialPilot/
├── android/              # Ứng dụng Android Java/XML
├── backend/              # Dịch vụ OAuth Java/Spring Boot
├── docs/                 # Proposal, kiến trúc, yêu cầu và hướng dẫn
└── .github/workflows/    # Build và kiểm thử CI
```

## Bắt đầu phát triển

1. Chuẩn bị JDK 21 và Android SDK Platform 36.
2. Mở thư mục `android/` trong Android Studio, chọn Gradle JDK 21 và chạy trên emulator/thiết bị API 23 trở lên.
3. Khi cần backend, mở terminal tại `backend/` và chạy `./gradlew.bat bootRun` trên Windows.
4. Kiểm tra backend tại [API health](http://127.0.0.1:8080/api/health).

**`bootRun` chạy liên tục để giữ server hoạt động.** Khi log hiện `Started OAuthApplication` và `Tomcat started on port 8080`, backend đã sẵn sàng. Gradle có thể tiếp tục hiển thị `80% EXECUTING`; không cần chờ lên 100%. Dùng `Ctrl+C` để dừng server.

Các lệnh build, kiểm thử, cấu hình và xử lý lỗi nằm trong [hướng dẫn phát triển](docs/development.md).

Trước khi nhận task hoặc tạo nhánh, đọc [quy tắc và convention chung](CONTRIBUTING.md): đặt tên nhánh/commit, quy trình PR và review, ranh giới phối hợp, kiểm thử và điều kiện hoàn tất.

Theo hướng dẫn giảng viên do nhóm cung cấp, bắt buộc dùng **GitHub Project + Repo**; mỗi ticket triển khai có assignee, nhánh riêng và PR về `main`, được ít nhất **2 thành viên khác review và approve** trước khi merge. Báo cáo tuần trên Slack channel `2024_31_md_<ma_nhom>` gồm Current tasks, Next plan, Blockers và người phụ trách. Nhóm tạm chọn gửi trước cuối thứ Bảy, chờ làm rõ mốc thứ Bảy/Chủ nhật trong hướng dẫn. Xem chi tiết và mẫu báo cáo trong CONTRIBUTING.

Giảng viên cho lựa chọn các mục `[N]`; 11 mục nâng cao trong phạm vi 44 yêu cầu là phần nhóm đã chọn cam kết. Backend OAuth là lựa chọn kỹ thuật của nhóm, không phải yêu cầu bắt buộc của môn học.

## Kế hoạch và phân công

Kế hoạch nội bộ gồm 6 tuần, tuần 1 là 02–08/11/2026 (thứ Hai–Chủ nhật). Demo tích hợp cuối tuần 5 (06/12/2026); tuần 6 (07–13/12/2026) kiểm thử, sửa lỗi, nghiệm thu và hoàn thiện bàn giao. Mỗi người cam kết 24 giờ/tuần, gồm thiết kế, lập trình, review, họp, tích hợp, kiểm thử và tài liệu: 96 giờ/nhóm/tuần, 480 giờ đến demo và 576 giờ toàn đợt. Demo không thay nghiệm thu 44 yêu cầu; hạn nộp/bảo vệ chính thức theo thông báo giảng viên.

Không lên kế hoạch hai Optional trong đợt 6 tuần này.

| Thành viên | Trách nhiệm chính |
|---|---|
| TV1 — Ngô Gia Minh | F1, F5, backend OAuth; hợp đồng kết nối mạng xã hội và điều phối tích hợp kỹ thuật |
| TV2 — Nguyễn Đức Đạt | F2, F8; nền tảng AI, BYOK, ngân sách, hợp đồng gọi AI dùng chung và điều phối schema Room/migration chung |
| TV3 — Đỗ Phước Thiện | F3, F4; điều phối F9; giao diện/ViewModel F7.B1, F7.B2, F7.B3, F7.N1, F7.N3; vận hành CI và đóng gói Android |
| TV4 — Tô Lãng Nghị | F6; dữ liệu, đồng bộ, tính toán và báo cáo F7; API comment/số liệu của hai nền tảng |

Mỗi người triển khai giao diện, nghiệp vụ, lưu offline, VI/EN, kiểm thử và tài liệu cho phần mình nhận. TV3 điều phối trải nghiệm chung, không làm toàn bộ giao diện hoặc offline của nhóm. TV3 và TV4 tự xây dựng nghiệp vụ AI của module mình qua dịch vụ TV2 cung cấp; TV4 trực tiếp làm API comment/số liệu trên hợp đồng thống nhất với TV1. TV1 điều phối tích hợp, mỗi chủ task chịu trách nhiệm tích hợp và sửa lỗi phần mình.

Tuần 1 kiểm chứng stack, quyền API và chốt hợp đồng dùng chung; tuần 2–3 hoàn thiện luồng tạo–duyệt–đăng, đồng thời TV4 xây dựng dữ liệu/đồng bộ và TV3 dựng khung dashboard; tuần 4–5 hoàn thiện tương tác, analytics và các yêu cầu nâng cao; demo cuối tuần 5; tuần 6 kiểm thử, sửa lỗi và nghiệm thu. Snapshot thật bắt đầu từ tuần 2–3 khi có quyền API, tuần 4 dùng lịch sử đã tích lũy; thiếu quyền ghi blocker. Chi tiết về ranh giới, bàn giao và việc chung trong [proposal, mục 4](docs/SocialPilot_Proposal_Java.md#4-kế-hoạch-triển-khai-và-phân-công).

TV4 — Tô Lãng Nghị sở hữu API, snapshot, repository, công thức, lưu mục tiêu/tính tiến độ và F7.N2 (nghiệp vụ cùng giao diện báo cáo AI). TV3 — Đỗ Phước Thiện sở hữu XML/ViewModel F7.B1 (số liệu từng bài), F7.B2 (đường tăng trưởng), F7.B3 (dashboard), F7.N1 (phân tích theo nhóm), F7.N3 (mục tiêu); không tính lại chỉ số trong UI. Snapshot thật bắt đầu từ tuần 2–3 khi có quyền API, tuần 4 dùng lịch sử đã tích lũy; thiếu quyền phải ghi blocker, giữ khoảng trống dữ liệu.

Đạt điều phối schema/migration theo CONTRIBUTING mục 6; các owner vẫn thiết kế entity/DAO và kiểm thử phần mình.

Có thể chuyển task theo tải thực tế mà không đổi phạm vi 44 yêu cầu bắt buộc. Mỗi task có một người chịu trách nhiệm chính, người review, đầu ra và hạn bàn giao; cập nhật bảng task cùng báo cáo đóng góp khi thay đổi. Phân công không yêu cầu chia đều tuyệt đối, nhưng phải tính cả tích hợp, kiểm thử và tài liệu để tránh dồn việc vào một số vai trò.

## Nguyên tắc bảo vệ dữ liệu

Không commit API key, client secret, `local.properties` hoặc keystore. Khi triển khai BYOK, key/token phải được mã hóa bằng Android Keystore và loại khỏi log, backup, dữ liệu xuất. Client secret của mạng xã hội nằm phía backend; API key AI chỉ được gửi đến nhà cung cấp tương ứng. Bản triển khai OAuth công khai cần HTTPS.
