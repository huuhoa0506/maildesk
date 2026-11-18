# Đặc tả công cụ gửi email Agent

## 1. Mục tiêu

Xây dựng công cụ quản lý nhiều trường và nhiều đợt gửi báo cáo cho Agent. Người dùng chọn trường, tạo Reminding từ file Excel nguồn, theo dõi trạng thái từng Agent và gửi bằng tài khoản email của trường. Mỗi Agent nhận một email kèm file Excel chỉ chứa các record của Agent đó.

Tài liệu này ghi lại yêu cầu đã trao đổi để duyệt trước khi triển khai backend và hoàn thiện giao diện.

## 2. Thuật ngữ và mô hình

Hệ thống có ba thực thể chính:

### School

Đại diện cho một trường và cấu hình gửi email của trường.

- Tên trường.
- Địa chỉ email gửi và tên hiển thị (nếu có).
- Cấu hình SMTP: máy chủ, port, username và app password.
- Banner/logo và thông tin thương hiệu dùng trong email (nếu có).
- Trạng thái hoạt động.

Mỗi School có nhiều Reminding. Cấu hình SMTP được quản lý tại School và có thể thay đổi khi ứng dụng đang chạy.

### Reminding

Đại diện cho một đợt thông báo/gửi email của một School. Mỗi lần cần gửi một thông báo mới, người dùng tạo một Reminding riêng; một trường có thể có nhiều Reminding trong cùng tháng.

- Thuộc về một School.
- Tên đợt, ngày tạo và người tạo.
- Danh sách Agent được nhóm từ Excel theo cột `Agent Email`.
- Template cấu trúc file Excel đầu ra; workbook được tạo riêng cho từng Agent khi gửi.
- Template nội dung email và tiêu đề email.
- Thông tin người gửi và các chi tiết cấu hình không bí mật được chụp lại tại thời điểm tạo, để lịch sử của đợt không tự thay đổi khi School được chỉnh sửa.
- Tệp đính kèm của đợt, bao gồm tệp Excel được người dùng chọn hoặc tạo trong quá trình chuẩn bị gửi, nếu có.
- Thống kê: tổng số email, đã gửi, chưa gửi và gửi lỗi.

Thông tin xác thực SMTP không được sao chép vào Reminding. Khi gửi, hệ thống lấy cấu hình SMTP hiện hành của School. Reminding vẫn giữ ảnh chụp thông tin người gửi/template của mình để tra cứu lịch sử.

### Agent

Đại diện cho một người nhận email duy nhất trong một Reminding. Các dòng có cùng `Agent Email` được gộp thành một Agent (so sánh email không phân biệt hoa/thường).

- Tên hiển thị (lấy từ cột AgentName nếu có).
- Địa chỉ Agent Email.
- Trạng thái gửi trong Reminding tương ứng.
- Thời điểm nhập và, khi phù hợp, thời điểm gửi.
- Thông tin lỗi nếu gửi thất bại.

Một Agent thuộc một Reminding; cùng địa chỉ email có thể xuất hiện trong nhiều Reminding khác nhau. Trạng thái gửi được quản lý riêng theo từng Reminding.

### AgentRecord

Là một dòng dữ liệu trong file Excel đầu vào thuộc về một Agent. AgentRecord không phải entity và không có trạng thái gửi riêng; các giá trị của nó chỉ dùng để dựng file Excel đầu ra của Agent. Một Agent có thể có nhiều AgentRecord.

File đầu vào hiện theo mẫu cố định, phải có `Agent Email` và các cột: `StudentId`, `Name`, `CourseName`, `Intake Date`, `Status`, `AgentName`, `Std_Mobile`, `Duedate`, `TuitionFee Amount`, `MiscFee Amount`, `Total Paid Amt`, `Total Due Amt`.

File đầu ra theo workbook mẫu đã cung cấp, gồm đúng 12 cột dữ liệu nói trên và không có cột `Agent Email`. Tên sheet và file đính kèm được sinh theo email Agent.

## 3. Giao diện và luồng sử dụng

### Menu bên trái

- Hiển thị các School dưới dạng mục điều hướng lớn, dễ phân biệt.
- Mỗi mục hiển thị rõ tên trường và địa chỉ email gửi.
- Chỉ đặt mục **Quản lý trường** ở cuối menu; không hiển thị khối trợ giúp hoặc thông tin quản trị viên trong menu.
- Trường đang chọn được làm nổi bật rõ ràng.

### Màn hình một School

- Hiển thị danh sách Reminding của trường.
- Mỗi Reminding thể hiện tên/đợt, ngày tạo và số liệu tổng email, đã gửi, chưa gửi, gửi lỗi.
- Có thao tác tạo Reminding mới.
- Chọn một Reminding để mở danh sách Agent và màn hình thao tác gửi email.

### Màn hình một Reminding

Đây là màn hình danh sách email đã được dựng ở bản demo, với dữ liệu thuộc Reminding đang chọn:

- Hiển thị School và địa chỉ người gửi áp dụng cho đợt.
- Hiển thị thông tin/template và tệp đính kèm của Reminding.
- Hiển thị danh sách Agent, địa chỉ email, số AgentRecord, trạng thái gửi và ngày nhập.
- Cho nhập danh sách từ file Excel, tìm kiếm, lọc theo trạng thái và ngày nhập.
- Cho chọn người nhận và gửi email theo lựa chọn.
- Hiển thị tiến độ/kết quả gửi và lý do lỗi để có thể xử lý tiếp.

### Quản lý School

Khu vực ít sử dụng, tách khỏi luồng gửi thông thường:

