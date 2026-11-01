# **Proposal dự án SocialPilot**

## *Ứng dụng Android sử dụng AI Agent hỗ trợ xây dựng và quản lý kênh mạng xã hội*

**Loại dự án:** Đồ án môn học, nhóm 4 thành viên.

**Thời gian:** 7 tuần, hướng đến hoàn thiện chức năng trong 6 tuần và dành tuần cuối cho kiểm thử, sửa lỗi, báo cáo và bảo vệ.

**Nguồn lực:** 15–20 giờ/người/tuần, tương đương 420–560 giờ công trong 7 tuần.

Nền tảng: Android native bằng Java, giao diện XML Views và Material Components; backend OAuth bằng Java với Spring Boot.

**Mạng xã hội tích hợp:** Facebook Page và LinkedIn.

Phiên bản rà soát ngày 30 tháng 10 năm 2026\. Phạm vi bắt buộc gồm 44 yêu cầu: 33 mục \[B\] và 11 mục \[N\] không có tag Optional đã được lựa chọn. Chỉ hai mục có tag Optional là mở rộng: nhập topic bằng ảnh và thông báo comment mới kèm mở đúng comment. Ma trận tại mục 5.1 quy định cách kiểm chứng từng yêu cầu.

Quyết định công nghệ: Java phù hợp với các chức năng F1–F9 khi nhóm nắm vững Java và Android Views. Bộ công nghệ được đồng bộ cho Android và backend OAuth; khả năng hoàn thành trong 7 tuần vẫn phải được kiểm chứng bằng bản mẫu kỹ thuật, ước lượng công việc và quyền API thực tế ở tuần 1\.

# **1 Tổng quan dự án**

## **1 1 Bối cảnh và vấn đề**

Việc xây dựng kênh mạng xã hội đòi hỏi người quản lý thực hiện nhiều công việc: tìm chủ đề, viết nội dung, chuẩn bị hình ảnh, điều chỉnh bài theo từng nền tảng, đăng bài, trả lời bình luận và đánh giá hiệu quả.

Khi quản lý đồng thời nhiều kênh, các công việc này dễ bị lặp lại và phân tán. Công cụ sinh nội dung đơn lẻ cũng chưa giải quyết đầy đủ quy trình từ ý tưởng đến tương tác và đo lường.

SocialPilot đề xuất một ứng dụng Android tập trung các hoạt động trên, sử dụng AI Agent để hỗ trợ người dùng nhưng vẫn duy trì quyền kiểm soát đối với nội dung được xuất bản.

## **1 2 Mục tiêu**

* Tạo bài viết và hình ảnh phù hợp với từng kênh từ một brief chung.  
* Cho phép người dùng duyệt, chỉnh sửa và đăng bài lên hai nền tảng.  
* Tập trung bình luận, hỗ trợ trả lời và tự động hóa có giới hạn.  
* Theo dõi tăng trưởng, phân tích hiệu quả và gợi ý nội dung tiếp theo.  
* Cho phép sử dụng API key cá nhân, kiểm soát nhà cung cấp, model và chi phí.  
* Duy trì khả năng xem dữ liệu đã lưu và xếp hàng đăng bài khi mất mạng.

## **1 3 Đối tượng sử dụng**

Người sáng tạo nội dung cá nhân, người quản lý truyền thông cho câu lạc bộ, nhóm dự án hoặc đơn vị nhỏ cần quản lý Facebook Page và LinkedIn.

Bản đồ án phục vụ **một người dùng trên mỗi bản cài đặt**, quản lý các kênh mà người đó có quyền truy cập. Việc nhóm có 4 thành viên là quy mô phát triển, không đồng nghĩa sản phẩm phải có chức năng cộng tác nhiều người.

## **1 4 Nguyên tắc xác định phạm vi**

* \[B\] là yêu cầu bắt buộc theo tài liệu yêu cầu chức năng. Cả 33 yêu cầu \[B\] phải có bằng chứng nghiệm thu; thiếu quyền API không tự động miễn yêu cầu.  
* \[N\] là nhãn chức năng nâng cao, không đồng nghĩa tùy chọn. Cả 11 yêu cầu \[N\] không có tag Optional đã được lựa chọn và là phần bắt buộc của dự án, có cùng trách nhiệm triển khai và nghiệm thu như 33 yêu cầu \[B\].  
* Nhập topic bằng ảnh là hạng mục \[N\] được ghi Optional và chỉ thực hiện khi đủ điều kiện mở rộng.  
* Thông báo comment mới và mở đúng comment là hạng mục \[N\] có tag Optional, thuộc phần mở rộng. Nếu triển khai, chọn đồng bộ nền kết hợp thông báo cục bộ; không đưa chức năng này vào điều kiện nghiệm thu 44 yêu cầu bắt buộc.  
* Không bổ sung iOS, video/Reels, quảng cáo trả phí, phân quyền nhóm hoặc đồng bộ nhiều thiết bị trong phiên bản đồ án.

Kế hoạch và nguồn lực phải bảo đảm đủ 44 yêu cầu bắt buộc, gồm cả các \[N\] đã chọn. Chỉ triển khai hai mục Optional sau khi hoàn thành phạm vi bắt buộc và đủ điều kiện mở rộng. Không tự hạ các \[N\] không có Optional thành tùy chọn vì thiếu thời gian; mọi thay đổi phạm vi cần được người đặt yêu cầu chấp thuận.

# **2 Phạm vi chức năng và kết quả mong đợi**

## **F1 Kết nối và quản lý mạng xã hội**

**Chức năng:**

* Kết nối Facebook Page và LinkedIn qua OAuth bằng trình duyệt hệ thống.

Diễn giải phạm vi F1: tích hợp hai nền tảng khác nhau là Facebook Page và LinkedIn. Tuần 1 phải chốt loại tài khoản LinkedIn dùng cho demo (cá nhân hoặc tổ chức), quyền quản trị và từng khả năng API. Một luồng OAuth thành công không chứng minh đã có quyền đăng, đọc/trả lời comment hoặc lấy follower. Không thay hai nền tảng bằng hai tài khoản cùng nền tảng mà không điều chỉnh phạm vi được chấp thuận.

* Hiển thị danh sách kênh, tài khoản/Page đang sử dụng và trạng thái kết nối.  
* Hiển thị khả năng thực tế của từng kênh: đăng bài, đọc/trả lời comment, mention và số liệu.  
* Quản lý vòng đời token; làm mới khi nền tảng và quyền ứng dụng hỗ trợ; yêu cầu kết nối lại khi cần.  
* Ngắt kết nối, dừng tác vụ chờ/đồng bộ/tự trả lời và xóa token cùng dữ liệu cục bộ gắn với kênh: bài, comment, snapshot, ảnh và log liên quan; giữ dữ liệu độc lập hoặc của kênh khác.

Trước khi xóa, hiển thị rõ dữ liệu bị ảnh hưởng và cho phép xác nhận. Xóa liên kết kênh không có nghĩa xóa bài đã công bố trên mạng xã hội. Bản nháp chung còn dùng cho kênh khác chỉ được gỡ liên kết kênh đã xóa; kết quả trả về muộn của tác vụ đã hủy không được khôi phục dữ liệu kênh.

**Kết quả mong đợi:** Người dùng biết rõ kênh nào đang hoạt động và chức năng nào có thể sử dụng.

