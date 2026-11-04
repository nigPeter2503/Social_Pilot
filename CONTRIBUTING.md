# Quy tắc làm việc chung — SocialPilot

Tài liệu này áp dụng cho cả Android Java/XML, backend Java/Spring Boot, kiểm thử và tài liệu của nhóm. Mục tiêu là dễ phối hợp, dễ review và giữ bản tích hợp chạy được; không yêu cầu chia đều số task hoặc số commit.

Phạm vi chức năng và ranh giới vai trò nằm trong [proposal, mục 4](docs/SocialPilot_Proposal_Java.md#4-kế-hoạch-triển-khai-và-phân-công). Cách cài đặt, build và chạy nằm trong [hướng dẫn phát triển](docs/development.md).

## Căn cứ và mức áp dụng

Đồng bộ ngày 03/11/2026 theo bản tổng hợp hướng dẫn giảng viên do nhóm cung cấp (tài liệu trình chiếu và lời hướng dẫn trong video); đây không phải bản xác minh độc lập video gốc. Khi có thông báo chính thức mới, nhóm cập nhật các điểm liên quan trong tài liệu này.

- **Yêu cầu của giảng viên theo bản tổng hợp:** dùng cả GitHub Project và GitHub Repo; toàn bộ công việc có ticket, mô tả, assignee và trạng thái; mỗi ticket triển khai có nhánh riêng và PR về `main`; PR mô tả chi tiết và được ít nhất **2 thành viên khác review, approve** trước khi merge; có Slack channel và báo cáo tuần. Sản phẩm, quy trình và đóng góp cá nhân đều được đánh giá, không mặc định điểm các thành viên bằng nhau.
- **Quy ước nội bộ:** mã `SP-xxx`, cách đặt tên nhánh/commit, các cột trạng thái, squash merge, thời gian ưu tiên review và cách phân công tổng hợp. Nhóm có thể điều chỉnh các quy ước này nhưng không làm giảm yêu cầu của giảng viên.
- **Phạm vi đã chọn của nhóm:** giảng viên phân loại `[B]` bắt buộc, `[N]` nâng cao được lựa chọn; 11 mục `[N]` đã chọn trong proposal là cam kết của nhóm, không phải mọi mục nâng cao đều do giảng viên bắt buộc. Hiện giữ 44 yêu cầu đã cam kết; muốn giảm/đổi phạm vi phải ghi nhận và xin chấp thuận theo quy trình dự án.
- Môn học tập trung mobile/frontend, không bắt buộc tự xây backend. Backend OAuth Java hiện là lựa chọn kỹ thuật của nhóm để giữ client secret, không phải yêu cầu công nghệ của giảng viên. Chưa thay đổi kiến trúc hoặc bổ sung các mục nâng cao ngoài proposal trong lần cập nhật rules này.

## 1. Nhận và theo dõi task

- Bắt buộc dùng **GitHub Project và GitHub Repo**. Đưa toàn bộ tính năng/công việc vào ticket trên Project, với trạng thái: **Chưa làm → Đang làm → Review → Kiểm thử → Hoàn tất**. Task bị chặn phải ghi nguyên nhân, người/phần đang chờ và thời điểm cần xử lý.
- Mỗi task có một mã duy nhất (ví dụ `SP-023`), mô tả rõ việc cần làm, mã yêu cầu liên quan (ví dụ `F4.N1`), một người chịu trách nhiệm chính được **assign**, hai người review khi có PR, đầu ra/điều kiện đạt, hạn bàn giao và phụ thuộc. Việc hạ tầng dùng mã như `INFRA`, tài liệu dùng `DOCS` thay cho mã F.
- Nhóm dùng GitHub Issue làm ticket và đưa vào Project để dễ liên kết; `SP-023` phải được ghi trên issue tương ứng, không thay cho đường dẫn issue. Liên kết ticket → branch → PR và bằng chứng kiểm thử để đối chiếu đóng góp. Đây là cách tổ chức nội bộ nhằm thực hiện yêu cầu theo dõi của giảng viên.
- Ước lượng gồm lập trình, tích hợp, kiểm thử và tài liệu. Ưu tiên một task có thể bàn giao trong 1–2 ngày làm việc; task lớn hơn nên chia thành đầu ra nhỏ có thể review.
- Trước khi làm, nhận task trên bảng để tránh trùng việc. Mỗi người ưu tiên hoàn tất một task chính trước khi mở thêm nhiều task.
- Có thể đổi người thực hiện theo tải và kỹ năng. Bàn giao branch/PR, phần đã làm, kiểm thử đã chạy, phần còn thiếu và phụ thuộc; cập nhật người chịu trách nhiệm và đóng góp thực tế.
- Không tự bỏ yêu cầu bắt buộc hoặc giảm điều kiện nghiệm thu khi chuyển task. Thay đổi phạm vi phải được người đặt yêu cầu chấp thuận và ghi lại.

## 2. Nhánh Git

Nhóm dùng **`main` và các nhánh ngắn theo task**. `main` là bản tích hợp dùng chung; không duy trì nhánh dài theo TV1/TV2/TV3/TV4 hoặc thêm `develop` khi chưa có nhu cầu được nhóm thống nhất.

Tên nhánh theo mẫu:

```text
<type>/<task-id>-<mo-ta-ngan>
```

Viết thường, không dấu, dùng dấu `-` giữa các từ; mã task trong nhánh viết thường (`sp-023`). Không dùng tên chung như `test`, `new`, `final`, `minh-code`.

| Type | Khi sử dụng | Ví dụ |
|---|---|---|
| `feat` | Thêm chức năng | `feat/sp-023-draft-history` |
| `fix` | Sửa lỗi | `fix/sp-024-publish-duplicate` |
| `refactor` | Cải tổ mã, giữ nguyên hành vi | `refactor/sp-025-ai-error-mapping` |
| `test` | Bổ sung/sửa kiểm thử | `test/sp-026-budget-concurrency` |
| `docs` | Thay đổi tài liệu | `docs/sp-027-oauth-setup` |
| `chore` | Build, CI, thư viện, cấu hình | `chore/sp-028-android-ci` |

- Mỗi ticket triển khai có **một nhánh riêng và PR riêng về `main`**, tạo từ `main` mới nhất. Không gom nhiều ticket triển khai độc lập vào một nhánh/PR. Task lớn cần tách thành các ticket con có đầu ra rõ trước khi triển khai.
- Mọi thay đổi vào `main` đi qua pull request (PR), kể cả tài liệu và sửa lỗi gấp; không tự push thẳng hoặc force-push lên `main`.
- Không sửa hoặc force-push nhánh của người khác. Nếu cần rebase nhánh riêng đã push, báo người đang review và chỉ dùng `--force-with-lease`; ưu tiên merge `main` vào nhánh để đơn giản hóa phối hợp.
- Sau khi PR được merge và chắc chắn không còn việc chưa bàn giao, xóa nhánh task; task mới tạo nhánh mới từ `main`.

Ví dụ bắt đầu task (chạy tại thư mục chứa `.git`, sau khi đã commit hoặc cất an toàn thay đổi đang có):

```powershell
git switch main
git pull --ff-only origin main
git switch -c feat/sp-023-draft-history
```

Đồng bộ nhánh đang làm:

```powershell
git fetch origin
git merge origin/main
```

Nếu có conflict, người phụ trách task phối hợp với người sửa phần liên quan để giữ đúng hành vi hai bên, rồi chạy lại kiểm tra bị ảnh hưởng. Không chọn toàn bộ một phía chỉ để hết conflict.

## 3. Commit

Mẫu commit và tiêu đề PR:

```text
<type>(<scope>): <mô tả ngắn> [<TASK-ID>]
```

Type dùng như bảng nhánh. Scope chọn `android`, `backend`, `ai`, `social`, `editor`, `analytics`, `docs`, `ci` hoặc tên module rõ nghĩa. Có thể viết mô tả bằng tiếng Việt hoặc tiếng Anh; tên trong mã nguồn dùng tiếng Anh.

```text
feat(editor): lưu và khôi phục phiên bản bản nháp [SP-023]
fix(social): chặn đăng lại khi chưa xác định kết quả [SP-024]
docs(docs): cập nhật hướng dẫn OAuth [SP-027]
```

- Mỗi commit có mục đích rõ; không gom sửa giao diện, nâng thư viện và đổi schema không liên quan vào một commit/PR.
- Xem `git diff` và `git diff --staged` trước khi commit; chỉ stage file thuộc task. Không đưa thay đổi đang làm của người khác vào commit của mình.
- Không commit file build, cấu hình máy cá nhân, API key, token, client secret, keystore, dữ liệu người dùng hoặc log chứa bí mật. Tuân thủ `.gitignore`; chỉ đưa mẫu cấu hình với giá trị giả vào repo.
- Nếu bí mật bị commit, báo ngay cho nhóm, thu hồi/đổi bí mật và phối hợp xử lý lịch sử; xóa ở commit tiếp theo không đủ.

## 4. Pull request và review

1. Tự review diff, chạy kiểm tra phù hợp và cập nhật tài liệu trước khi yêu cầu review. Có thể mở Draft PR sớm để trao đổi hợp đồng hoặc thay đổi lớn.
2. PR liên kết ticket trên Project/Issue, ghi mã yêu cầu và mô tả chi tiết việc đã làm, vấn đề và kết quả sau thay đổi, cách kiểm thử cùng kết quả thực tế. Thay đổi UI có ảnh hoặc video ngắn; đổi dữ liệu/API ghi migration và ảnh hưởng tới người tích hợp.
3. Chỉ định ít nhất **hai thành viên khác người tạo PR** review. Chọn một người am hiểu phần thay đổi (ưu tiên TV1 ↔ TV2 cho bảo mật/hàng đợi/ngân sách, TV3 ↔ TV4 cho giao diện/dữ liệu) và một thành viên còn lại; luân phiên để phân bổ tải. Thay đổi hợp đồng chung phải có người sử dụng hợp đồng tham gia review.
4. Cả hai người review kiểm tra kỹ hành vi, ranh giới module, tình huống lỗi, kiểm thử và khả năng tích hợp; nêu rõ nhận xét nào cần sửa, nhận xét nào là gợi ý. Có thể dùng AI review trước, nhưng AI/bot không thay thế hai thành viên và không được tính là approval của thành viên nhóm.
5. Chỉ merge khi không còn conflict hoặc nhận xét bắt buộc chưa xử lý, có ít nhất **2 approval từ 2 thành viên khác nhau, khác người tạo PR**, và các kiểm tra áp dụng đã đạt. Sau thay đổi đáng kể, yêu cầu cả hai reviewer xác nhận lại phần mới. Tác giả không tự approve thay người khác.
6. Dùng **Squash and merge** để mỗi PR thành một commit rõ nghĩa trên `main`; người merge kiểm tra lại tiêu đề commit theo mục 3. Người merge có thể là chủ PR khi đã đủ điều kiện, không cần dồn mọi lượt merge cho TV1.

Ưu tiên review trong một ngày làm việc là quy ước nội bộ. Nếu reviewer bận, đổi reviewer trên bảng task; không giảm xuống một approval hoặc bỏ review để bù thời gian chờ. Người quản lý repo cần cấu hình bảo vệ `main` yêu cầu PR, tối thiểu 2 approval và kiểm tra CI áp dụng; tài liệu này chưa tự bật các thiết lập đó trên GitHub.

## 5. Quy ước mã nguồn

| Thành phần | Quy ước |
|---|---|
| Java class/interface/enum | `PascalCase`, ví dụ `PublishQueue`, `AiProvider` |
| Method, field, biến cục bộ | `camelCase`, ví dụ `loadDrafts`, `channelId` |
| Hằng số | `UPPER_SNAKE_CASE`, ví dụ `MAX_RETRY_COUNT` |
| Package | Chữ thường, theo cấu trúc package hiện có; không tạo package theo tên thành viên |
| Android resource/file XML | `snake_case`, tên thể hiện mục đích, ví dụ `fragment_draft_editor.xml`, `draft_title` |
| Tài liệu mới | Tên ngắn bằng tiếng Anh, `kebab-case.md`; giữ các tên quy ước như `README.md`, `CONTRIBUTING.md` |

- Tuân thủ `.editorconfig`: UTF-8, xuống dòng LF, có newline cuối file; Java dùng 4 dấu cách, XML/YAML dùng 2 dấu cách. Không format lại cả file không liên quan.
- Dùng Java và các phiên bản đã khóa của dự án. Nâng JDK/Gradle/thư viện hoặc đổi stack cần task riêng, nêu lý do và được người bị ảnh hưởng review.
- Activity/Fragment xử lý giao diện; ViewModel quản lý trạng thái; nghiệp vụ và dữ liệu đi qua lớp dịch vụ/Repository. Không gọi API hoặc tính toán nghiệp vụ phức tạp trực tiếp trong View.
- Chuỗi hiển thị cho người dùng nằm trong resource VI/EN. Mỗi module tự xử lý loading, rỗng, lỗi, thiếu quyền và offline.
- Không chạy mạng hoặc tác vụ nặng trên main thread. Dùng mẫu stream/hủy và quản lý vòng đời đã thống nhất; chặn callback muộn sau hủy/ngắt kênh.
- Đặt tên rõ nghĩa, tách hàm theo trách nhiệm; comment giải thích lý do hoặc trường hợp khó. TODO cần mã task, không thay cho xử lý bắt buộc trước nghiệm thu.

## 6. Phối hợp phần dùng chung

Ánh xạ thành viên: TV1 — Ngô Gia Minh; TV2 — Nguyễn Đức Đạt; TV3 — Đỗ Phước Thiện; TV4 — Tô Lãng Nghị.

Nguyễn Đức Đạt (TV2) điều phối schema Room chung và quy tắc migration; mỗi chủ module vẫn thiết kế và kiểm thử dữ liệu phần mình.

- Tuần 1, Đạt cùng các owner chốt quy ước bảng/cột, ID, quan hệ, index/khóa duy nhất, thời gian/múi giờ, null/dữ liệu thiếu và quyền sở hữu/xóa dữ liệu theo kênh; ghi thiết kế đã thống nhất trong tài liệu kiến trúc.
- Chủ module đề xuất entity/DAO, migration và kiểm thử bảo toàn dữ liệu trong ticket/PR. Đạt chốt số phiên bản, thứ tự tích hợp và thay đổi database/schema chung trước khi sửa. Mỗi thời điểm chỉ một task sửa khai báo database/version và chuỗi migration chung; module khác phát triển theo hợp đồng đã chốt.
- PR đổi schema do người khác tạo có Đạt và một người sử dụng dữ liệu review. Nếu Đạt là tác giả, hai thành viên khác review, có người sử dụng dữ liệu; Đạt không tự approve.
- Chủ task chạy kiểm thử migration từ các phiên bản đang hỗ trợ và kiểm tra dữ liệu/ràng buộc bị ảnh hưởng; Đạt kiểm tra khả năng ghép chuỗi migration. Không dùng migration phá hủy.
- Tính giờ điều phối vào tải TV2; không giao toàn bộ entity, DAO, repository hoặc sửa lỗi dữ liệu cho Đạt. Nếu cần người thay thế, ghi bàn giao trong ticket trước khi sửa phần chung.

- Trước khi đổi interface, DTO, schema Room, cấu hình build hoặc định dạng lỗi chung, báo người phụ trách và người sử dụng; chốt đầu vào/đầu ra, lỗi và mốc bàn giao trong task/PR.
- TV1 phụ trách OAuth/token và API đăng; TV4 trực tiếp làm API comment/số liệu. TV2 cung cấp nền tảng AI; TV3/TV4 tự triển khai prompt và nghiệp vụ module qua dịch vụ đó, không gọi provider ngoài luồng kiểm soát ngân sách.
- TV4 — Tô Lãng Nghị sở hữu API, snapshot, repository, công thức, lưu mục tiêu/tính tiến độ và F7.N2 (nghiệp vụ cùng giao diện báo cáo AI). TV3 — Đỗ Phước Thiện sở hữu XML/ViewModel F7.B1 (số liệu từng bài), F7.B2 (đường tăng trưởng), F7.B3 (dashboard), F7.N1 (phân tích theo nhóm), F7.N3 (mục tiêu); không tính lại chỉ số trong UI. Snapshot thật bắt đầu từ tuần 2–3 khi có quyền API, tuần 4 dùng lịch sử đã tích lũy; thiếu quyền phải ghi blocker, giữ khoảng trống dữ liệu.
- Mỗi chủ task tự làm lưu offline, VI/EN, kiểm thử, tài liệu và tích hợp phần mình. Người điều phối không là người mặc định sửa mọi lỗi.
- Khi API hoặc module khác chưa sẵn sàng, có thể dùng dữ liệu mẫu được ghi nhãn để phát triển; không dùng mock thay bằng chứng nghiệm thu tích hợp thật.
- Đổi schema phải xem xét migration và dữ liệu đang có. Không xóa database hoặc dùng migration phá hủy để che lỗi mất bản nháp, hàng đợi hay lịch sử.

## 7. Kiểm tra trước khi hoàn tất

Chạy từ thư mục tương ứng, dùng Gradle Wrapper của dự án:

| Thay đổi | Kiểm tra tối thiểu |
|---|---|
| Android Java/XML/resource/build | Tại `android`: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` |
| Backend Java/cấu hình/build | Tại `backend`: `.\gradlew.bat test bootJar` |
| Luồng UI, vòng đời hoặc tác vụ nền | Kiểm tra tương ứng trên emulator/thiết bị, thêm kiểm thử tự động phù hợp với hành vi thay đổi |
| Chỉ tài liệu | Đọc lại nội dung, kiểm tra liên kết tương đối và `git diff --check`; không bắt buộc build ứng dụng |

Đổi hợp đồng xuyên Android/backend phải kiểm tra cả hai phía. Lỗi nghiệp vụ cần ca kiểm thử thể hiện lỗi và kết quả mong đợi; ưu tiên các trường hợp mất dữ liệu, đăng trùng, hủy tác vụ, vượt ngân sách hoặc mất quyền. Ghi rõ kiểm tra chưa chạy và nguyên nhân, không báo đạt theo suy đoán.

Ticket triển khai chỉ **Hoàn tất** khi đáp ứng đầu ra đã thống nhất, PR có đủ 2 approval và đã merge, kiểm tra phù hợp đã đạt, tài liệu/bằng chứng được cập nhật và trạng thái trên GitHub Project được cập nhật. Việc không tạo thay đổi repo (họp, demo, tổng hợp tiến độ) vẫn có ticket, assignee và bằng chứng kết quả, không tạo PR rỗng. Task con hoàn tất không tự động làm cả yêu cầu F đạt; yêu cầu phụ thuộc API chưa kiểm chứng vẫn ghi chưa đạt hoặc bị chặn trong ma trận nghiệm thu.

## 8. Nhịp làm việc và bàn giao cuối kỳ

Kế hoạch nội bộ gồm 6 tuần, tuần 1 là 02–08/11/2026 (thứ Hai–Chủ nhật). Demo tích hợp cuối tuần 5 (06/12/2026); tuần 6 (07–13/12/2026) kiểm thử, sửa lỗi, nghiệm thu và hoàn thiện bàn giao. Mỗi người cam kết 24 giờ/tuần, gồm thiết kế, lập trình, review, họp, tích hợp, kiểm thử và tài liệu: 96 giờ/nhóm/tuần, 480 giờ đến demo và 576 giờ toàn đợt. Demo không thay nghiệm thu 44 yêu cầu; hạn nộp/bảo vệ chính thức theo thông báo giảng viên.

- Họp ngắn hai lần mỗi tuần: tiến độ, trở ngại, tải còn lại và quyết định chuyển task. Demo bản tích hợp vào cuối tuần.
- Khi bị chặn hoặc có nguy cơ trễ, cập nhật ngay kèm phần có thể tiếp tục; không đợi tới buổi họp hoặc ngày bàn giao.
- TV3 vận hành CI và đóng gói Android; TV1 triển khai backend và điều phối tích hợp. Lỗi build/test do chủ phần gây lỗi xử lý, có hỗ trợ khi cần.
- Mỗi người viết tài liệu và cung cấp bằng chứng phần mình. TV4 tổng hợp ma trận nghiệm thu và video; TV2 tổng hợp báo cáo/đóng góp; TV3 tổng hợp slide; TV1 tổng hợp kịch bản demo.
- Báo cáo đóng góp dựa trên task, PR, kiểm thử, review và sản phẩm thực tế, không chỉ số commit hoặc số dòng code. Điều chỉnh phân công lớn phải cập nhật proposal và README.

## 9. Slack và báo cáo tuần

- Tạo channel riêng theo đúng định dạng giảng viên ghi: **`2024_31_md_<ma_nhom>`**; thay `<ma_nhom>` bằng mã nhóm, không tự đổi `2024_31` theo năm hiện tại. Mời giảng viên và toàn bộ thành viên vào channel.
- Báo cáo có **Current tasks**, **Next plan**, **Blockers**. Mỗi task nêu người phụ trách; nhóm bổ sung kết quả cụ thể và link ticket/PR để dễ đối chiếu. Không giới hạn số task báo cáo là ba.
- Bản tổng hợp có hai mốc khác nhau: lời nói là Chủ nhật, tin nhắn hướng dẫn viết sau đó là cuối ngày thứ Bảy. **Nhóm tạm chọn gửi trước cuối thứ Bảy hằng tuần**, chờ thông báo làm rõ; không coi đây là hạn đã được xác minh dứt điểm và không tự gán giờ 23:59.
- Mỗi người cập nhật việc mình, kết quả, kế hoạch và blockers trước lúc tổng hợp. Người trực báo cáo luân phiên TV1 → TV2 → TV3 → TV4 theo tuần, kiểm tra đủ nội dung và gửi lên Slack; thay người trực phải ghi rõ trên Project. Việc này tách khỏi trách nhiệm TV2 tổng hợp báo cáo đồ án cuối kỳ.
- Theo dõi thông báo bổ sung về hạn báo cáo, ngày nộp/vấn đáp, bộ sản phẩm phải nộp và cách chấm điểm. Những lựa chọn kỹ thuật trong proposal không được trình bày thành xác nhận của giảng viên về quyền API, mock hoặc cơ chế bàn giao thủ công.

### Mẫu báo cáo tuần

```text
TUẦN <ID> — <khoảng ngày>
Người tổng hợp: <thành viên>

Current tasks:
- <Task, việc đã làm và kết quả> — <người phụ trách> — <link ticket/PR>

Next plan:
- <Task và đầu ra tuần tới> — <người phụ trách> — <link ticket>

Blockers:
- <Vướng mắc, ảnh hưởng và hỗ trợ cần> — <người theo dõi> — <link ticket>
- Ghi “Không có” nếu tuần này không có vướng mắc.
```

## Mẫu nội dung PR

```markdown
Task: SP-023
Ticket/Issue: <link issue đã đưa vào GitHub Project>
Yêu cầu: F4.N1
Reviewers: <thành viên 1>, <thành viên 2> — khác người tạo PR

### Thay đổi
- Vấn đề cần giải quyết và hành vi sau thay đổi.

### Kiểm tra
- Lệnh/ca thử đã chạy và kết quả.
- Ảnh/video nếu thay đổi UI.
- Phần chưa kiểm chứng và lý do, nếu có.

### Ảnh hưởng và bàn giao
- Hợp đồng/schema/cấu hình thay đổi, hoặc ghi không có.
- Phụ thuộc, migration và tài liệu liên quan.

### Điều kiện merge
- [ ] Hai thành viên khác đã review và approve phiên bản cần merge.
- [ ] Đã xử lý nhận xét bắt buộc và các kiểm tra áp dụng đã đạt.
- [ ] Ticket, bằng chứng và tài liệu đã được cập nhật.
```