- Thêm, sửa và tạm ngưng School.
- Cấu hình địa chỉ người gửi, SMTP, banner/logo và thông tin thương hiệu.
- Nhập/cập nhật app password và kiểm tra kết nối SMTP.
- App password chỉ được dùng cho việc gửi; không hiển thị lại sau khi lưu.

## 4. Nhập Excel và tạo Reminding

1. Người dùng chọn School và bắt đầu tạo Reminding.
2. Nhập tên đợt, tiêu đề và nội dung/template email; cấu hình người gửi mặc định theo School.
3. Chọn file Excel nguồn theo mẫu cố định.
4. Hệ thống xác thực các cột, nhóm các dòng theo Agent Email, và báo các dòng có email thiếu/sai.
5. Người dùng xác nhận; hệ thống tạo Reminding, Agent và các AgentRecord tương ứng.
6. Khi gửi, hệ thống dựng một workbook từ các AgentRecord của từng Agent, rồi đính kèm workbook đó vào email của Agent.

Mỗi Reminding giữ dữ liệu riêng của đợt; việc tạo một Reminding mới không ghi đè danh sách của đợt cũ.

## 5. Trạng thái và thống kê

Mỗi Agent trong một Reminding có một trong các trạng thái:

- **Chưa gửi**: chưa có lần gửi thành công.
- **Đang gửi**: email đang được xử lý.
- **Đã gửi**: dịch vụ email xác nhận gửi thành công.
- **Gửi lỗi**: lần gửi thất bại; lưu lý do để tra cứu và có thể gửi lại.

Thống kê Reminding được tính theo Agent/email, không phải theo số AgentRecord. Gửi lỗi được tách khỏi chưa gửi để dễ nhận biết và xử lý; gửi lại không tạo thêm Agent hay Reminding.

## 6. Quy tắc cấu hình SMTP khi chạy

- Cấu hình SMTP của mỗi School được lưu ở backend và có thể cập nhật trong khi ứng dụng chạy, không yêu cầu khởi động lại.
- Mật khẩu là app password, phải được bảo vệ khi lưu và không bao giờ trả về giao diện sau khi đã lưu.
- Chỉ người có quyền phù hợp được cập nhật cấu hình SMTP.
- Có thể kiểm tra kết nối/cấu hình trước khi sử dụng.
- Một thao tác gửi dùng một bản cấu hình nhất quán được đọc tại lúc bắt đầu thao tác. Những email bắt đầu gửi sau khi cấu hình được lưu dùng cấu hình mới; email đang gửi không bị thay đổi giữa chừng.
- Nếu cấu hình SMTP hiện hành không hợp lệ hoặc tài khoản bị thu hồi, hệ thống báo lỗi rõ ràng và không đánh dấu email là đã gửi.

## 7. Yêu cầu bảo vệ dữ liệu và độ tin cậy

- Không lưu app password dạng văn bản thường; dùng cơ chế mã hóa phù hợp và giữ khóa mã hóa ngoài cơ sở dữ liệu.
- Không ghi app password vào log, nội dung lỗi, HTML hoặc phản hồi API.
- Hiển thị rõ School, địa chỉ người gửi, Reminding, số người nhận và tệp đính kèm ở bước xác nhận gửi.
- Ghi nhận kết quả gửi và thời điểm gửi để người dùng tra cứu.
- Lỗi đọc file, dữ liệu không hợp lệ và lỗi gửi phải được thông báo; không âm thầm bỏ qua hoặc giả lập gửi thành công.

## 8. Phạm vi MVP đã triển khai

- Giao diện Thymeleaf/CSS/JavaScript thuần: chọn School, xem các Reminding, danh sách Agent, số record, trạng thái và lỗi gửi.
- Lưu School, Reminding và Agent trong SQLite qua Spring Data JPA; AgentRecord lưu dưới dạng dữ liệu nhúng, không phải entity.
- Đọc file Excel đầu vào theo mẫu cố định, nhóm record theo Agent Email và xác thực các cột cần có.
- Soạn tiêu đề/nội dung theo Reminding, xem trước với Agent đầu tiên; sinh file Excel riêng theo mẫu đầu ra cho mỗi Agent.
- Có thể cập nhật tiêu đề/nội dung của Reminding sau khi tạo; thay đổi chỉ áp dụng cho email chưa gửi hoặc gửi lại. Tên biến được chuẩn hóa để xử lý khác biệt dấu, chữ hoa/thường và HTML entity.
- Cấu hình SMTP theo School, mã hóa app password AES-GCM bằng khóa ngoài database, kiểm tra kết nối và gửi email thật theo danh sách được chọn.
- Hàng đợi job lưu trong SQLite, worker song song và UI hiển thị tiến độ.
- Trạng thái gửi được cập nhật theo từng Agent; Reminding lưu snapshot tên/email người gửi và template.

MVP bind mặc định vào `127.0.0.1`, chưa có đăng nhập/phân quyền, banner/logo, quản lý người dùng hoặc thao tác xóa/khôi phục. Không public ứng dụng lên mạng. Chi tiết chạy và lưu trữ xem `README.md`.

## 9. Nội dung cần xác nhận trước khi triển khai chức năng

- File Excel hiện cố định theo mẫu Agent đã thống nhất; thay đổi tên/cấu trúc cột cần cập nhật ánh xạ.
- Các dòng cùng Agent Email được gộp thành một Agent và giữ toàn bộ AgentRecord.
- Mỗi Agent nhận workbook riêng; không còn file đính kèm dùng chung trong luồng mới.
- MVP chưa có đăng nhập/phân quyền; không public ứng dụng lên mạng.
- Quy tắc lưu trữ/xóa Reminding, danh sách email, lịch sử gửi và tệp đính kèm.
- Hạn mức/tốc độ gửi phù hợp với nhà cung cấp SMTP của từng trường.
