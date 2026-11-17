# SP-011 — Contract sửa feedback cuối và tích hợp main

Ngày soạn: 17/11/2026, Asia/Bangkok. Trạng thái: bản kế hoạch thực hiện; các checkbox chưa đánh dấu là việc chưa thực hiện trong contract này.

- Owner và người tích hợp: Ngô Gia Minh (`nigPeter2503`, TV1).
- Reviewer: Nguyễn Đức Đạt (`dat532006`) và Tô Lãng Nghị (`bob35306`), hai người khác tác giả.
- Ticket: [Issue #13](https://github.com/nigPeter2503/Social_Pilot/issues/13); [PR #77](https://github.com/nigPeter2503/Social_Pilot/pull/77), nhánh `feat/sp-011-linkedin-oauth-spike`, đích `main`.
- Yêu cầu: F1.B1, F1.B3, F5.B1, F5.B5; sửa provenance và tài liệu trong phạm vi spike hiện có.
- Mốc bàn giao của đợt sửa: hoàn tất trước lượt re-review tiếp theo; merge sau khi đủ các gate ở bước 8. Chưa cam kết ngày merge vì phụ thuộc reviewer. Mốc W1 gốc đã trễ, không dịch lại lịch task.
- Nguồn quy tắc: [CONTRIBUTING](../../CONTRIBUTING.md), [brief TV1](../TaskBrief/W1/tv1-minh-taskbrief.md#sp-011), [Social v1](../social-contract.md), [handoff](../sp-011-handoff.md), [contract sửa trước](sp-011-review-fix-contract.md).

## 1. Baseline và phân loại việc cần giải quyết

GitHub được đọc lại khi soạn: head `75cda640f9095803d76b5c3db59305f3d9d7986e`; tip main `230c2a6ebcd8785288e57764c4ebb5fb6bbd4afa`; PR OPEN, mergeable=false/dirty; 0 approval, ba COMMENTED của Đạt và một COMMENTED của Nghị. Base SHA API trả `7582fb38aa1d079f669e53464f3e19d4fb7be8ea` không được dùng thay tip main hiện tại.

Dependency SP-010/#74 đã squash merge tại `c7b0b9ca12fc8e96b6166dde9074f26ab871456b`; head dependency là `d1d55a03ea9bafd3073946b1376ee37d36d9689f`. `15875c4` là mốc dùng đối chiếu phần riêng SP-011. Khi thực hiện phải đọc lại remote vì snapshot này có thể thay đổi.

| Hạng mục | Mức độ / tác động | Điều kiện xử lý |
|---|---|---|
| Nguồn OWNER_CONFIRMED chưa lưu theo attempt | P3, không chặn merge trong review; thiếu truy vết nội bộ | Lưu nguồn theo đúng attempt và kiểm tra sau reload |
| README nói chưa thử thiết bị/API thật | P3, không chặn merge; gây hiểu sai evidence | Đồng bộ trạng thái và dẫn nguồn hiện hành |
| Conflict với main sau squash dependency | Chặn tích hợp | Giải conflict theo hành vi, giữ thay đổi hai phía |
| Thiếu hai approval | Chặn merge theo CONTRIBUTING §4 | Hai người thật review và approve phiên bản cuối |
| Kết quả test cũ trước tích hợp | Chưa đủ chứng minh bản mới | Chạy lại gates trên source sau sửa/giải conflict |
| Refresh chưa được cấp; expiry/revocation thật chưa thử | Khoảng trống nghiệm thu F1.B3/Issue #13 | Giữ BLOCKED/đạt một phần và NOT TESTED; theo dõi có owner |
| Cold start giữa OAuth, hai provider thật cùng kết nối | Hồi quy mở rộng chưa kiểm chứng | Ghi giới hạn; không tự chuyển thành PASS |

Chín finding P1/P2 trước đã có sửa ở baseline. Chúng là bất biến hồi quy cần bảo vệ khi merge, không phải chín lỗi mới phải viết lại.

## 2. Chuẩn bị checkout và điểm khôi phục

1. Đọc lại Issue #13/Project để xác nhận owner và trạng thái hiện hành; trước triển khai cập nhật bước sửa/review theo phạm vi ủy quyền. Không tự báo tracker đã cập nhật chỉ vì đã viết tài liệu.
2. Dùng worktree `build/sp011` đang giữ nhánh SP-011. Kiểm tra `git status --short`, HEAD, upstream, staged diff. Checkout main ở root đang có file untracked của công việc khác, phải giữ nguyên.
3. Fetch remote, ghi SHA head và main vào báo cáo. Nếu remote head đã đổi, đọc diff mới và cập nhật contract trước khi áp dụng bản sửa; không reset/ghi đè thay đổi mới.
4. Tạo ref dự phòng tại head trước sửa, với tên duy nhất như `backup/sp-011-before-final-integration-<timestamp>`. Ghi SHA và đường dẫn worktree trong báo cáo.
5. Chỉ stage các file thuộc đợt sửa. Không dùng `git add .`; không xóa cache, database hay credential để làm test đạt.

Đầu ra: baseline rõ, worktree phù hợp, điểm khôi phục còn truy cập được, không trộn thay đổi người khác.

## 3. Sửa P3: provenance đúng attempt

### Thiết kế tối thiểu

Giữ PublishResult/Social v1 và Room nguyên hợp đồng. Trong LinkedInAttemptLedger, dùng metadata riêng cho từng attempt, ví dụ khóa `url_verification_<attemptKey>` chứa `requestId`, `postId`, `postUrl`, `source=OWNER_CONFIRMED`. Ràng buộc metadata với requestId/ID/URL giúp không gán nguồn cũ cho attempt mới dùng lại cùng khóa.

1. Thêm regression trước sửa: tạo A, giữ target A, tạo B, xác nhận A, reload preferences/ledger. Assertion nguồn của A phải thất bại ở baseline; lưu kết quả RED.
2. Trong `confirm(target,url)`, dưới cùng khóa đồng bộ, kiểm tra target/key/requestId/Post ID và trạng thái hợp lệ như hiện tại.
3. Tạo kết quả SUCCESS của A và metadata provenance. Ghi record A + metadata A trong cùng SharedPreferences.Editor.commit. Chỉ trả thành công khi commit thành công.
4. Cập nhật `last_payload`/`last_result` chỉ khi `target.key == last_key`. Không đổi con trỏ mới nhất sang A khi B vẫn là bài mới nhất. Publisher tiếp tục trả State của A cho caller xác nhận A.
5. Thêm accessor nội bộ đọc provenance theo target, xác thực requestId/Post ID/URL trùng record. Metadata thiếu/hỏng/không khớp trả chưa có nguồn xác minh, không suy thành provider readback.
6. Khóa toàn cục `url_verification_source` không còn là nguồn dữ liệu chính. Không suy nguồn cho mọi record lịch sử từ giá trị toàn cục này. Có thể bỏ khóa cũ trong cùng commit khi xác nhận mới; kiểm tra không có consumer trước khi bỏ.
7. Bản ghi SUCCESS cũ vẫn đọc được ID/URL và giữ dedupe khi thiếu provenance. Không tự hạ thành UNKNOWN hoặc cho phép POST lại. Người dùng xác nhận lại đúng URL có thể bổ sung provenance mà không đăng lại.
8. Khi một attempt được thay hợp lệ bằng request mới, dọn metadata cũ của đúng key cùng thao tác ghi mới. Khi migrate attempt key cũ, chỉ chuyển metadata nếu khớp danh tính record; thiếu nguồn thì giữ thiếu, không bịa.

### Ca nghiệm thu bắt buộc

| Ca | Kết quả cần chứng minh |
|---|---|
| A pending → B pending → confirm A → reload | A SUCCESS, đúng ID/URL/requestId và OWNER_CONFIRMED; record B và toàn bộ last_* giữ nguyên |
| Confirm bài đang là last_key | Record, metadata và summary cùng chỉ bài đó |
| Confirm A cùng URL lần hai sau reload | Idempotent, không POST thêm, không chạm B |
| Target bị thay requestId/Post ID | Từ chối, không ghi provenance cho bản ghi thay thế |
| URL chứa share/ugcPost cùng loại nhưng khác ID | Từ chối, giữ pending và ID gốc |
| Metadata thiếu/hỏng/không khớp | Không gán nhầm nguồn; ID/URL và khóa chống trùng hiện có vẫn được giữ |
| Commit báo lỗi | Không trả State SUCCESS mới; kiểm tra sau reload không có record/metadata bị ghép sai |
| Luồng đi qua LinkedInSpikePublisher | Caller nhận State A; B còn pending; số POST không tăng do confirm |

Dùng MemoryPreferences/reboot và MockWebServer hiện có; bổ sung assertions vào test repository, không phụ thuộc hai init script ReviewOnly ngoài checkout. Lỗi commit cần kiểm cả bộ nhớ và dữ liệu bền vững theo fixture hiện hành; nếu cần sửa rollback, giới hạn ở ledger và thêm test thể hiện lỗi thực tế.

## 4. Sửa P3: README và hồ sơ bàn giao

1. Thay câu “kiểm thử trên thiết bị và API thật qua ứng dụng còn chờ” bằng nội dung: OAuth LinkedIn PERSON và đăng chữ qua app đã có bằng chứng thiết bị; URL do owner xác nhận; refresh và expiry/revocation thật chưa nghiệm thu; dẫn `docs/sp-011-handoff.md`.
2. Giữ rõ phạm vi một account/provider, text harness, chưa thay editor/queue/approval production và chưa nghiệm thu đầy đủ F1/F5.
3. Trong handoff/implementation/audit, thêm trạng thái hiện tại ở đầu và đánh dấu các đoạn head cũ/#74 còn mở/chưa push là lịch sử. Không sửa số test hoặc nguồn bằng chứng của các lần chạy trước.
4. Ghi thay đổi provenance, tính tương thích dữ liệu cũ và kết quả test mới. Giữ phân biệt OWNER_CONFIRMED và API readback.
5. Sau merge main, rà lại hướng dẫn cấu hình provider và kiến trúc v3; bỏ mô tả hiện hành mâu thuẫn với cấu hình enable/disable từng provider nếu gặp.
6. Kiểm tra liên kết tương đối và diff whitespace. Không cần đăng lại bài hay thu lại 11 ảnh Postman chỉ để sửa tài liệu.

## 5. Tích hợp main vào nhánh SP-011 và giải conflict

Thực hiện sau khi lưu bản sửa thành commit tập trung hoặc điểm khôi phục riêng, không merge khi có thay đổi chưa bảo toàn. Ưu tiên merge theo CONTRIBUTING, giữ PR #77 và lịch sử review.

```powershell
git fetch origin
git status --short
git rev-parse HEAD
git rev-parse origin/main
git diff 15875c4..HEAD --stat
git merge --no-commit --no-ff origin/main
git diff --name-only --diff-filter=U
```

1. Merge thực dùng tổ tiên Git bình thường. Không giả định chỉ README conflict: kết quả mô phỏng lấy 15875c4 làm mốc chỉ là công cụ đối chiếu phần SP-011.
2. Đối chiếu SP-010 ở head dependency với squash commit trên main để nhận diện nội dung trùng. Không copy toàn bộ cây cũ đè lên main và không dùng blanket ours/theirs.
3. Với OAuth backend: giữ registry hai provider, binding phiên/provider/state, TTL, consume một lần, gọi provider ngoài lock và kiểm lại phiên; giữ các cải tiến mới trên main nếu có.
4. Với Android: giữ callback queue, kiểm TTL/session khi dequeue, kho v3/marker, chặn token v2 cũ khi recovery, migration v2 lần đầu, channel/revision và rollback khi ghi lỗi.
5. Với publisher/ledger: giữ one-shot, chống đăng trùng, error/nextAction/Retry-After, target A/State A và bản sửa provenance mới.
6. Với README/docs/build/wiring: kết hợp nội dung hiện hành, giữ module/contract/CI đã có trên main; không hạ phiên bản hoặc xóa test của task khác để hết conflict.
7. Review từng file đã resolve; stage đích danh. Xác nhận không còn unmerged entry hoặc conflict marker. Kiểm tra cả staged diff và diff kết quả so với origin/main.
8. Chạy gates ở bước 6 trước hoàn tất bàn giao. Commit merge theo convention, ví dụ `chore(social): integrate main after SP-010 squash [SP-011]`.
9. Fetch lại trước push; nếu main đổi tiếp, đánh giá diff mới và tích hợp/kiểm tra phần bị ảnh hưởng. Nếu head remote đổi, đồng bộ có đối chiếu; không force-push.

Điều kiện đạt: main hiện hành là ancestor của nhánh tích hợp; diff PR chỉ còn thay đổi SP-011 có chủ đích; thay đổi độc lập trên main được giữ; không còn conflict. Nếu giải conflict sai và chưa hoàn tất merge, dùng `git merge --abort` sau khi đã bảo toàn phần sửa; không dùng reset cứng lên worktree đang chứa việc khác.

## 6. Kiểm thử trên bản tích hợp

Kết quả baseline đã chạy độc lập: Android 98 JVM tests, lint 0 lỗi/6 cảnh báo; backend 57 tests. Đây là đối chứng trước sửa, không phải kết quả bản mới. Số test mới được lấy từ XML thực tế, không điền sẵn 98/57 hoặc coi số lượng là tiêu chí duy nhất.

Tại `android`, JDK 21:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --rerun-tasks --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --rerun-tasks --console=plain
```

Tại `backend`, JDK 21:

```powershell
.\gradlew.bat test bootJar --rerun-tasks --console=plain
```

Có thể thêm `--offline` khi dependencies đã cache. Không coi build APK instrumentation là đã chạy test thiết bị. Chọn thiết bị/emulator rõ ràng; giữ cấu hình APK đang dùng nếu cài test có thể thay ứng dụng thử thật.

Phải kiểm lại: provenance A/B sau reload; 503 Retry-After:0 chỉ một POST; 401/reconnect/duyệt lại; 403/429 và hạn chờ; dedupe qua reload/recovery; URL mâu thuẫn; callback trùng/hết TTL/bị thay phiên; migration v2 lần đầu và stale-v2 có marker; lỗi commit/rollback; state/replay/TTL/backend provider binding. Instrumentation dùng credential giả và preferences riêng, Keystore/Activity Intent thật.

Ghi commit hoặc source hash khi chưa commit, main SHA đã tích hợp, ngày/môi trường/JDK/device API, lệnh, exit code, XML counts, lint warnings, lỗi và mục chưa chạy. Nếu thiếu thiết bị, ghi instrumentation NOT RUN và giữ gate liên quan đang chờ. Chạy `git diff --check`, kiểm links và ảnh evidence còn nguyên. Không gọi provider thật hoặc đăng thêm bài để kiểm tra hai P3.

## 7. Xử lý khoảng trống nghiệm thu và bàn giao Nghị

1. F1.B3 giữ BLOCKED/đạt một phần: app/product thử chưa nhận refresh token; reconnect là OAuth mới. Không đổi thành N/A/PASS vì có test parse refresh field.
2. Expiry/revocation thật giữ NOT TESTED. Handoff ngày 15/11 ghi owner không yêu cầu thêm phép thử thu hồi thật trong đợt bàn giao; ghi nhận giới hạn này và tiêu chí Issue #13 còn mở, không ép thêm thao tác tài khoản trong đợt sửa code.
3. Nếu cần nghiệm thu đầy đủ sau này, Minh chuẩn bị ca riêng: account/grant/revision, cách gây mất hiệu lực, kỳ vọng reauth/giữ dữ liệu/chặn retry; chạy trong phạm vi chủ tài khoản cho phép rồi ghi response và recovery. Không sửa đồng hồ hoặc gọi reconnect là refresh.
4. Cold start giữa OAuth và hai provider thật đồng thời tiếp tục ghi chưa thử riêng; mock không thay evidence thật.
5. Minh bàn giao cho SP-019: PERSON/author URN, Post ID/URL đúng bài, scope/product/tier/API version và đường lấy token thử còn hiệu lực đúng account/revision qua kênh bảo mật. Không ghi token vào contract/issue/log.
6. Nghị xác nhận đã nhận và dùng được đầu vào; Nghị sở hữu kiểm chứng comment/reply/metrics/follower và blocker từng operation. Không suy capability từ đăng chữ thành công. Có token thử phù hợp thì SP-019 có thể thử độc lập trước merge #77.
7. Production SP-027 → SP-022 → SP-041/SP-045 tiếp tục ở ticket của chúng. Không đưa triển khai Room/PublishedPost/analytics vào bản sửa SP-011.

Đầu ra: mỗi khoảng trống có owner, nguồn, trạng thái và hành động tiếp theo. Merge code spike không tự đóng Issue #13 hoặc nghiệm thu F1.B3. Nếu chuyển việc còn thiếu sang ticket khác phải có quyết định phạm vi rõ và liên kết truy vết, không tự hạ tiêu chí gốc.

## 8. Đưa bản sửa lên PR và squash vào main

1. Commit chỉ file thuộc task, ví dụ `fix(social): persist URL provenance per attempt [SP-011]` và `docs(social): align LinkedIn verification status [SP-011]`; merge commit ở bước 5 giữ riêng để reviewer nhận diện.
2. Push nhánh SP-011 theo ủy quyền thực hiện; cập nhật PR body bằng hành vi cuối, head/main SHA, tests mới và giới hạn F1.B3. Dùng `Refs #13` nếu tiêu chí issue vẫn chưa đầy đủ, tránh tự đóng bằng `Closes`.
3. Chuẩn bị bảng finding → commit → test → kết quả. Chỉ gửi phản hồi/request review khi có ủy quyền gửi; không coi contract này là đã gửi thông báo cho Đạt/Nghị.
4. Chờ CI Android/backend của head mới hoàn tất. CI xanh trên 75cda64 không thay CI head tích hợp.
5. Đạt review storage/migration/recovery và tích hợp backend; Nghị review provenance/ID/URL/handoff. Cả hai cần approve phần cuối; COMMENTED không tính approval.
6. Đọc lại head, base, conflict, required checks và reviews ngay trước merge. Nếu head thay đổi đáng kể sau approval, xin reviewer xác nhận lại. Không bypass hoặc dùng admin merge để bỏ hai approval.
7. Khi tất cả gate đạt, squash PR #77 vào main, kiểm tra tiêu đề squash đúng convention; thao tác phải ràng buộc đúng head đã kiểm tra nếu dùng CLI/API.
8. Fetch và xác minh merge commit trên main chứa sửa provenance, README và thay đổi tích hợp; theo dõi CI trên main. Nếu CI main lỗi, báo đúng lỗi và sửa bằng PR tiếp theo; không ghi tích hợp đã đạt hoàn toàn.
9. Cập nhật handoff/Issue/Project theo kết quả thực tế. PR merged nhưng tiêu chí còn thiếu thì giữ issue mở và blocker rõ. Chỉ xóa nhánh khi không còn việc/handoff cần giữ.

## 9. Checklist bàn giao

- [ ] Baseline và main SHA được ghi; không mất thay đổi ngoài task.
- [ ] Test provenance RED trước sửa, GREEN sau sửa; A/B/last_* đúng sau reload.
- [ ] Metadata cũ tương thích; không gán nguồn toàn cục sang bài khác.
- [ ] README và trạng thái đầu hồ sơ thống nhất với evidence.
- [ ] Main được merge vào nhánh task; conflict đã resolve và diff được review.
- [ ] Android/backend/instrumentation áp dụng đạt trên source cuối; evidence ghi đúng phạm vi.
- [ ] CI head mới xanh; các nhận xét bắt buộc đã xử lý.
- [ ] Đạt và Nghị approve phiên bản cuối, khác tác giả.
- [ ] Squash merge thành công; nội dung và CI main được đọc lại.
- [ ] Issue/Project phản ánh đúng phần đã xong, F1.B3 còn thiếu và bàn giao SP-019.

Contract này mô tả trình tự sửa và tích hợp; việc tạo file không xác nhận các bước triển khai, push, review hoặc merge đã xảy ra.