**Giới hạn cần kiểm chứng:** LinkedIn không cấp khả năng làm mới token theo chương trình cho mọi ứng dụng. Đây là phụ thuộc quyền truy cập cần kiểm chứng ở tuần 1\. [Tài liệu LinkedIn về refresh token](https://learn.microsoft.com/en-us/linkedin/shared/authentication/programmatic-refresh-tokens)

Tiêu chí F1.B3: tự làm mới token khi được cấp cơ chế tương ứng; khi token hết hạn hoặc bị thu hồi, báo kết nối lại và giữ bản nháp. Nếu ứng dụng không được cấp cơ chế refresh cần thiết, ghi yêu cầu là “Bị chặn” hoặc “Đạt một phần”; chỉ chuyển “Đạt” khi đã kiểm chứng hoặc có điều chỉnh yêu cầu được chấp thuận.

## **F2 AI Agent viết bài và sinh ảnh**

**Chức năng:**

* Orchestrator tiếp nhận topic, chuẩn hóa brief và chạy song song hai sub-agent tương ứng hai kênh.  
* Mỗi sub-agent thực hiện đủ chuỗi: **Dàn ý → Bản nháp → Rà soát quy tắc kênh → Mô tả ảnh → Sinh ảnh → Đóng gói kết quả.**  
* Điều chỉnh giọng văn, cấu trúc, kích thước và phong cách ảnh theo kênh.  
* Dùng template dựng ảnh khi không có API sinh ảnh khả dụng.  
* Hiển thị tiến độ từng bước và stream văn bản trong quá trình viết.  
* Hủy tác vụ; lỗi một kênh không làm mất kết quả của kênh còn lại; chạy lại riêng kênh lỗi.  
* Lưu nhật ký gồm bước thực hiện, prompt, kết quả, trạng thái, thời gian, token và chi phí.

Log được xem theo phiên và theo kênh; lưu prompt, đầu ra từng bước, usage và chi phí ước tính. Che key/token trong mọi request, lỗi và log; người dùng có thể xóa log. Hủy một kênh chỉ hủy nhánh đó, hủy phiên dừng mọi nhánh; vẫn giữ kết quả đã hoàn thành và ghi nhận phần chi phí đã phát sinh.

**Kết quả mong đợi:** Một brief tạo ra hai bộ nội dung riêng biệt, có thể chỉnh sửa và duyệt độc lập.

Nhật ký lưu các đầu vào, đầu ra và kết quả kiểm tra có cấu trúc; không yêu cầu model cung cấp chuỗi suy luận nội bộ.

## **F3 Nhập và gợi ý topic**

**Phạm vi chính:**

* Nhập văn bản cùng mục tiêu, đối tượng, giọng văn, ngôn ngữ VI/EN và kênh đích.  
* Nhập URL, trích xuất nội dung bài công khai và tạo bài chia sẻ có dẫn nguồn.  
* Nhận link từ ứng dụng khác qua Android Sharesheet.  
* Gợi ý topic từ hai nguồn: bài có hiệu quả tốt trong dữ liệu đã lưu và các bài mới từ nguồn RSS/Atom được cấu hình.  
* Mỗi gợi ý hiển thị nguồn hoặc lý do đề xuất để người dùng đánh giá.

**Giới hạn triển khai:** Không vượt đăng nhập hoặc paywall để lấy bài. Khi không đọc được URL, báo nguyên nhân và cho người dùng nhập nội dung thủ công. “Xu hướng” được giới hạn trong các nguồn theo dõi, không tuyên bố bao quát toàn bộ mạng xã hội.

**Mở rộng nếu kịp:** Nhập ảnh slide/whiteboard, trích xuất nội dung và chuyển thành brief.

## **F4 Duyệt và chỉnh sửa bài**

**Chức năng:**

* Không đăng bài khi chưa có người dùng duyệt.  
* Xem trước riêng cho Facebook và LinkedIn.  
* Sửa văn bản, đếm ký tự và kiểm tra giới hạn theo kênh.  
* Viết lại một đoạn theo yêu cầu; sinh lại ảnh.  
* Lưu, xem và khôi phục phiên bản bản nháp.  
* Sinh ba phương án câu mở đầu để lựa chọn.  
* Tự sinh alt text; cho phép chỉnh sửa và gửi lên nền tảng khi API hỗ trợ.

**Quy tắc duyệt:** Việc duyệt gắn với một phiên bản cụ thể. Nếu nội dung hoặc hình ảnh thay đổi sau khi duyệt, phiên bản mới phải được duyệt lại.

**Kết quả mong đợi:** Người dùng kiểm soát chính xác nội dung được đưa vào hàng đợi đăng.

## **F5 Đăng bài đa kênh và hàng đợi offline**

**Chức năng:**

* Đăng một bài đã duyệt lên nhiều kênh; theo dõi kết quả riêng từng kênh.  
* Hỗ trợ trạng thái: nháp, chờ, đang đăng, đã đăng, lỗi, đã bàn giao.  
* Khi mất mạng, lưu yêu cầu đăng vào hàng đợi bền vững.  
* Khi có mạng và hệ điều hành cho phép chạy, tiếp tục xử lý hàng đợi.  
* Chống tạo yêu cầu trùng khi người dùng bấm nhiều lần hoặc ứng dụng thử lại.  
* Thử lại có giãn cách đối với lỗi mạng/rate limit; thông báo rõ lỗi quyền và lỗi nội dung.  
* Lưu mã bài, đường dẫn và thời điểm đăng thành công.

**Xử lý trường hợp khó:** Nếu kết nối bị ngắt sau khi đã gửi yêu cầu nhưng chưa nhận kết quả, hệ thống đối soát trước khi thử lại. Nếu không thể xác định bài đã được tạo hay chưa, chuyển sang trạng thái cần kiểm tra thay vì đăng lại mù quáng.

Khóa chống trùng được tạo theo kênh, bản nháp và phiên bản đã duyệt; hàng đợi khóa xử lý nguyên tử và giữ khóa qua lần khởi động lại. “Cần kiểm tra” là lý do phụ của trạng thái lỗi, không phải trạng thái thành công. Chỉ tạo lượt đăng mới khi người dùng chủ động xác nhận. Phép thử chống trùng phải kiểm tra số bài thực tế ở kênh đích, không chỉ đếm yêu cầu trong ứng dụng.

**Trạng thái “đã bàn giao”:** Chỉ dùng khi người dùng chủ động chuyển nội dung sang ứng dụng mạng xã hội để hoàn tất thủ công; không được tính là đăng thành công qua API.

Hàng đợi chỉ nhận phiên bản đã duyệt. Lỗi quyền/nội dung dừng để người dùng xử lý; lỗi mạng/rate limit dùng backoff, tuân thủ thời gian chờ do API trả về nếu có và giới hạn số lần thử. Hết số lần thử chuyển lỗi có thể xử lý thủ công; không tự thử lại trường hợp chưa rõ bài đã được tạo hay chưa.

## **F6 Hộp thư comment và trả lời**

**Chức năng:**

* Hợp nhất comment từ hai kênh; lọc theo kênh, bài và trạng thái xử lý.  
* Kéo để làm mới và đồng bộ nền.  
* AI phân loại: câu hỏi, khen, góp ý, tiêu cực, spam.  
* AI đề xuất câu trả lời theo bài viết và giọng văn; người dùng sửa trước khi gửi.  
* Có chế độ tự trả lời giới hạn, mặc định tắt.  
* Mở rộng Optional: thông báo comment mới; chạm thông báo mở đúng comment.

**Quy tắc tự trả lời:**

* Chỉ áp dụng cho lời khen và câu hỏi khớp bộ FAQ đã được người dùng xác nhận.  
* Không tự gửi khi comment tiêu cực, có thông tin cá nhân hoặc kết quả không đủ chắc chắn.  
* Mỗi comment chỉ được tự trả lời một lần.  
* Giới hạn mặc định 10 câu trả lời tự động/kênh/ngày; người dùng có thể giảm hoặc tắt.  
* Lưu lịch sử để người dùng biết câu nào được trả lời tự động.

Điều kiện tự gửi phải được kiểm tra lại ngay trước khi gửi: người dùng còn bật chế độ, còn hạn mức, comment chưa được trả lời và vẫn thuộc nhóm được phép. Bộ FAQ do người dùng xác nhận được quản lý riêng. Dùng bộ kiểm thử có nhãn để chọn ngưỡng tin cậy; chưa hiệu chỉnh hoặc không chắc chắn thì chuyển người dùng duyệt. Không dùng một con số tin cậy do model tự khai làm điều kiện duy nhất.

**Giải pháp cho phần mở rộng Optional:** WorkManager đồng bộ nền kết hợp thông báo cục bộ. Đây là thông báo sau khi phát hiện dữ liệu mới, không cam kết thời gian thực. Android cho phép trì hoãn tác vụ định kỳ do các cơ chế tiết kiệm pin. [Tài liệu WorkManager](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)

Đồng bộ bắt buộc dùng mã comment của nền tảng để chống trùng, lưu con trỏ/thời điểm đồng bộ và xử lý phân trang. Phạm vi thu thập là các bài ứng dụng có quyền đọc trên kênh đã kết nối; khoảng lịch sử ban đầu phải được cấu hình và hiển thị rõ. Riêng phần thông báo Optional, nếu triển khai, phải tra đúng kênh, bài và comment khi mở; comment đã xóa hoặc kênh đã ngắt phải có thông báo phù hợp.

## **F7 Theo dõi và phân tích tăng trưởng**

**Chức năng:**

* Hiển thị lượt thích, comment, chia sẻ, lượt hiển thị nếu có và tỷ lệ tương tác.  
* Lưu snapshot từng bài và follower theo kênh ít nhất theo lịch một lần/ngày khi thiết bị đủ điều kiện; hỗ trợ làm mới thủ công, lưu thời điểm thực tế và giữ khoảng trống khi bỏ lỡ lịch.  
* Dashboard gồm follower, tương tác theo tuần, top bài và so sánh kênh.  
* Phân tích theo chủ đề, định dạng và giờ đăng.  
* AI viết báo cáo tuần: biến động, diễn giải và đề xuất tuần tiếp theo.  
* Đặt mục tiêu follower hoặc tương tác và theo dõi tiến độ.

**Quy ước dữ liệu:**

* Số liệu không được API cung cấp hiển thị “Không khả dụng”, không thay bằng 0\.  
* Mỗi chỉ số có thời điểm cập nhật.  
* Tỷ lệ tương tác theo lượt hiển thị \= (lượt thích hoặc reaction \+ comment \+ chia sẻ) / lượt hiển thị × 100%, với các thành phần cùng phạm vi dữ liệu và thời điểm. Thiếu thành phần hoặc mẫu số bằng 0 thì hiển thị “Không khả dụng”; không suy ra số 0\. Chỉ so sánh kênh có cùng định nghĩa và khoảng thời gian.  
* Báo cáo AI dựa trên số liệu đã lưu; nguyên nhân tăng/giảm chỉ được trình bày là giả thuyết nếu dữ liệu chưa đủ chứng minh.  
* Khi chưa đủ dữ liệu, hiển thị trạng thái rõ ràng thay vì tạo nhận xét hoặc biểu đồ giả.

Dashboard dùng múi giờ đã cấu hình, tuần từ thứ Hai đến Chủ nhật. Tương tác theo tuần là chênh lệch bộ đếm giữa hai snapshot hợp lệ trên cùng tập bài; nếu chỉ có một phần tuần thì ghi rõ khoảng đo. Top bài dùng chỉ số và khoảng đo được người dùng chọn. Số đếm giảm do nội dung bị xóa hoặc điều chỉnh được giữ kèm chú thích, không ép thành 0\. Follower phải là số follower của kênh, không thay bằng số kết nối cá nhân. Dữ liệu không khả dụng vì thiếu quyền phải được ghi nhận trong kết quả nghiệm thu F7.

## **F8 BYOK và kiểm soát chi phí AI**

**Chức năng:**

* Hỗ trợ hai nhà cung cấp: OpenAI và Gemini.  
* Nhập, kiểm tra, che và xóa API key.  
* Mã hóa API key bằng khóa bảo vệ bởi Android Keystore; lưu dữ liệu mã hóa trong vùng riêng của ứng dụng và loại khỏi sao lưu/xuất dữ liệu.  
* Gọi API AI trực tiếp từ thiết bị; không gửi key đến backend của nhóm.  
* Chọn model phù hợp cho viết bài, phân loại comment và sinh ảnh.  
* Theo dõi token và chi phí theo ngày/tháng.  
* Đặt trần ngân sách ngày/tháng; khi chạm một trong hai trần, dừng agent ở bước gọi AI kế tiếp, chặn mọi lệnh gọi AI mới và yêu cầu hủy lệnh đang chạy khi nhà cung cấp hỗ trợ.  
* Tự chuyển nhà cung cấp khi gặp lỗi khả dụng và đã có cấu hình dự phòng hợp lệ.

**Quy tắc triển khai:**

* Model được lựa chọn theo khả năng hỗ trợ từng tác vụ, không giả định model văn bản luôn sinh được ảnh.  
* Khi hai sub-agent chạy song song, hệ thống giữ trước ngân sách ước tính để tránh cùng sử dụng phần ngân sách còn lại.  
* Chi phí hiển thị là ước tính theo usage và bảng giá cấu hình, không phải hóa đơn chính thức.  
* Không chuyển nhà cung cấp để vượt trần ngân sách.  
* Bộ đếm chi phí chỉ phản ánh các lệnh gọi qua ứng dụng trên thiết bị này.

Ngân sách áp dụng chung cho tất cả provider và mọi tác vụ AI, kể cả viết lại, phân loại, sinh ảnh, báo cáo và retry. Giữ trước chi phí theo giới hạn đầu ra trước mỗi lần gọi, cập nhật usage khi có và quyết toán sau khi kết thúc. Khi chạm trần, lưu kết quả từng phần và lý do dừng; không chuyển provider để tiếp tục. Phần đã xử lý tại nhà cung cấp vẫn có thể bị tính phí, nên đây là trần điều phối của ứng dụng, không phải bảo đảm hóa đơn tuyệt đối. Ngày/tháng tính theo múi giờ cấu hình và được hiển thị cho người dùng.

## **F9 Cài đặt và sử dụng offline**

**Chức năng:**

* Giao diện tiếng Việt và tiếng Anh.  
* Xem bản nháp, comment và số liệu đã lưu khi offline.  
* Chỉnh sửa và lưu bản nháp cục bộ.  
* Hiển thị trạng thái mạng, thời điểm đồng bộ và các tác vụ đang chờ.

Sinh nội dung AI, lấy dữ liệu mới và gửi bài/comment cần kết nối mạng; giao diện phải thể hiện rõ điều kiện này.

# **3 Giải pháp kỹ thuật**

## **3 1 Kiến trúc tổng thể**

Sử dụng kiến trúc phân lớp theo MVVM: Activity/Fragment với XML Views và View Binding; ViewModel công bố trạng thái qua LiveData; lớp nghiệp vụ Java điều phối tác vụ; Repository truy cập Room và API. Dữ liệu cục bộ là nguồn phục vụ giao diện để hỗ trợ offline và giảm phụ thuộc vào phản hồi mạng.

![Kiến trúc Java của SocialPilot: XML Views, ViewModel LiveData, nghiệp vụ Java, Room, AI API trực tiếp và backend OAuth Spring Boot.][image1]

***Hình 1  Kiến trúc tổng thể ứng dụng SocialPilot***

AI Agent chạy trên thiết bị ở mức điều phối bằng Java: xây dựng prompt, gọi model, kiểm tra đầu ra, cập nhật trạng thái và lưu kết quả. Hai nhánh theo kênh chạy trên ExecutorService dùng chung, có giới hạn số luồng; không triển khai hệ thống điều phối nhiều dịch vụ riêng. Việc sinh văn bản/ảnh vẫn do API nhà cung cấp thực hiện, không đòi hỏi mô hình AI viết bằng Java hoặc chạy cục bộ.

## **3 2 Công nghệ đề xuất**

| Thành phần | Lựa chọn |
| :---- | :---- |
| Ngôn ngữ và giao diện | Java; XML Views, Material Components for Android, View Binding; Activity/Fragment và RecyclerView |
| Quản lý trạng thái | AndroidX ViewModel và LiveData; quan sát theo vòng đời màn hình |
| Xử lý bất đồng bộ | ExecutorService, Future và callback; cập nhật UI qua main thread; quản lý hủy theo phiên và kênh |
| Lưu trữ dữ liệu | Room 2.x với Java annotationProcessor; bảng cài đặt trong Room; ảnh trong vùng riêng của ứng dụng |
| Giao tiếp mạng | Retrofit/OkHttp; đọc stream tăng dần trên luồng nền, parser theo provider và callback về ViewModel |
| Tác vụ nền | WorkManager với Worker hoặc ListenableWorker viết bằng Java; ràng buộc mạng và unique work |
| Bảo vệ bí mật trên thiết bị | Android Keystore; AES/GCM mã hóa key/token trong kho riêng; loại khỏi backup và log |
| Nhận link chia sẻ | Android Sharesheet với ACTION\_SEND |
| Thông báo | Android Notification và điều hướng tới comment, chỉ dùng cho phần mở rộng Optional |
| Backend OAuth | Java và Spring Boot, Spring Web; HTTPS; một dịch vụ tối thiểu, giữ client secret phía server |
| Kiểm thử | JUnit, MockWebServer, Espresso; Room/WorkManager test helpers; Spring Boot Test và MockMvc |
| Quản lý mã nguồn và build | Git, pull request, CI; Gradle Wrapper với Groovy DSL cho Android và backend; khóa phiên bản |

Tuần 1 chọn bản ổn định tương thích và khóa phiên bản JDK, Android Gradle Plugin, Gradle, AndroidX, Spring Boot và thư viện mạng. Mã ứng dụng Android và backend viết bằng Java; thư viện phụ thuộc có thể chứa mã Kotlin mà không yêu cầu nhóm viết Kotlin. Android dùng Room 2.x (androidx.room) với annotationProcessor; không tự nâng sang Room 3 dùng KSP. Đề xuất minSdk 23, kiểm tra lại theo tập thư viện và thiết bị demo. JDK chạy build, mức ngôn ngữ Java và API khả dụng trên Android phải được cấu hình riêng; chỉ dùng desugaring cho API được hỗ trợ. Backend dùng JDK LTS tương thích với Spring Boot đã chọn. \[1–5\]

## **3 3 Vai trò backend**

Backend Java với Spring Boot và Spring Web chỉ hỗ trợ luồng OAuth cần giữ client secret ngoài ứng dụng, bao gồm trao đổi mã và làm mới token khi được hỗ trợ. Triển khai một dịch vụ HTTPS nhỏ; Spring Boot không chạy trong APK.

* Không lưu hoặc xử lý API key LLM của người dùng.  
* Không đóng gói social client secret trong APK.  
* Không đưa access token vào đường dẫn điều hướng về ứng dụng.  
* Dùng mã bàn giao một lần, thời hạn ngắn, gắn với phiên xác thực.  
* Không ghi token hoặc key vào log.

Backend không thực hiện sinh nội dung AI, lưu kho dữ liệu người dùng hay vận hành hệ thống thông báo server trong phiên bản đồ án.

Backend OAuth chỉ giữ trạng thái phiên và mã bàn giao tạm thời có thời hạn, xóa sau khi sử dụng hoặc hết hạn; không lưu token dài hạn. App giữ social token trong kho bí mật và chỉ gửi đến backend khi cần trao đổi/refresh. Yêu cầu refresh phải được gắn với phiên ứng dụng hợp lệ, truyền qua HTTPS và không ghi bí mật vào log. Đây là luồng riêng với BYOK: key LLM chỉ đi từ thiết bị đến đúng nhà cung cấp AI đã chọn.

Kiểm tra state và ràng buộc phiên OAuth, redirect URI trong danh sách cho phép và PKCE khi nền tảng hỗ trợ. Mã bàn giao có hạn dùng ngắn, chỉ đổi một lần và gắn với phiên đã khởi tạo. Client secret lấy từ biến môi trường hoặc kho bí mật của hosting. Với một instance, trạng thái tạm có thể giữ trong bộ nhớ có TTL; restart yêu cầu đăng nhập lại. Nếu chạy nhiều instance phải dùng kho phiên dùng chung. Không bổ sung cơ sở dữ liệu người dùng phía server chỉ vì chọn Spring Boot.

## **3 4 Các hợp đồng nội bộ chính**

| Thành phần | Trách nhiệm |
| :---- | :---- |
| SocialConnector | Kết nối, truy vấn khả năng kênh, đăng bài, lấy/trả lời comment, lấy số liệu |
| AiProvider | Java interface: kiểm tra key, stream qua callback sự kiện có thứ tự, sinh ảnh, trả usage/lỗi chuẩn hóa và tay cầm hủy |
| AgentOrchestrator | Java: lập brief, chạy hai nhánh bằng ExecutorService, hủy Future và HTTP Call, chạy lại riêng nhánh, lưu checkpoint và log |
| PublishQueue | Room transaction và khóa duy nhất; WorkManager đánh thức xử lý; chống trùng, thử lại, đối soát trạng thái |
| BudgetManager | Giữ trước và quyết toán chi phí trong Room transaction; không để hai nhánh cùng chi phần ngân sách còn lại |
| SyncCoordinator | Đồng bộ comment/số liệu, giới hạn số lần gọi và phát hiện dữ liệu mới |

Mỗi bộ tích hợp phải trả về lỗi có phân loại và khả năng hỗ trợ thực tế, giúp giao diện không giả định Facebook và LinkedIn có tính năng giống nhau.

LiveData chỉ công bố trạng thái giao diện mới nhất; không dùng làm hàng đợi từng token vì cập nhật liên tiếp có thể được gộp. Parser tích lũy văn bản theo đúng thứ tự, ViewModel phát bản chụp tiến độ/văn bản; Room lưu checkpoint và kết quả. Callback mang mã phiên, kênh và bước để bỏ qua phản hồi muộn sau hủy hoặc ngắt kênh. \[2–3\]

## **3 5 Dữ liệu chính**

* Kênh kết nối và thông tin quyền.  
* Brief, nguồn URL và topic.  
* Bản nháp, phiên bản, ảnh và alt text.  
* Phiên agent, bước chạy, usage và chi phí.  
* Yêu cầu đăng, bài đã xuất bản và đường dẫn.  
* Comment, nhãn phân loại, câu trả lời và trạng thái xử lý.  
* Snapshot số liệu, mục tiêu và báo cáo tuần.  
* Cấu hình nhà cung cấp, model và ngân sách.

API key và token không nằm trong các bảng dữ liệu thông thường; database chỉ giữ tham chiếu cần thiết đến kho bí mật.

## **3 6 Yêu cầu chất lượng**

* Tác vụ mạng và AI không chặn giao diện.  
* Bản nháp và hàng đợi được giữ lại sau khi ứng dụng khởi động lại.  
* Một kênh lỗi không làm mất kết quả kênh còn lại.  
* Nội dung URL/comment được xử lý như dữ liệu; không được phép ghi đè quy tắc duyệt, ngân sách hoặc tự cấp quyền đăng.  
* Log loại bỏ bí mật; nội dung có thông tin cá nhân không được đưa vào luồng tự trả lời.  
* Các màn hình chính có trạng thái loading, rỗng, lỗi, offline và thiếu quyền.  
* Không cam kết tự động hóa nền 24/7 khi thiết bị bị tắt, ứng dụng bị force-stop hoặc hệ điều hành hạn chế thực thi.

## **3 7 Đánh giá lựa chọn Java**

Java đáp ứng Android native, lưu trữ offline, OAuth, HTTP streaming và điều phối hai nhánh AI của SocialPilot. Lợi ích chính là thống nhất ngôn ngữ và tận dụng kỹ năng nhóm. Đánh đổi là UI Views/XML và callback cần nhiều mã hơn, đồng thời nhóm phải tự quản lý vòng đời, hủy tác vụ và đồng bộ trạng thái. Tính khả thi đến từ năng lực triển khai đã kiểm chứng, không phải bản thân tên ngôn ngữ.

Các thay đổi đồng bộ gồm XML Views thay Compose, LiveData thay StateFlow, ExecutorService/callback thay Coroutines/Flow, cài đặt trong Room thay DataStore, Spring Boot thay Ktor và Espresso thay Compose UI Test. Phạm vi 44 yêu cầu bắt buộc và hai mục Optional được giữ nguyên. Nếu đã có mã nguồn cũ, cần kiểm kê màn hình, luồng bất đồng bộ, dữ liệu và bài kiểm thử trước khi ước lượng chuyển đổi; lịch 7 tuần phải tính cả phần công việc này.

## **3 8 Quy tắc triển khai và kiểm chứng Java**

Tác vụ AI tương tác thuộc phạm vi phiên do ViewModel/Repository quản lý, không thuộc View của Fragment. Xoay màn hình không tạo yêu cầu AI mới. Hủy phải dừng Future, hủy HTTP Call và chặn callback muộn; không giả định Future.cancel tự hủy mạng. Khi process bị kết thúc, khôi phục checkpoint dưới trạng thái gián đoạn và cho người dùng chạy lại; không tự gọi lại bước có thể phát sinh chi phí mà chưa đối soát.

WorkManager xử lý hàng đợi đăng và đồng bộ có thể trì hoãn; mỗi lượt chia thành công việc hữu hạn và lưu checkpoint. Worker chỉ trả hoàn tất sau khi công việc thật sự xong; với API callback dùng ListenableWorker và nối hủy đúng cách. Unique work không thay thế khóa chống trùng của Room và đối soát tại nền tảng. Không dùng WorkManager để hứa hẹn stream tương tác liên tục hoặc tự động hóa 24/7. \[6\]

Bản mẫu cuối tuần 1 phải build được Android Java/XML và backend Spring Boot; kiểm chứng Room đọc/ghi sau khởi động lại, hai nhánh stream đồng thời, hủy riêng nhánh, xoay màn hình, job nền và callback OAuth. CI tách build/test Android và backend; kiểm thử UI trên thiết bị hoặc emulator. Chỉ chốt stack và ước lượng còn lại sau khi bản mẫu đạt.

## **3 9 Tài liệu kỹ thuật tham chiếu**

\[1\] Android View Binding — [Tài liệu chính thức](https://developer.android.com/topic/libraries/view-binding)

\[2\] Android LiveData — [Tài liệu chính thức](https://developer.android.com/topic/libraries/architecture/livedata)

\[3\] Android Java threads — [Tài liệu chính thức](https://developer.android.com/develop/background-work/background-tasks/asynchronous/java-threads)

\[4\] Room 2 và Java annotationProcessor — [Tài liệu chính thức](https://developer.android.com/jetpack/androidx/releases/room)

\[5\] Spring Boot System Requirements — [Tài liệu chính thức](https://docs.spring.io/spring-boot/system-requirements.html)

\[6\] WorkManager API — [Tài liệu chính thức](https://developer.android.com/reference/androidx/work/WorkManager)

# **4 Kế hoạch triển khai và phân công**

## **4 1 Phân công nhóm 4 người**

Mỗi thành viên chịu trách nhiệm một luồng xuyên suốt, gồm giao diện, nghiệp vụ và kiểm thử của phần được giao.

| Thành viên | Trách nhiệm chính | Sản phẩm phụ trách |
| :---- | :---- | :---- |
| TV1 — Điều phối và tích hợp mạng xã hội | F1, F5, backend OAuth Java/Spring Boot; cấu hình Gradle/JDK, CI và tích hợp bản phát hành | Kết nối hai nền tảng; hàng đợi đăng; chống trùng; xử lý token |
| TV2 — AI Agent và BYOK | F2, F8; hợp đồng Java, ExecutorService, stream/callback và hủy tác vụ dùng chung | Orchestrator; hai sub-agent; hai provider; sinh ảnh; log; ngân sách |
| TV3 — Soạn thảo và trải nghiệm sử dụng | F3, F4, F9; XML Views, Material Components, View Binding, ViewModel/LiveData | Nhập topic/URL; nhận chia sẻ; editor; preview; phiên bản; VI/EN; offline |
| TV4 — Comment và phân tích | F6, F7; DAO Java, WorkManager, truy vấn tổng hợp và dashboard | Inbox; trả lời; snapshot; dashboard; báo cáo; mục tiêu. Thông báo chỉ triển khai nếu thực hiện phần mở rộng Optional. |

**Phối hợp bắt buộc:**

* TV1 cung cấp bộ tích hợp mạng xã hội để TV4 sử dụng.  
* TV2 cung cấp dịch vụ AI để TV3 và TV4 tích hợp.  
* TV3 hỗ trợ giao diện và kiểm thử cho TV4 ở tuần 5–6.  
* TV1 và TV2 cùng rà soát bảo mật, hàng đợi, ngân sách và xử lý lỗi.  
* Mỗi thay đổi quan trọng được ít nhất một thành viên khác review.

## **4 2 Lịch trình 7 tuần**

| Tuần | Công việc trọng tâm | Kết quả phải đạt |
| :---- | :---- | :---- |
| 1 — Phân tích và kiểm chứng | Lập ma trận yêu cầu; wireframe; tạo dự án; thiết kế dữ liệu; đăng ký ứng dụng mạng xã hội; thử OAuth, đăng bài, comment và số liệu; thử hai nhà cung cấp AI; kiểm kê mã cũ nếu có; bản mẫu Java/XML, stream/hủy, Room, WorkManager và Spring Boot | Khung Android Java và backend Spring Boot build/test được; bản mẫu kỹ thuật đạt; bảng quyền API thực tế; khóa phiên bản và ước lượng cả chi phí chuyển đổi |
| 2 — Luồng chức năng đầu tiên | Kết nối kênh; Room và secure storage; nhập brief; kiểm tra BYOK; sinh văn bản; lưu/duyệt bản nháp; đăng bài qua bộ tích hợp; ViewModel/LiveData và cài đặt trong Room | Chạy được luồng brief → AI → sửa → duyệt → đăng trên ít nhất một kênh; tiếp tục kiểm chứng kênh thứ hai |
| 3 — Tạo và xuất bản đa kênh | Hai sub-agent song song; stream, hủy, chạy lại; sinh ảnh/template; log; preview hai kênh; hàng đợi offline; retry; chống trùng | Luồng tạo và đăng đa kênh hoàn chỉnh; kiểm thử mất mạng và lỗi từng kênh |
| 4 — Tương tác và dữ liệu | Inbox; đồng bộ; phân loại; gợi ý trả lời; snapshot; dashboard nền tảng; nhập URL và nhận link chia sẻ | Đọc/trả lời comment; có dữ liệu tăng trưởng được lưu thực tế. Thông báo thuộc phần Optional, không phải điều kiện đạt tuần 4\. |
| 5 — Hoàn thiện các yêu cầu nâng cao | Gợi ý topic; lịch sử phiên bản; ba hook; alt text; tự trả lời giới hạn; phân tích; báo cáo tuần; mục tiêu; ngân sách; provider dự phòng | Toàn bộ 44 yêu cầu bắt buộc có luồng sử dụng và có thể kiểm thử; không tính hai mục Optional vào mốc này. |
| 6 — Nghiệm thu phạm vi chính | Kiểm thử liên thông; token hết hạn; rate limit; timeout; phục hồi ứng dụng; VI/EN; offline; sửa lỗi; hoàn thiện hướng dẫn và báo cáo; Espresso, Room migration, WorkManager và Spring Boot Test/MockMvc | Bản ứng viên phát hành; ma trận yêu cầu có kết quả kiểm chứng; không còn lỗi nghiêm trọng |
| 7 — Ổn định và bảo vệ | Chạy thử trên thiết bị; sửa lỗi còn lại; video dự phòng; slide; luyện demo; đóng gói bàn giao | APK cuối, source code, tài liệu, video và kịch bản bảo vệ |

Nếu thời hạn chỉ có 6 tuần, công suất ở mốc 16 giờ/người/tuần giảm từ 448 xuống 384 giờ công. Cần lập lại ước lượng, phân bổ nhân lực và cách triển khai để đáp ứng đủ 44 yêu cầu bắt buộc; không tự cắt các \[N\] không có Optional hoặc thời gian kiểm thử. Nếu nguồn lực không đủ, nêu rõ chênh lệch và đề xuất điều chỉnh thời gian hoặc nguồn lực để người đặt yêu cầu quyết định. Hai phần Optional không triển khai trong phương án 6 tuần.

## **4 3 Mốc quản lý tiến độ**

* Cuối tuần 1: Có bằng chứng quyền API, bản mẫu Java đạt, tập phiên bản đã khóa và ước lượng cập nhật gồm chuyển đổi mã nguồn nếu có.  
* **Cuối tuần 3:** Luồng tạo–duyệt–đăng đa kênh hoạt động.  
* Cuối tuần 5: Hoàn tất triển khai 44 yêu cầu bắt buộc, gồm 33 \[B\] và 11 \[N\] không có Optional; ghi rõ phần chưa đạt và kế hoạch khắc phục.  
* **Cuối tuần 6:** Hoàn tất nghiệm thu nội bộ.  
* **Tuần 7:** Ưu tiên ổn định và bảo vệ.

Nhóm họp ngắn hai lần mỗi tuần, demo bản tích hợp vào cuối tuần và cập nhật bảng công việc theo trạng thái: chưa làm, đang làm, review, kiểm thử, hoàn tất.

## **4 4 Ngân sách và nguồn lực**

Lấy **448 giờ công** làm mốc lập kế hoạch, tương ứng 16 giờ × 4 người × 7 tuần. Khoảng còn lại trong mức cam kết 15–20 giờ/người/tuần là khả năng điều chỉnh, không coi toàn bộ là thời gian rảnh cho chức năng mới.

448 giờ là mốc năng lực, chưa phải ước lượng công việc đã được kiểm chứng. Cuối tuần 1, chia đủ 44 yêu cầu bắt buộc thành nhiệm vụ và ước lượng thiết kế, lập trình, tích hợp, kiểm thử, tài liệu cùng dự phòng. TV1 và TV4 có nguy cơ quá tải do phụ thuộc API; cần phân bổ hỗ trợ và chọn cách triển khai phù hợp. Nếu không đủ năng lực phải báo chênh lệch, không tự chuyển các \[N\] đã chọn sang tùy chọn. Tuần 6–7 vẫn dành cho nghiệm thu và ổn định. Ước lượng Java phải tính UI XML, quản lý callback/hủy, kiểm thử vòng đời và thời gian làm quen Spring Boot; không mặc định việc đổi ngôn ngữ giúp rút ngắn tiến độ.

Chi phí trực tiếp gồm:

* Gọi API văn bản và sinh ảnh.  
* Hosting HTTPS có hỗ trợ JVM cho backend OAuth Java/Spring Boot; đo RAM, thời gian khởi động và hành vi sleep bằng bản mẫu trước khi chọn gói.  
* Tên miền nếu cấu hình triển khai cần.  
* Thiết bị Android sử dụng máy sẵn có của nhóm.

Tuần 1 chạy một bộ yêu cầu mẫu để đo chi phí trung bình, từ đó đặt hạn mức kiểm thử. Dùng dữ liệu mô phỏng cho kiểm thử lặp lại và template cho kiểm thử bố cục ảnh; vẫn phải có kiểm chứng API thật ở các luồng tích hợp.

# **5 Kiểm thử nghiệm thu và bàn giao**

## **5 1 Các kịch bản nghiệm thu chính**

Ma trận bám từng dòng \[B\]/\[N\] trong PRJ\_MD (1).md. Phạm vi bắt buộc gồm toàn bộ 33 mã B và 11 mã N không có Optional. Chỉ F3.N2 (nhập ảnh) và F6.N1 (thông báo comment) là mở rộng Optional. Nhãn B/N giữ theo nguồn để truy vết, không dùng N để chỉ tính tùy chọn. Tiêu chí dưới đây chưa phải kết quả kiểm thử đã đạt; các mốc triển khai áp dụng cho 44 yêu cầu bắt buộc, nghiệm thu tích hợp hoàn tất ở tuần 6\. Hai mục Optional không có hạn hoàn thành bắt buộc.

Khi thực thi, mỗi mã yêu cầu phải có kết quả Chưa kiểm thử, Đạt, Đạt một phần, Không đạt hoặc Bị chặn, cùng liên kết bằng chứng, ngày thử và người xác nhận. Dùng API thật cho các luồng tích hợp; dữ liệu mô phỏng chỉ dùng bổ sung cho lỗi khó tái hiện. Không cộng một yêu cầu bị chặn vào số đã đạt.

### **F1 Kết nối và quản lý mạng xã hội**

Phụ trách TV1. Mốc triển khai Tuần 1–2.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F1.B1 | Kết nối ít nhất hai kênh **Bắt buộc** | OAuth thật với Facebook Page và LinkedIn; nhận đúng định danh và tài khoản/Page. Hai tài khoản cùng nền tảng không thay hai nền tảng đã chọn. |
| F1.B2 | Màn hình Kênh của tôi **Bắt buộc** | Hiển thị trạng thái, tài khoản/Page và khả năng đăng, đọc/trả lời comment, mention, số liệu; đối chiếu quyền thực tế cho từng kênh. |
| F1.B3 | OAuth và vòng đời token **Bắt buộc** | Mở trình duyệt hệ thống; thử refresh, hết hạn và thu hồi token. Refresh thành công khi được cấp quyền; thiếu cơ chế refresh phải ghi bị chặn/đạt một phần. |
| F1.B4 | Ngắt kết nối và xóa dữ liệu **Bắt buộc** | Ngắt một kênh rồi mở lại app: token, dữ liệu và job liên quan không còn; kênh khác và bản nháp dùng chung vẫn đúng; kết quả về muộn không khôi phục dữ liệu. |

### **F2 AI Agent viết bài và sinh ảnh**

Phụ trách TV2. Mốc triển khai Tuần 3\.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F2.B1 | Orchestrator và sub-agent **Bắt buộc** | Một topic tạo brief chung và khởi chạy song song hai nhánh riêng; log chứng minh thời gian thực thi chồng lấp. |
| F2.B2 | Đủ các bước của từng nhánh **Bắt buộc** | Mỗi kênh có dàn ý, bản nháp, kết quả tự rà, mô tả ảnh, ảnh và gói kết quả; lỗi ghi đúng bước. |
| F2.B3 | Sinh ảnh riêng và template **Bắt buộc** | Mỗi kênh có cấu hình kích thước/phong cách; thử cả API sinh ảnh và trường hợp không có API để xác nhận template hoạt động. |
| F2.B4 | Tiến độ và stream **Bắt buộc** | Stream xuất hiện trước khi hoàn tất; tích lũy đủ nội dung đúng thứ tự. Xoay màn hình/đổi Fragment không tạo lệnh mới; process bị kết thúc khôi phục checkpoint và báo gián đoạn. |
| F2.B5 | Hủy và cô lập lỗi **Bắt buộc** | Hủy giữa chừng phải hủy Future và HTTP Call, bỏ qua callback muộn; chạy lại riêng nhánh lỗi. Nhánh còn lại và kết quả đã lưu không mất; không ghi đè sau hủy. |
| F2.B6 | Nhật ký agent **Bắt buộc** | Xem prompt, kết quả, token và chi phí theo từng bước/kênh; phân biệt số liệu thiếu với 0; không xuất hiện key hoặc token bí mật. |

### **F3 Nhập và gợi ý topic**

Phụ trách TV3. Mốc triển khai B tuần 2 • N tuần 4–5.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F3.B1 | Topic và brief **Bắt buộc** | Nhập và lưu topic, mục tiêu, đối tượng, giọng văn, VI/EN và kênh đích; dữ liệu đúng được chuyển vào brief chung. |
| F3.N1 | Nhập URL có trích nguồn **Bắt buộc** | URL công khai tạo bài chia sẻ kèm nguồn; URL lỗi/bị chặn có thông báo và nhập tay, không bịa nội dung nguồn. |
| F3.N2 | Nhập topic bằng ảnh **Mở rộng Optional** | Phần mở rộng: nếu triển khai, thử slide/whiteboard, cho sửa nội dung nhận dạng trước khi tạo brief; không đánh dấu đạt khi chưa triển khai. |
| F3.N3 | Share extension **Bắt buộc** | Chia sẻ link từ app khác qua ACTION\_SEND mở đúng form với URL; luồng hủy không tạo bài hoặc tự đăng. |
| F3.N4 | Gợi ý topic **Bắt buộc** | Có gợi ý từ bài hiệu quả và từ nguồn xu hướng RSS/Atom đã chọn, kèm nguồn/lý do; thiếu dữ liệu có trạng thái rõ. |

### **F4 Duyệt và chỉnh sửa bài**

Phụ trách TV3. Mốc triển khai B tuần 3 • N tuần 5\.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F4.B1 | Duyệt trước khi đăng **Bắt buộc** | Bài chưa duyệt không vào hàng đợi; sửa nội dung/ảnh sau duyệt làm mất hiệu lực duyệt. Kiểm tra cả luồng online và offline. |
| F4.B2 | Preview sửa tay và giới hạn **Bắt buộc** | Preview riêng hai kênh, sửa tay, đếm ký tự; thử dưới/đúng/vượt giới hạn đã kiểm chứng. Vượt giới hạn không được gửi. |
| F4.B3 | Viết lại đoạn và sinh lại ảnh **Bắt buộc** | Chọn một đoạn để viết lại, giữ phần còn lại; sinh lại ảnh theo đúng kênh và yêu cầu duyệt lại bản thay đổi. |
| F4.N1 | Lịch sử phiên bản **Bắt buộc** | Xem và khôi phục phiên bản; không mất phiên bản cũ và không kế thừa nhầm trạng thái duyệt. |
| F4.N2 | Ba phương án mở đầu **Bắt buộc** | Sinh đủ 3 hook; chọn một hook chỉ thay phần mở đầu và giữ phần thân. |
| F4.N3 | Alt text **Bắt buộc** | Tạo, sửa và lưu alt text; gửi cùng ảnh nếu API hỗ trợ, báo rõ nếu không thể truyền lên kênh. |

### **F5 Đăng mạng xã hội**

Phụ trách TV1. Mốc triển khai Tuần 3\.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F5.B1 | Đăng đa kênh và trạng thái **Bắt buộc** | Đăng lên hai kênh với trạng thái độc lập: nháp, chờ, đang đăng, đã đăng/lỗi/đã bàn giao. Một kênh lỗi không đổi kết quả kênh kia. |
| F5.B2 | Hàng đợi offline **Bắt buộc** | Mất mạng vẫn xếp hàng bài đã duyệt; đóng/mở app không mất job; có mạng và đủ điều kiện hệ điều hành thì tiếp tục. |
| F5.B3 | Không đăng trùng **Bắt buộc** | Thử bấm hai lần, retry, khởi động lại và mất phản hồi sau khi server nhận. Kiểm tra bài thực tế; kết quả chưa rõ phải dừng đối soát, không đăng lại mù quáng. |
| F5.B4 | Retry và báo lỗi **Bắt buộc** | Lỗi mạng/429 có backoff và giới hạn thử; lỗi quyền/nội dung hiện lý do và bước xử lý. Không retry tự động khi chưa rõ kết quả đăng. |
| F5.B5 | Link bài gốc **Bắt buộc** | Sau API thành công lưu mã bài và URL đúng từng kênh; mở đúng bài gốc. Đã bàn giao không được tự gán link/thành công giả. |

### **F6 Đọc comment và trả lời**

Phụ trách TV4. Mốc triển khai Tuần 4–5.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F6.B1 | Inbox hợp nhất **Bắt buộc** | Comment thật của hai kênh hiển thị cùng hộp thư; lọc đúng theo kênh, bài và trạng thái xử lý. |
| F6.B2 | Đồng bộ comment **Bắt buộc** | Kéo làm mới và job nền cập nhật comment, xử lý phân trang; đồng bộ lặp không nhân bản; hiển thị lần cập nhật gần nhất. |
| F6.B3 | Phân loại AI **Bắt buộc** | Bộ kiểm thử có đủ câu hỏi, khen, góp ý, tiêu cực và spam; lưu nhãn từng mẫu, số sai và ma trận nhầm lẫn để đánh giá. |
| F6.B4 | Soạn sửa và gửi trả lời **Bắt buộc** | AI dựa trên bài và giọng văn; người dùng sửa rồi gửi. Câu gửi xuất hiện đúng comment trên nền tảng; thử lại không gửi lặp. |
| F6.B5 | Tự trả lời giới hạn **Bắt buộc** | Mặc định tắt; chỉ khen/FAQ đã xác nhận được gửi. Toàn bộ ca tiêu cực, thông tin cá nhân, không chắc chắn và quá hạn mức trong bộ thử đều phải bị chặn. |
| F6.N1 | Thông báo và mở comment **Mở rộng Optional** | Phương án đồng bộ nền \+ thông báo cục bộ: chỉ thông báo comment mới, chạm mở đúng nơi; thử từ chối quyền, comment bị xóa và kênh bị ngắt. Không cam kết thời gian thực. |

### **F7 Theo dõi tăng trưởng**

Phụ trách TV4. Mốc triển khai B tuần 4 • N tuần 5\.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F7.B1 | Số liệu từng bài **Bắt buộc** | Đối chiếu thích/reaction, comment, chia sẻ, impression nếu có và công thức tương tác. Thiếu quyền/dữ liệu hoặc mẫu số 0 không hiển thị thành 0%. |
| F7.B2 | Snapshot và đường tăng trưởng **Bắt buộc** | Lưu ít nhất hai thời điểm thực tế cho bài/kênh; biểu đồ đúng thời điểm và giá trị, giữ khoảng trống khi bỏ lỡ lịch. |
| F7.B3 | Dashboard **Bắt buộc** | Có follower theo thời gian, tương tác theo tuần, top bài và so sánh kênh. Đối chiếu bằng dữ liệu có cùng định nghĩa; follower không được thay bằng connections. |
| F7.N1 | Phân tích theo nhóm **Bắt buộc** | Phân nhóm theo chủ đề, định dạng và giờ đăng; số liệu tổng hợp khớp dữ liệu gốc và múi giờ cấu hình. |
| F7.N2 | Báo cáo tuần AI **Bắt buộc** | Nêu biến động và đề xuất dựa trên snapshot; số liệu khớp nguồn, nguyên nhân chưa chứng minh được ghi là giả thuyết; thiếu dữ liệu không bịa kết luận. |
| F7.N3 | Mục tiêu tăng trưởng **Bắt buộc** | Lưu chỉ số, mức đích và hạn đạt; tiến độ tính từ dữ liệu thực, cập nhật sau đồng bộ và xử lý trường hợp chưa có số liệu. |

### **F8 API key LLM và chi phí**

Phụ trách TV2. Mốc triển khai B tuần 2–3 • N tuần 5\.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F8.B1 | Ít nhất hai nhà cung cấp **Bắt buộc** | Key hợp lệ của OpenAI và Gemini đều thực hiện được một tác vụ văn bản; đổi provider không mất bản nháp hoặc usage đã ghi. |
| F8.B2 | Kiểm tra và bảo vệ key **Bắt buộc** | Thử key đúng/sai, bị thu hồi và lỗi mạng; key được che, mã hóa, xóa được và không nằm trong backup/log. Kiểm tra network: chỉ gửi tới provider tương ứng, không qua backend nhóm. |
| F8.B3 | Chọn model theo tác vụ **Bắt buộc** | Chọn và lưu model cho viết bài, phân loại comment, sinh ảnh; model không có khả năng phù hợp bị chặn; thiếu API ảnh dùng template. |
| F8.B4 | Token và chi phí **Bắt buộc** | Usage từng lệnh tổng hợp đúng theo ngày/tháng và provider; ảnh ghi chi phí riêng nếu không có token. Số ước tính có nhãn và không biến usage thiếu thành 0\. |
| F8.N1 | Trần ngân sách và dừng agent **Bắt buộc** | Thử trần ngày/tháng với hai nhánh song song và retry: giữ trước ngân sách, không mở lệnh mới khi hết; dừng nhánh, lưu kết quả một phần và thử hủy lệnh đang chạy nếu hỗ trợ. |
| F8.N2 | Provider dự phòng **Bắt buộc** | Gây lỗi khả dụng ở provider chính: dùng provider phụ có key/model phù hợp và còn ngân sách; ghi log, cộng chi phí chung, không tạo kết quả/ảnh trùng. |

### **F9 Cài đặt chung**

Phụ trách TV3. Mốc triển khai Tuần 6\.

| Mã | Yêu cầu | Phép thử và điều kiện đạt |
| :---- | :---- | :---- |
| F9.B1 | Giao diện VI và EN **Bắt buộc** | Đổi ngôn ngữ trên toàn bộ luồng chính, trạng thái lỗi và thông báo; kiểm tra chuỗi thiếu và bố cục. |
| F9.B2 | Dùng offline **Bắt buộc** | Sau đồng bộ, bật chế độ máy bay và khởi động lại: vẫn xem bản nháp, comment, số liệu đã lưu; nhận biết dữ liệu cũ và chức năng cần mạng. |

Đánh giá AI bằng bộ dữ liệu cố định do nhóm gán nhãn trước: đề xuất tối thiểu 20 brief (10 VI, 10 EN) và 60 comment gồm đủ 5 nhóm cùng các trường hợp FAQ, thông tin cá nhân và không chắc chắn. Ghi kết quả từng mẫu và ma trận nhầm lẫn; không chỉ chọn ca thành công. Chỉ bật tự trả lời sau khi mọi ca cấm trong bộ kiểm thử đều bị chặn, FAQ ngoài danh sách không tự gửi và giới hạn hằng ngày hoạt động. Đây là điều kiện kiểm thử nội bộ, không phải bảo đảm AI không bao giờ sai ngoài thực tế.

Kiểm thử kỹ thuật Java bổ sung: Espresso cho luồng Views/XML; MockWebServer cho stream đứt đoạn, timeout và hủy; Room migration và transaction cho hàng đợi/ngân sách; WorkManager test helpers cho retry và ràng buộc mạng; Spring Boot Test/MockMvc cho state, mã bàn giao một lần, hết hạn, phiên không hợp lệ và che bí mật. Lưu bằng chứng CI và thử trên thiết bị thật; không xem build thành công là đủ nghiệm thu chức năng.

## **5 2 Rủi ro và phương án xử lý**

| Rủi ro | Tác động | Biện pháp |
| :---- | :---- | :---- |
| Chưa được cấp đủ quyền Facebook/LinkedIn | Chặn đăng bài, comment hoặc số liệu thật | Đăng ký và kiểm chứng từ đầu tuần 1; ghi bằng chứng; làm việc với giảng viên ngay khi xác định bị chặn |
| Phạm vi lớn so với 6–7 tuần | Dồn tích hợp và kiểm thử về cuối | Tích hợp liên tục; dùng UI đơn giản; đóng chức năng chính trước tuần 6 |
| Hạn mức API | Đồng bộ thất bại hoặc bị giới hạn | Đồng bộ tăng dần, phân bổ lượt gọi, backoff; tránh polling liên tục |
| Tác vụ nền bị trì hoãn | Comment và thông báo cập nhật chậm | Hiển thị thời điểm đồng bộ; hỗ trợ làm mới thủ công; không cam kết tức thời |
| AI sai nội dung hoặc phân loại | Bài viết/trả lời không phù hợp | Duyệt bài; kiểm tra đầu ra; tự trả lời mặc định tắt và có điều kiện chặt |
| Timeout khi đăng | Nguy cơ đăng trùng | Hàng đợi bền vững, khóa xử lý, đối soát và kiểm tra thủ công khi cần |
| Thiếu lịch sử tăng trưởng | Khó chứng minh dashboard | Thu thập snapshot ngay từ tuần 4; dữ liệu mẫu được gắn nhãn riêng |
| Chi phí AI vượt dự tính | Thiếu ngân sách kiểm thử | Đo usage sớm, đặt hạn mức và giảm số lần gọi lặp không cần thiết |
| Chuyển stack và vòng đời Java | Viết lại UI/async hoặc mất trạng thái, tốn thêm giờ tích hợp | Kiểm kê mã cũ; bản mẫu tuần 1; mẫu callback/hủy dùng chung; kiểm thử xoay màn hình, process bị kết thúc và Room transaction |

LinkedIn Community Management ở Development tier có giới hạn lượt gọi và hạn chế webhook, nên thiết kế đồng bộ phải dựa trên quyền và hạn mức thực tế được cấp. [Tài liệu LinkedIn về cấp truy cập](https://learn.microsoft.com/en-us/linkedin/marketing/increasing-access?view=li-lms-2026-06)

**Điều kiện khả thi quan trọng:** Mục tiêu tích hợp thật hai nền tảng phụ thuộc quyền API. Nếu quyền cần thiết không được cấp đúng thời hạn, nhóm phải ghi nhận đó là yêu cầu chưa đạt hoặc xin giảng viên chấp thuận điều chỉnh. Dữ liệu mock và thao tác bàn giao thủ công không tự động được coi là hoàn thành tích hợp API.

Bảng kiểm chứng API ở tuần 1 phải ghi: nền tảng, loại tài khoản/Page, API hoặc endpoint, quyền đã cấp, khả năng refresh, đăng bài, đọc/trả lời comment, follower và số liệu bài; kèm ngày thử, kết quả và bằng chứng đã che bí mật. TV1 phụ trách kết nối/đăng, TV4 phụ trách comment/số liệu. Khi quyền còn chờ, ghi “Bị chặn” và thời điểm cần quyết định; không ghi “Đạt” dựa trên mock, UI hoặc thao tác bàn giao.

## **5 3 Điều kiện bắt đầu phần mở rộng**

Chỉ triển khai hai phần mở rộng Optional là nhập topic bằng ảnh và thông báo comment mới kèm mở đúng comment khi:

* Toàn bộ yêu cầu chính đã qua nghiệm thu.  
* Không còn lỗi gây mất dữ liệu, đăng trùng hoặc lộ bí mật.  
* Báo cáo, slide và kịch bản demo đã sẵn sàng.  
* Còn thời gian đủ cho triển khai và kiểm thử độc lập.

Không sử dụng thời gian sửa lỗi bắt buộc để hoàn thiện phần mở rộng.

## **5 4 Sản phẩm bàn giao**

1. APK Android cài đặt và chạy được.  
2. Mã nguồn Android Java/XML và backend OAuth Java/Spring Boot, kèm Gradle Wrapper và phiên bản đã khóa.  
3. Proposal, tài liệu yêu cầu và ma trận truy vết F1–F9.  
4. Tài liệu kiến trúc, mô hình dữ liệu và các luồng nghiệp vụ chính.  
5. Hướng dẫn JDK/Gradle, cấu hình OAuth, BYOK, biến môi trường và triển khai backend Java/Spring Boot qua HTTPS.  
6. Bộ kiểm thử, kết quả nghiệm thu và danh sách hạn chế còn lại.  
7. Báo cáo phân công và đóng góp của từng thành viên.  
8. Slide, kịch bản demo và video dự phòng.

Tiêu chí hoàn thành: đủ 44 yêu cầu bắt buộc, gồm 33 \[B\] và 11 \[N\] không có Optional, có bằng chứng kiểm thử và người xác nhận. Hai mục Optional được theo dõi riêng và không thay thế yêu cầu bắt buộc còn thiếu. Yêu cầu phụ thuộc quyền API chưa kiểm chứng phải ghi chưa đạt hoặc bị chặn; mọi điều chỉnh phạm vi cần người đặt yêu cầu chấp thuận. Dữ liệu mô phỏng và bàn giao thủ công không thay thế bằng chứng tích hợp thật.

[image1]: <data:image/png;base64,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>