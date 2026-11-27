# SP-041 · Verification và nội dung cập nhật PR #87

**Trạng thái đề nghị cho Nghị: giữ issue #43 mở.** Channel production đã nối được với adapter Meta. Các ca trên thiết bị dùng Room và Keystore thật, database instrumentation riêng và GET bị chặn tại OkHttp để trả payload mô phỏng. Việc đưa các bảng vào `socialpilot.db` cần slot của Đạt. Lượt đọc thật của ứng dụng cần grant, role và bài/media được Minh xác nhận theo revision. [Handoff consumer](handoffs/sp-041-published-post-input.md) · [Manifest app live](sp-041-live-manifest.json)

## Revision và môi trường

| Thành phần | Giá trị |
|---|---|
| PR #87 → main | Head remote `ca77f6265f87d9cfc56242106072e7efc91b3cc7`. GitHub REST trả `base.ref=main`, `base.sha=a351435` trên snapshot PR. `git ls-remote` và fetch cho head nhánh main hiện hành `c49676e47bbe9ee72910150ef0516878b00d1053`. |
| Mã kiểm thử | `6facb57279c20524aa09da1ab57dbec7c5df1a4f`. Merge từ đúng head PR với `6c22dee` (Channel) rồi `c49676e` (ledger và pipeline). Worktree tích hợp riêng. |
| Schema | Production AppDatabase v2, migration v1→v2 của SP-022. Schema candidate khai báo trong androidTest và dùng cùng ChannelDao. Schema demo, ledger và schema export production giữ theo main. |
| Slot | PR #82 (draft `a3d88da`) khai báo v3 cho Brief. PR #83 (draft `834bca6`) khai báo v4 cho Version. Chưa có version nguồn–đích cấp cho SP-041. |
| Lượt kiểm | 27/11/2026, giờ Việt Nam. Windows x64, Temurin 21.0.13+9, Gradle Wrapper 8.13, minSdk 23, compile/target 36. AVD AOSP default x86_64 API 23 r10 và API 36 r02. |
| Graph | FB Page, SP-018 `230c2a6`; IG Professional, SP-066 `eda55ac`. Cùng v27.0. IG-01 ánh xạ Page → IG user; IG-02–03 cung cấp media, trang và terminal; IG-04–05 cung cấp reply readback dưới đúng parent. Các spike lịch sử được giữ riêng với grant của ứng dụng. |

## JVM và fixture

- Gradle build APK debug và instrumentation thành công. **469/469** JVM trong 35 suite, 0 fail, 0 error, 0 skip. Lint 0 lỗi và 14 cảnh báo.
- MockWebServer kiểm GET dùng đúng `from.id` của Page hoặc `owner.id` của IG, ID provider dạng chuỗi, target/owner sai dừng import. Bộ hồi quy bao gồm trang overlap, replay, conflict khi trùng timestamp, parent UNKNOWN, cursor không dùng được, cancel, offline, 401/403, 429 và 503. Các ca Retry-After gồm giá trị 0, deadline tương lai và lỗi khi body đến chậm.
- Cursor không giải mã được sẽ khởi động lại đúng query với dedup. Với lỗi provider tổng quát như 403, ứng dụng giữ checkpoint. Mã lỗi Graph cụ thể cho cursor hết hạn trên từng grant/version còn cần payload có bằng chứng để phân loại tự động.
- Cả 4 script Node đạt. Hai validator IG kiểm dữ liệu mô phỏng và payload IG-01–13 đã che. Facebook 55/55, analytics 75/75. Fixture SP-066 mang nhãn mô phỏng khi phát lại.

## Room, Channel và Worker trên thiết bị

