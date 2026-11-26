# SP-023 — Bằng chứng kiểm thử Kênh của tôi

Ngày kiểm thử 21/11/2026 (UTC+07:00). [Issue #25](https://github.com/nigPeter2503/Social_Pilot/issues/25) thuộc F1.B2. Giữ card In Progress trong khi chờ đầu vào production.

## Bản kiểm thử

- Baseline `origin/main` tại `ac38888c7038eaa8135933acac72d565ab29f2e5`. Mã Android đã thử tại `0099caca33c18ecc2a73106109e6a2fc257b3743`.
- Windows, JDK 21.0.7, Android SDK Platform 36. Android Emulator Pixel 5, API 36 x86_64, build Android `BE2A.250530.026.D1`. Mở màn hình bằng nút **Kênh của tôi** trên trang chính.
- [Draft SP-022 #96](https://github.com/nigPeter2503/Social_Pilot/pull/96) tại `3e18f9832bb4385d02a388fec54daa34649700a3` là nguồn đối chiếu fixture `INSTAGRAM / PROFESSIONAL`, revision 7 và 8. Repo SP-023 hiện đọc metadata Page từ kho mã hóa SP-010. Bản ghi spike có ID tài khoản, tên và hạn dùng nếu có. `channelId`, revision và bằng chứng quyền chỉ có khi repo production bàn giao. [Hợp đồng Social, mục 3](../social-contract.md#3-kênh-và-capability)

## Kiểm tra đã chạy

| Kiểm tra | Kết quả |
| --- | --- |
| `git diff --check`, `git diff --staged --check` | Đạt. |
| Tại `android`, `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` | Đạt. 63 unit test, lint 0 lỗi và 14 cảnh báo có sẵn, APK app và APK instrumentation đã build. |
| `adb -s emulator-5580 shell am instrument -w -r com.socialpilot.app.test/androidx.test.runner.AndroidJUnitRunner` | 4 test đạt, gồm đi từ trang chính rồi quay lại, tái tạo Activity và hai test kho mã hóa có sẵn. |
| Bốn trạng thái capability, thiếu bản ghi, bằng chứng sai tài khoản/kênh/revision | Đạt trên unit test dùng fixture. Ca revision 7 tới muộn giữ trạng thái quyền của revision 8. Các thao tác cùng kênh giữ kết quả riêng. |
| Danh sách rỗng online và offline | Đạt trên cài đặt sạch của AVD. [Rỗng EN](sp-023/en-online-empty.png) · [Offline chưa có cache VI](sp-023/vi-offline-empty.png) |
| VI/EN và mở lại khi offline có cache | Đạt trên AVD với **Page giả** được ghi bằng APK instrumentation riêng qua kho SP-010. Sau force-stop, tắt mạng và mở app qua trang chính, tên và ID Page vẫn hiển thị. Quyền thiếu bằng chứng hiện Chưa xác minh. [VI](sp-023/vi-facebook-fixture.png) · [EN](sp-023/en-facebook-fixture.png) · [Offline sau khi mở lại](sp-023/vi-offline-facebook-fixture.png) |
| Hết hạn và đường vào kết nối Facebook | Đạt với bản ghi giả đã hết hạn. Thao tác mở đến nút kết nối Facebook ở trang chính, chờ người dùng bấm. [Ảnh hết hạn](sp-023/vi-expired-facebook-fixture.png) |
| Lỗi đọc và Thử lại | Đạt với bản ghi giả bị lỗi trong AVD. Nút Thử lại giữ thông báo lỗi khi dữ liệu vẫn không đọc được. [Ảnh lỗi](sp-023/en-error-facebook-fixture.png) |

Fixture thiết bị nằm trong APK instrumentation dành riêng cho AVD. Ảnh chứa tên và ID mẫu. Các ca backend nằm ngoài phạm vi thay đổi Android này.

## Đồng bộ ngày 25/11/2026

Đã ghép main `ddab17f`, giữ lối vào Kênh của tôi, BYOK, kết nối Facebook/Instagram
và prototype từ màn hình chính. Nguồn metadata hiện tại của Kênh của tôi vẫn chỉ
đọc Page từ kho OAuth cục bộ. Kiểm lại lookup Facebook gọi đúng provider sau khi
SP-065 tách kho theo đích.

- Android `assembleDebug`, `testDebugUnitTest`, `lintDebug` và
  `assembleDebugAndroidTest` đạt. 145 unit test, lint 0 lỗi và 11 cảnh báo.
- AVD API 36 chạy AndroidJUnitRunner đạt 32/32, gồm hai ca điều hướng và tái
  tạo Activity của Kênh của tôi. Lượt đầu bị hộp thoại ANR của System UI chiếm
  focus. Chạy lại toàn bộ sau khi khôi phục AVD.

## Điều hướng trên nhánh Draft ngày 26/11/2026

Nhánh #97 đã ghép `main` tại `c5b8b17`. Màn hình chính giữ Kênh của tôi, Khóa API AI
và kết nối hai nền tảng. Kênh của tôi mở đúng mục Facebook hoặc Instagram và
đợi thao tác xác nhận của người dùng. Tái tạo Activity sau khi đi tới mục
kết nối không tự mở trình duyệt. Nhánh Draft đang dùng nguồn dữ liệu cũ
trong lúc chờ đầu vào Room vào `main`.

Android Gradle Wrapper đạt `assembleDebug`, `testDebugUnitTest`, `lintDebug` và
`assembleDebugAndroidTest`. 170 unit test, lint 0 lỗi và 11 cảnh báo. API 36 chạy
AndroidJUnitRunner đạt 35/35, gồm mở BYOK, định tuyến hai provider và kiểm màn
hình chính vẫn khả dụng sau callback cũ thiếu phiên.

## Bàn giao còn mở

| Phần còn mở | Điều kiện tiếp tục |
| --- | --- |
| Hai kênh thật, thời điểm xác minh sau restart và cập nhật khi thu hồi quyền | Minh bàn giao repository production SP-022 phát snapshot metadata đã lưu, đúng phạm vi tài khoản/kênh/revision. Đối chiếu kiểu Instagram trên `main`; dùng tài khoản thử có đủ quyền. Sau khi nối, chạy lại các ca offline với thời điểm xác minh thật và cạnh tranh revision tại điểm ghi. |
| Reconnect theo kênh, callback và xoay trong lúc kết nối | Minh bàn giao API bắt đầu bằng `channelId`, callback và cập nhật snapshot. Thiện xác nhận điều hướng. Ca tái tạo Activity hiện chỉ xác nhận màn hình tiếp tục mở. Chạy lại ca xoay trong lúc đang tải/kết nối, theo dõi lần gọi mạng và lần mở OAuth. |
| Mention | Hợp đồng Java chưa có operation mention. Màn hình hiển thị Chưa xác minh. Minh và Nghị xác nhận operation, trạng thái quyền và ca đối chiếu theo tài khoản trước khi nghiệm thu. [Ghi nhận trên issue](https://github.com/nigPeter2503/Social_Pilot/issues/25#issuecomment-7058172808) |

Brief W3 ước lượng ban đầu 6 giờ với cutoff 21/11/2026. Ước tính còn 2–4 giờ để nối repo đã sẵn sàng và kiểm thử lại, chưa gồm thời gian chờ quyền Meta, đầu vào SP-022 và review.
