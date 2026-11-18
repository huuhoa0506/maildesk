# Maildesk MVP

Ứng dụng cục bộ quản lý trường học và các đợt gửi `Reminding`, dùng Spring Boot, Thymeleaf, CSS/JavaScript thuần, Spring Data JPA và SQLite.

## Chạy ứng dụng

### Chạy từ mã nguồn

Yêu cầu JDK 17. Tạo khóa mã hóa một lần và giữ nguyên khóa này cho các lần chạy sau:

```bash
export APP_ENCRYPTION_KEY="$(openssl rand -base64 32)"
./gradlew bootRun
```

Mở [http://localhost:8080](http://localhost:8080). Ứng dụng chỉ bind vào `127.0.0.1`; chưa có đăng nhập/phân quyền, vì vậy không đổi bind address hoặc public ứng dụng lên mạng.

Khóa `APP_ENCRYPTION_KEY` phải là Base64 của đúng 32 byte. Khóa không nằm trong SQLite hay repository. Nếu mất/đổi khóa, các app password đã mã hóa không thể giải mã; hãy lưu khóa an toàn ngoài máy chủ ứng dụng.

### Đóng gói cho Windows

Workflow **Package Windows app** trong GitHub Actions tạo gói `Maildesk-Windows-x64.zip` có sẵn Java runtime. Chạy workflow thủ công từ tab **Actions**, tải artifact, giải nén rồi mở `Maildesk.exe`. Ứng dụng tự mở trình duyệt; giữ cửa sổ console đang chạy trong khi sử dụng và đóng cửa sổ đó để dừng ứng dụng. Không cần cài Java riêng.

Ứng dụng đóng gói lưu SQLite, khóa mã hóa và log tại `%LOCALAPPDATA%\Maildesk`; gói cập nhật không xóa dữ liệu này. Sao lưu toàn bộ thư mục đó và giữ riêng tư. Chỉ bind vào `127.0.0.1`, không chia sẻ lên mạng.

## Bắt đầu dùng

1. Tải [file Excel đầu vào mẫu](samples/agent-records-sample.xlsx), hoặc tải trực tiếp từ `/templates/agents.xlsx`.
2. Mở **Quản lý trường**, nhập địa chỉ người gửi và thông tin SMTP của trường. Mật khẩu cần là app password. Bật STARTTLS nếu nhà cung cấp yêu cầu (thường là cổng 587), lưu trường rồi dùng **Kiểm tra SMTP**.
3. Chọn trường, tạo Reminding và tải file `.xlsx`. Sheet đầu tiên cần cột `Agent Email` cùng các cột dữ liệu trong mẫu: `StudentId`, `Name`, `CourseName`, `Intake Date`, `Status`, `AgentName`, `Std_Mobile`, `Duedate`, `TuitionFee Amount`, `MiscFee Amount`, `Total Paid Amt`, `Total Due Amt`.
4. Các dòng có cùng địa chỉ `Agent Email` (không phân biệt hoa/thường) được nhóm thành một Agent. Dòng thiếu/sai email bị bỏ qua và báo theo số dòng; cùng email lặp lại là các AgentRecord riêng, không bị loại trùng. Có thể dùng biến template từ cột dữ liệu (ví dụ `{{AgentName}}`); biến email lấy giá trị từ AgentRecord đầu tiên.
5. Tạo Reminding, chọn Agent và gửi. Mỗi Agent nhận một email cùng file Excel riêng gồm toàn bộ AgentRecord của mình. File đầu ra lấy đúng 12 cột dữ liệu (không kèm `Agent Email`), theo định dạng mẫu đầu ra đã cung cấp.
6. Trạng thái, lỗi và thống kê được lưu theo từng Agent/Reminding; Agent gửi lỗi có thể chọn gửi lại.
6. Có thể mở Reminding đã tạo và chọn **Sửa nội dung** để cập nhật tiêu đề/template. Thay đổi áp dụng cho email chưa gửi và email gửi lại; không ảnh hưởng email đã gửi.

Template email và thông tin người gửi được lưu theo Reminding; tài khoản SMTP hiện hành được lấy từ School vào lúc gửi. File đính kèm cá nhân theo Agent được sinh trong worker khi gửi, không cần tải lên file đính kèm riêng.

## Chế độ gửi thử (log)

Đặt `app.mail.delivery-mode=log` trong `application.properties` để render email, sinh workbook theo từng Agent và ghi thông tin gửi vào log mà không kết nối SMTP. Nếu xử lý thành công, Agent được cập nhật thành **Đã gửi**, giúp kiểm thử trạng thái và thống kê như luồng thật. Xem log trong terminal đang chạy `bootRun` hoặc log của tiến trình ứng dụng. Log có thể chứa địa chỉ email và dữ liệu cá nhân, vì vậy chỉ dùng dữ liệu thử nghiệm.

Khi đã cấu hình SMTP thật và sẵn sàng gửi, đổi thuộc tính thành:

```properties
app.mail.delivery-mode=smtp
```

Sau đó khởi động lại ứng dụng. Chế độ `smtp` mới kiểm tra kết nối SMTP và chỉ cập nhật trạng thái thành **Đã gửi** sau khi máy chủ SMTP chấp nhận email.

## Hàng đợi và worker gửi email

Khi bấm gửi, ứng dụng lưu một job và từng người nhận vào SQLite rồi trả về Reminding ngay; không giữ request mở trong lúc gửi. Worker xử lý song song và trang tự cập nhật tiến độ, số thành công/lỗi. Cấu hình `app.mail.worker-count` (mặc định `3`) giới hạn số email xử lý đồng thời; `app.mail.queue.poll-interval-ms` cấu hình chu kỳ lấy job. Mỗi Reminding chỉ có một lượt gửi đang hoạt động, tránh tạo job chồng nhau.

Job đang chờ được giữ trong SQLite qua lần khởi động lại. Nếu ứng dụng dừng khi một email đang được xử lý, email đó được đánh dấu lỗi với cảnh báo cần xác minh hộp thư trước khi gửi lại; các email còn chờ tiếp tục được worker xử lý. Nên dùng số worker vừa phải theo giới hạn SMTP của nhà cung cấp.

## Lưu trữ và giới hạn MVP

- SQLite: `reminder.db` trong thư mục dự án khi chạy từ mã nguồn; `%LOCALAPPDATA%\Maildesk\reminder.db` trong gói Windows.
- Workbook đầu ra được tạo riêng trong bộ nhớ cho từng email; không lưu bản đính kèm phát sinh vào đĩa.
- App password được mã hóa AES-GCM; khi chạy từ mã nguồn, khóa lấy từ `APP_ENCRYPTION_KEY`. Gói Windows tự tạo và giữ khóa trong `%LOCALAPPDATA%\Maildesk\app-encryption.key`.
- Agent và danh sách AgentRecord được lưu cục bộ trong SQLite. Hãy sao lưu database và giữ riêng khóa mã hóa.
- Hàng đợi gửi nền xử lý song song theo `app.mail.worker-count` (mặc định 3); chọn giới hạn phù hợp với SMTP của nhà cung cấp. Chưa có đăng nhập, phân quyền nhiều người dùng, quản lý banner/logo hoặc xóa/khôi phục dữ liệu.
- Không gửi được nếu chưa cấu hình SMTP hợp lệ. Không dùng dữ liệu học sinh thật trước khi thiết lập bảo vệ máy, sao lưu và quy trình truy cập phù hợp.
