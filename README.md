# SocialPilot

SocialPilot là ứng dụng Android sử dụng AI Agent để hỗ trợ xây dựng nội dung và quản lý kênh mạng xã hội. Từ một ý tưởng chung, ứng dụng hướng đến việc tạo nội dung riêng cho **Facebook Page và LinkedIn**, cho phép người dùng chỉnh sửa, duyệt và đăng bài, sau đó theo dõi bình luận và hiệu quả hoạt động.

Dự án được thực hiện dưới hình thức đồ án môn học bởi nhóm **4 thành viên** trong **7 tuần**. Ngôn ngữ phát triển chính là **Java** cho ứng dụng Android và backend OAuth.

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
- Backend có health check; endpoint bắt đầu OAuth trả `501 OAUTH_NOT_CONFIGURED` vì chưa triển khai adapter.
- Đã có bộ kiểm thử nền tảng và cấu hình CI; xem [kết quả kiểm tra](docs/build-verification.md).

OAuth thật, AI/BYOK/Keystore, sinh ảnh, duyệt phiên bản, hàng đợi đăng, inbox, analytics và lựa chọn ngôn ngữ trong app còn cần triển khai. Các nhóm chức năng F1–F9 chưa được nghiệm thu đầy đủ.

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

## Kế hoạch và phân công

| Thành viên | Trách nhiệm chính |
|---|---|
| TV1 | F1, F5, backend OAuth, cấu hình và tích hợp bản phát hành |
| TV2 | F2, F8, AI Agent, BYOK và hợp đồng AI dùng chung |
| TV3 | F3, F4, F9, soạn thảo và trải nghiệm sử dụng |
| TV4 | F6, F7, bình luận và phân tích tăng trưởng |

Tuần 1 kiểm chứng stack và quyền API; tuần 2–3 hoàn thiện luồng tạo, duyệt và đăng; tuần 4–5 triển khai tương tác, dữ liệu và các yêu cầu nâng cao; tuần 6 nghiệm thu; tuần 7 ổn định, báo cáo và bảo vệ. Chi tiết trong [proposal](docs/SocialPilot_Proposal_Java.docx).

## Nguyên tắc bảo vệ dữ liệu

Không commit API key, client secret, `local.properties` hoặc keystore. Khi triển khai BYOK, key/token phải được mã hóa bằng Android Keystore và loại khỏi log, backup, dữ liệu xuất. Client secret của mạng xã hội nằm phía backend; API key AI chỉ được gửi đến nhà cung cấp tương ứng. Bản triển khai OAuth công khai cần HTTPS.
