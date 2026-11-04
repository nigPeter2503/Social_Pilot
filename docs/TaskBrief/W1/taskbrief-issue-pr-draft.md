# Bản nháp issue và PR — Tạo taskbrief Tuần 1

**DRAFT — chưa tạo issue/PR, chưa assign hoặc request review trên GitHub.** Rà issue mở/đóng ngày 04/11/2026 thấy mã đã dùng đến SP-059; đề xuất SP-060, cần kiểm tra lại ngay trước khi tạo. Không dùng SP-001 vì issue #2/PR #3 đã đóng/merge.

Nhánh local đã chuẩn bị từ `origin/main`: `docs/sp-060-week-1-taskbrief` (chưa commit/push). Sau khi tạo issue, đưa vào [Project #5](https://github.com/users/nigPeter2503/projects/5), assign Minh, cập nhật In Progress và thay placeholder issue trong mô tả PR. Nếu SP-060 đã được dùng, đổi mã/branch/tiêu đề cho đồng nhất trước commit.

## Issue

**Title:** `[SP-060] Tạo taskbrief Tuần 1 cho nhóm`

**Description:**

```markdown
Yêu cầu: DOCS.
Owner: Ngô Gia Minh — @nigPeter2503.
Reviewers: Nguyễn Đức Đạt — @dat532006; Tô Lãng Nghị — @bob35306.
Branch: docs/sp-060-week-1-taskbrief → main.
Trạng thái đề xuất: In Progress. Ước lượng bổ sung đề xuất: 2–3h, gồm rà soát và sửa sau review.
Hạn đề xuất: trước khi phát hành bộ brief W1 (02–08/11/2026); chưa chốt ngày cụ thể.
Phụ thuộc: SP-001 (#2, đã merge qua PR #3); backlog W1 SP-009–SP-021.

Tạo bản tổng hợp và bốn taskbrief cá nhân trong docs/TaskBrief/W1, đối chiếu với issue/Project; làm rõ đầu ra, phụ thuộc, kiểm chứng, tải review và branch từng ticket.

- [ ] Owner, hai reviewer, requirement, ước lượng và mốc bàn giao khớp tracker; nêu rõ rủi ro còn lại.
- [ ] Branch dự kiến đã ghi trong 13 issue W1 và brief; phân biệt branch dự kiến với branch đã tạo.
- [ ] Liên kết/anchor và whitespace đạt; giữ 44 yêu cầu, hai approval trước squash merge và cập nhật Project.

Bàn giao cho TV1–TV4 qua PR riêng; không triển khai tính năng hoặc thay đổi assignee/phạm vi W1.
```

## Pull request

**Title:** `docs(docs): tạo taskbrief Tuần 1 cho nhóm [SP-060]`

**Description:**

```markdown
Closes #<số issue SP-060 sau khi tạo>
Yêu cầu: DOCS. Owner: @nigPeter2503. Reviewers: @dat532006, @bob35306.

Thêm bản tổng hợp và bốn taskbrief W1, thống nhất owner/reviewer, đầu ra, phụ thuộc, kiểm chứng và quy ước branch. Bổ sung branch dự kiến vào 13 issue; làm rõ trạng thái board, rủi ro tải/lịch và điều kiện nghiệm thu.

Kiểm tra: 13 issue khớp branch; 12 task khớp owner/reviewer/requirement và bảng review; 39 liên kết/anchor nội bộ hợp lệ. Sáu file mới đạt UTF-8/LF và whitespace (kiểm tra cả file untracked bằng diff --no-index --check). Không chạy Gradle vì chỉ sửa tài liệu; chạy lại git diff --staged --check sau stage.

Bàn giao: TV1–TV4; theo dõi quyền API và tiến độ trên issue. Chờ Đạt và Nghị approve phiên bản cuối, xử lý nhận xét bắt buộc rồi squash merge.
```

Trước khi dùng mô tả PR: thay số issue thật, kiểm tra `git diff --staged --check` sau khi stage và cập nhật kết quả kiểm tra thực tế của phiên bản gửi review.