| Ca | Kỳ vọng | Kết quả trên API 23 và 36 |
|---|---|---|
| Channel production | Nâng v1→v2 giữ Draft; đóng/mở DB giữ ID, revision và credential đúng scope. | Chạy 11 ca `ChannelRepositoryDeviceTest` trên mỗi API, gồm upgrade, import legacy, restart, reconnect và rollback. |
| Nhập bài có sẵn và đọc replies | Lặp lại import giữ local ID. Hai kênh có cùng external post ID nhận local ID riêng. Page ID dùng làm owner của media IG bị từ chối. | Local ID ổn định; GET replies trả row với parent `p`. Parent của kênh khác, parent của media khác và version không khớp đều dừng trước HTTP. 401 chuyển đúng Channel sang REAUTH_REQUIRED; cache trước đó còn đọc được. |
| Rollback giữa trang | Trigger SQLite làm UPDATE checkpoint thất bại sau ghi payload và membership. | Sau mở lại DB, payload vắng, cache rỗng, checkpoint null và attempt ghi STORAGE. |
| Late callback và cleanup | Trong khi GET IG đang chờ, reconnect cùng Channel để tăng revision. | Response cũ bị từ chối. Revision mới giữ capability riêng; cleanup gọi tường minh trong fixture chỉ dọn revision cũ. Bài, comment Facebook và Draft vẫn đọc được. Hook SP-024 production còn chờ Minh. |
| Trang hai lỗi và mở lại DB | Trang đầu ghi `c1` và checkpoint MORE. GET kế tiếp trả 403 chưa phân loại. | Có 1 ID duy nhất và `lastCompleteSyncAt=null`. Checkpoint lưu cursor mã hóa; không chứa vị trí thô hoặc URL trang kế. Worker tạo lại gửi đúng vị trí, nhận `c1,c2`. Đọc offline được 2 ID duy nhất, checkpoint COMPLETE và mốc hoàn tất. |
| Process death qua WorkManager | Enqueue khi offline, force-stop, mở process khác, rồi bật mạng. | Hai pha queue/resume xác nhận PID đổi, giữ Work ID và local post ID, đọc được trang đầu trước khi bật mạng. Worker gửi 1 GET tiếp theo, hoàn tất và trả 2 row qua cache offline. |

Bốn ca Room candidate và 11 ca Channel chạy trong cùng nhóm. Bốn ca Worker fixture riêng kiểm hủy HTTP, invalidation, retry hữu hạn và mất mạng ở trang hai. Ca cold start chạy thêm bằng hai lệnh instrumentation, giữa hai lệnh có `am force-stop com.socialpilot.app`. Lần chạy đầu phát hiện fixture dùng Java Stream trên API 23. Đã thay bằng iterator tương thích minSdk trong store mô phỏng.

**Kết quả cuối tại 2026-11-27T12:05:26+07:00.** Mỗi API đạt 15/15 Room + Channel, 4/4 Worker fixture, 1/1 pha queue và 1/1 pha resume. Tổng hai API đạt **42/42 lượt instrumentation**, 0 fail và 0 skip. Hai pha cold start được đếm thành hai lượt cho cùng một kịch bản.

## App live và điều kiện đóng

| Provider | Scope, grant, request | Kết quả |
|---|---|---|
| Facebook Page v27.0 | READ_COMMENTS, chưa có grant/role, Channel/revision và bài được chỉ định cho app | NOT_RUN, 0 GET live. Unique ID, terminal, refresh, offline readback production chưa đo. |
| Instagram Professional v27.0 | READ_COMMENTS, chưa có grant/role, Channel/revision và media được chỉ định cho app | NOT_RUN, 0 GET live. IG-03 và IG-05 là bằng chứng Explorer lịch sử. |

Đạt/SP-012 cần chốt base, thứ tự và version cho migration cùng Thiện. Sau đó đăng ký bốn entity candidate và DAO trên AppDatabase, thêm migration và export, chạy fresh install và mọi đường upgrade trên API 23/36. Minh/SP-022 cần cấp scope READ_COMMENTS và target đủ hai trang cho từng provider; Minh/SP-024 xác nhận thứ tự invalidation/cancel/cleanup và hook. Nghị chạy GET hai trang, terminal, refresh và readback sau restart trên app rồi cập nhật manifest.

Trên head remote `ca77f62`, GitHub trả 4 check Android/backend thành công. Thiện approve `ca77f62`; lượt review quyết định gần nhất của Đạt yêu cầu sửa tại `bc34d96`. Gói local mới cần CI và hai approval theo CONTRIBUTING trước khi merge. [PR #87](https://github.com/nigPeter2503/Social_Pilot/pull/87)

## Tái lập

Tại `android`, chạy bằng JDK 21. Tham số runner chọn Application fixture cho các ca WorkManager. Tên class đầy đủ của hai nhóm đầu là `com.socialpilot.app.core.CommentRoomDeviceTest` và `com.socialpilot.app.data.repository.ChannelRepositoryDeviceTest`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest '-PdeviceTestRunner=com.socialpilot.app.core.CommentDeviceRunner'
```

Cài APK app và androidTest lên AVD riêng. Để kiểm process death, tắt Wi-Fi và mobile data, chạy `CommentRoomDeviceTest#reopenedWorkerUsesEncryptedCursorAndKeepsOfflineRows` với `-e roomPhase queue`, force-stop app, rồi chạy cùng ca với `-e roomPhase resume`. Pha resume bật lại mạng. Tại root chạy bốn script Node trong yêu cầu và `git diff --check`.
