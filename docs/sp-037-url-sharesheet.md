# SP-037 — URL và Sharesheet

Android `ACTION_SEND` + `text/plain` tìm URL http/https trong văn bản được chia sẻ và đưa vào ô nguồn của brief. Link nội bộ, link có userinfo, URL sai và MIME khác không được nhập. Nếu form đang có nguồn, người dùng phải xác nhận trước khi thay. Mở form, hủy, hoặc quay lại không tự lưu brief hay gửi nội dung. Khi Activity được tái tạo, nguồn đang nhập được giữ; intent cũ không được xử lý lại.

URL `http` vẫn có thể làm nguồn ghi tay; cấu hình Android hiện chặn cleartext nên bước đọc metadata của các URL này có thể thất bại. Dùng `https` để đọc tự động.

Nút **Đọc thông tin bài công khai** là thao tác chủ động. Repository chỉ tải HTML tối đa 128 KiB, giới hạn 10 giây, tối đa ba redirect; DNS và redirect không được trỏ đến địa chỉ nội bộ. Nó đọc `title` và `description` công khai, không lấy toàn bộ bài, không đi qua login/paywall. HTTP 401/403/402, giới hạn tần suất, trang không phải HTML và lỗi mạng có lý do riêng; URL vẫn nằm trong form để nhập tay. Sau redirect, nguồn dùng URL cuối đã truy cập. Tiêu đề/mô tả chỉ gợi ý vào topic khi ô này còn trống; người dùng phải kiểm tra và tự lưu. Nguồn URL lưu dưới `Brief.Source(URL, url)` như dữ liệu, không tự chạy AI, duyệt, enqueue hay publish.

Khi không có kết nối mạng đã xác thực, nút đọc link báo lỗi ngay và không tạo request; nguồn và brief vẫn nhập/lưu local được.

Kiểm chứng với MockWebServer: chuyển hướng, metadata, login, HTML không có metadata và chặn URL nội bộ. UI instrumentation: Sharesheet điền nguồn và giữ qua tái tạo Activity. Build/JVM/lint và emulator được ghi trên PR. Chưa thử link có login/paywall thực tế, nhiều kiểu charset hoặc mọi site công khai; không tuyên bố trích toàn văn hay vượt hạn chế nguồn.

Ngày 26/11/2026: đồng bộ SP-035 với ChannelRepository SP-022; giữ cả import URL và picker kênh trong MainActivity/MainViewModel. Android assemble/unit/lint/APK test đạt; 4/4 ca Sharesheet và brief trên AVD API 36 đạt. Bằng chứng site thật còn thiếu như giới hạn phía trên.
