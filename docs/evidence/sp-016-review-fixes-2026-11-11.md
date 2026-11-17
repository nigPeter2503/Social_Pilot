# SP-016 — Kiểm tra bản sửa theo review

- Ngày: 11/11/2026 (Asia/Ho_Chi_Minh).
- Người chạy: Codex trên nhánh `feat/sp-016-brief-approval-contract`.
- Môi trường: Windows, Temurin JDK 21.0.13, Android SDK Platform 36, Build Tools 36.0.0.
- Lệnh trong `android/`: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain --no-daemon` với `ANDROID_HOME` trỏ đến SDK trên máy.
- Kết quả: `BUILD SUCCESSFUL`; 13 unit test, 0 failure/error/skipped; Android lint 0 lỗi, 13 cảnh báo.
- Ca hồi quy mới: callback từ v1 đến sau khi v2 được duyệt không đổi current/approval; restore từ bản cũ phải khớp current đã quan sát; danh sách nguồn bất biến rỗng/có phần tử hợp lệ; nguồn null bị từ chối; hai kênh Facebook khác ID bị từ chối trong khi Facebook + LinkedIn hợp lệ.
- `git diff --check`: đạt.

Đây là kiểm tra model trong bộ nhớ và hợp đồng brief. Chưa kiểm chứng Room, queue bền vững, API thật hoặc walkthrough trực tiếp với Minh/Đạt. Các phần này tiếp tục được review/tích hợp ở task tương ứng.
