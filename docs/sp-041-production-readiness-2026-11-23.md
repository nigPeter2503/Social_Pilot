# SP-041 · Đối chiếu SP-066 và gate production

Owner Tô Lãng Nghị · 23/11/2026 20:43 +07:00 · giờ công thực tế: —.
**Phần độc lập đã kiểm; production/live chưa hoàn tất.**

## Revision và bảo toàn

- Baseline local và PR #87 đã đọc từ remote: `00108ab246c91ab41aff36dd5e320a2ce2f51eb2`.
- Main đã fetch: `a351435b2b9f93ae245bd15c8400e753147c2043`; chứa squash SP-066 `eda55acd8fb95a0dba9853a35e16fe8754245600` và SP-017. Merge vào checkout tại `498dd0e`, không conflict.
- Mã kiểm chứng mới: `f57cf554cd81a4b95f8fae369417eeabfbdc0ebc`. Commit tài liệu tiếp theo không đổi mã đã kiểm.
- Baseline không có tracked diff/staged diff. 22 hồ sơ untracked được chụp SHA-256 trước sửa và đối chiếu lại: **22/22 nguyên vẹn**, không stage/commit chúng.
- `AppDatabase` vẫn **v1**, chỉ có `DraftEntity`; schema `1.json` giữ nguyên hash. `PrototypeDatabase` v4 từ SP-017 là DB demo riêng, không phải slot cho Channel/comment.
- Hồ sơ local: `D:/CNTT/mobile/.reviews/sp041-sp045-20261123/` chứa baseline, log build, kết quả XML tổng hợp và bản đọc issue/PR. Không chứa credential.

## Đầu vào đã đọc và blocker

| Owner | Artifact thực tế | Kết luận và đầu ra cần nhận |
|---|---|---|
| Minh / SP-022 | Nhánh `origin/feat/sp-022-oauth-token-lifecycle` tại `3e18f98`, `docs/evidence/sp-022-fixture-verification.md`; issue #24 đã đọc | Lifecycle, scope/revision/grant là **MO_PHONG**, chưa có Channel entity/DAO production. Cần commit/schema root, PK, scoped lookup trong cùng AppDatabase, identity IG/Page, revision bền vững, credential reference và grant READ_COMMENTS của đúng media. |
| Minh / SP-024 | Issue #26 và Room contract §4/§7 đã đọc | Chưa nhận policy/hook production có revision cho invalidation → cancel → cleanup và recovery sau process death. Không tự chốt tombstone/retention. |
| Đạt / SP-012, Thiện / SP-035/036 | Room contract §6; `178e05c` có AppDatabase v2, `c6e3dd5` có v3; `adfd87c:docs/ai-budget-ledger-contract.md` §4 ghi v2/v3 thuộc PR của Thiện | Không có slot được cấp cho SP-041/045. Cần pin base/schema SHA, owner đang giữ lượt, version nguồn–đích và thứ tự Channel → PublishedPost → Comment/checkpoint/attempt → Snapshot. Không tự lấy v2/v3 hoặc schema demo. |
| Nghị / SP-066 | [Manifest IG-01–13](api-verification/instagram/verification-manifest.md), response đã che trên `eda55ac` | Đã có evidence API. Grant mỗi operation, role/target, Channel ID/revision production vẫn thiếu trong manifest. Không dùng ID Explorer để seed root production. |

Issue #14/#24/#26/#43/#47 và PR #87/#88 chưa có issue comments ở lượt đọc này; không có xác nhận slot/grant mới trong các artifact đã kiểm. Đây là phạm vi kiểm tra, không khẳng định mọi trao đổi ngoài repo đều chưa tồn tại.

## Mapping và expected → actual

Các test mới nằm trong `CommentAdapterTest`, chạy adapter HTTP thật của module với MockWebServer và **store RAM MO_PHONG**. Payload lấy trực tiếp từ thư mục evidence trên main; không sao chép giá trị Explorer thành dữ liệu production.

