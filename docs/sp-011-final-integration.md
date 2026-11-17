# SP-011 — Sửa provenance và tích hợp main ngày 17/11/2026

Owner: Minh; reviewers: Đạt và Nghị. [Issue #13](https://github.com/nigPeter2503/Social_Pilot/issues/13), [PR #77](https://github.com/nigPeter2503/Social_Pilot/pull/77).

## Source và kết quả hiện tại

- Baseline review: `75cda640f9095803d76b5c3db59305f3d9d7986e`.
- Sửa code/tests/README: `489cbf2`.
- Tích hợp main `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa`: merge commit `7e1c8fa`.
- Backup local: `backup/sp-011-before-final-integration-20261117`.
- Issue đã đọc lại: OPEN, owner nigPeter2503, Project In Progress. Không chuyển Done.

Main hiện là ancestor của nhánh SP-011. 21 file conflict do lịch sử squash dependency được đối chiếu từng file bằng ba phía HEAD / 15875c4 / origin/main; phần main ở các file conflict không khác mốc 15875c4, nên kết quả giữ nội dung SP-011. Các file editor, taskbrief và evidence Facebook mới trên main được giữ trong merge. Không còn unmerged entries; diff so với main không sửa lại AI/editor của task khác.

## Hai feedback đã sửa

Ledger lưu `url_verification_<attemptKey>` cùng record SUCCESS trong một commit. Metadata chứa requestId, Post ID, URL và OWNER_CONFIRMED. Accessor chỉ trả nguồn khi cả danh tính và URL khớp record SUCCESS. Xác nhận A sau B không đổi record B hoặc con trỏ last_*. Khi commit thất bại, khôi phục các giá trị trước ghi và báo lỗi. Test mô phỏng cả lỗi ghi và lỗi rollback đồng bộ, kiểm tra bộ nhớ và dữ liệu sau reboot.

Record cũ thiếu metadata vẫn giữ ID/URL và khóa chống đăng lại; không suy provenance từ khóa toàn cục cũ. Xác nhận lại đúng URL bổ sung metadata, migration chỉ mang metadata khớp record. Lần đặt chỗ mới bỏ metadata cũ của đúng attempt key. Không đổi Social v1, OAuth DTO hoặc Room schema.

README đã bỏ câu còn chờ kiểm thử thiết bị/API thật, dẫn handoff và giữ giới hạn refresh/expiry/revocation. Các hồ sơ ngày 15/11 bên cạnh giữ vai trò lịch sử, không phải trạng thái GitHub hiện hành.

## Kiểm tra thực tế

Windows, JDK 21, Gradle Wrapper 8.13, offline. Các lệnh sau chạy trên source tích hợp được commit ở 7e1c8fa; commit merge không đổi thêm source sau khi test.

| Kiểm tra | Kết quả |
|---|---|
| Regression A/B trước sửa tại baseline, thêm assertion provenance | RED: 1 test thất bại vì không có source của A |
| Android `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --rerun-tasks --offline --console=plain` | PASS: 111 JVM tests, 0 failures/errors/skipped; lint 0 errors, 7 warnings |
| Backend `test bootJar --rerun-tasks --offline --console=plain` | PASS: 57 tests, 0 failures/errors/skipped |
| Android `:app:connectedDebugAndroidTest --offline --console=plain` | Chưa thực thi test: APK app/test build thành công nhưng task lỗi `No connected devices!`; không tính là instrumentation PASS |
| Diff whitespace, unmerged entries, main ancestry | PASS |

Ba tests mới: provenanceIsValidatedAndLegacySuccessKeepsDedupe, failedConfirmationRestoresMemoryAndDisk, migrationCarriesMatchingProvenance. Test A/B có assertion sau reload; đường publisher kiểm tra nguồn của A và vẫn giữ State/con trỏ B. Suite cũng chạy lại các regression 503, 401/403/429, URL, callback, token migration/recovery cũ. Số test tăng gồm cả editor tests từ main, không quy toàn bộ phần tăng là test SP-011 mới.

Lint 7 warnings: bốn ApplySharedPref (ghi đồng bộ có chủ đích, bao gồm rollback ledger mới), một UnusedResources và hai Autofill. Không tắt lint để thông qua gate.

## Việc cần người dùng / reviewer kiểm chứng

1. Kết nối thiết bị USB debugging hoặc mở emulator, cho phép ADB rồi báo đã sẵn sàng. Chạy lại instrumentation trên source tích hợp; test dùng credential giả và preferences riêng. Nếu chạy thiết bị đang giữ app OAuth thật, bảo toàn cấu hình APK trước khi cài test.
2. Đạt và Nghị review bản cuối và approve; COMMENTED không thay approval. Đọc lại CI/head/conflict trước merge. Hai approval và instrumentation áp dụng còn chờ thì chưa squash vào main.
3. Không cần đăng thêm bài để kiểm hai P3. F1.B3 vẫn BLOCKED/đạt một phần; expiry/revocation thật, cold start giữa OAuth và hai provider thật đồng thời chưa có evidence mới.
4. Minh xác nhận đường lấy token thử đúng account/revision qua kênh bảo mật, Nghị xác nhận nhận/dùng được đầu vào cho SP-019. Không yêu cầu gửi token trong chat hoặc repo.

## Giới hạn quy trình

Main đã được tích hợp vào nhánh task ở local, chưa đồng nghĩa PR đã merge vào main. Báo cáo này không tự xác nhận CI head mới, hai approval hoặc đóng Issue #13. Trạng thái remote phải được đọc lại sau push. [Contract thực hiện](drafts/sp-011-final-fixes-main-integration-contract.md) ghi các bước còn lại.
