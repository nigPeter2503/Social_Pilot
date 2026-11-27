# SP-047 — Dashboard framework

Màn hình dashboard hiện là bản xem trước dùng **MO_PHONG**. Giá trị người theo dõi `100` và mốc `2026-11-08T17:00:00Z` đến từ `docs/contracts/analytics-examples.json` (`mock-snapshot-1`). Ảnh chụp này chỉ có một mốc; màn hình giữ khoảng trống ngày 02/11, không tính tăng trưởng hoặc đổi dữ liệu thiếu thành 0. Tương tác tuần và bài nổi bật chưa có mẫu tương thích, nên hiện trạng thái thiếu. Bộ lọc chỉ số được giữ qua tái tạo Activity bằng `SavedStateHandle`.

Màn hình có nhãn fixture rõ ràng và trạng thái online/offline; không gọi API và không trình bày mẫu như dữ liệu thật. Ngày hiển thị theo `Asia/Ho_Chi_Minh` (UTC+7). Khi SP-045/046 bàn giao repository/DTO và mẫu API thật, ViewModel cần nhận `DashboardData` cùng timestamp, missing reason, freshness, coverage và sync state theo `docs/analytics-data-contract.md` §7. Lúc đó mới thay dữ liệu fixture, kiểm chứng trục thời gian, loading/error/permission, cache và nguồn thật. Phần goal/grouped UI thuộc SP-049.

Kiểm thử: build/JVM/lint và instrumentation trên API 23/36 được ghi tại PR. Chưa đối chiếu số liệu API thật.

Ngày 26/11/2026: đồng bộ SP-051/SP-035/SP-022; giữ cả DashboardActivity, PrototypeActivity và AI Keys trong manifest. Android assemble/unit/lint và APK test đạt; 1/1 ca dashboard trên AVD API 36 đạt. Dashboard vẫn dùng fixture MO_PHONG, chưa có số liệu production.