| Phép thử | Expected → actual | Nhãn |
|---|---|---|
| IG-01/03: account/media và page1/page2/refresh/terminal | IG user `17841459976957663`, media `18111630677338097`; C04/C03/C01 giữ nguyên chuỗi. Trang hai thêm overlap C04 mô phỏng; 3 ID duy nhất sau terminal + refresh → đạt. C02 không nằm trong chuỗi trang đã ghi nên không dựng thêm. | FIXTURE_TU_API_DA_CHE + MO_PHONG |
| Lỗi checkpoint sau khi ghi row | Row C03 rollback, checkpoint cũ giữ nguyên; retry cùng attempt commit được → đạt. Dựng lại writer/codec đọc cursor cùng key → đạt trên RAM; chưa chứng minh process restart/Room. | MO_PHONG |
| IG-04/05: rỗng và replies | `data:[]` là SUCCESS/terminal; GET readback trả reply `18120004997517397` thuộc parent `18105580130575258`, giữ media ID → đạt. Chỉ gửi GET tới MockWebServer, không POST lại IG-05. | FIXTURE_TU_API_DA_CHE |
| IG-12 lỗi insights không phải grant comment | Envelope Graph code 10 là FAILURE, không thành trang rỗng. Khi thiếu scoped READ_COMMENTS evidence, giữ lỗi chưa phân loại → đạt. Không chuyển quyền insights sang comment. HTTP 200 của test là mô phỏng vì evidence không ghi status. | FIXTURE_TU_API_DA_CHE + MO_PHONG |
| Username, parent root, cursor | Username không có trong evidence → null. Parent reply từ edge đã bind; comment root vẫn UNKNOWN khi thiếu evidence riêng. Cursor `[REDACTED_BY_USER]` được thay **tường minh trong test** bằng cursor synthetic; không chứng minh continuity cursor provider. | MO_PHONG |
| Regression hiện có | Offline/429 giữ cache/checkpoint/attempt; Retry-After; giới hạn Worker; stale revision, disconnect/reconnect, FB isolation; late update và transaction rollback → suite hiện hành đạt. | MO_PHONG |

## Kiểm chứng

Windows, Temurin 21.0.13+9 tại `.reviews/pr70-tools/jdk-21.0.13+9`, SDK `C:/Users/ASUS/AppData/Local/Android/Sdk`, compile/target 36, minSdk 23. Không đổi dependency.

```powershell
# tại android/
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# tại root checkout
node docs/api-verification/instagram/verify-fixtures.cjs
node docs/api-verification/instagram/verify-api-fixtures.cjs
node docs/api-verification/facebook/verify-fixtures.cjs
node docs/contracts/verify-analytics-examples.cjs
git diff --check
```

- Baseline chạy mới tại `00108ab`: **239/239** test, 13 suite, 0 failure/error/skip.
- Mã `f57cf55`: **244/244** test, 14 suite, 0 failure/error/skip; assemble đạt; lint **0 lỗi, 6 cảnh báo**. XML ở `android/app/build/test-results/testDebugUnitTest`, lint ở `android/app/build/reports/lint-results-debug.xml`.
- Hai validator Instagram đạt; Facebook **55/55** và analytics **75/75** đạt; diff check đạt. Không có lệnh Meta live.
- Lượt compile đầu của test mới lỗi do assertion đọc field package-private `generation`; đã thay bằng kiểm checkpoint object cũ còn nguyên sau rollback, rồi chạy lại toàn bộ lệnh trên thành công. Không đổi visibility API.

## Chuyển giai đoạn và bàn giao

**SP-041 chưa cung cấp PublishedPost local ID production.** SP-045 chỉ tiếp tục đối chiếu adapter/collector/fixture độc lập. Follower production chờ Channel + grant + slot; POST production còn chờ root bài đúng scope từ SP-041.

SP-042/043/SP-051 tiếp tục dùng [handoff inbox](handoffs/sp-041-published-post-input.md). Quyền reply quan sát trong cache không thay thế grant hiện hành; SP-066 IG-05 không được gửi lại. Nghị nhận Channel/slot rồi hiện thực Room store trên cùng transaction với root; kiểm FK/unique, rollback, persistent cursor/attempt, restart, cleanup revision và API 23/36 trước thu live.

[Manifest production](sp-041-live-manifest.json) ghi **NOT_RUN** cho Room, fresh install/upgrade giữ Draft v1, API 23/36 và GET comment thật ≥2 trang + refresh/readback. Không có count/ID/checkpoint/time production để báo đạt. Không đóng issue #43.
