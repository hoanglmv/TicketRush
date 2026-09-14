# Quy định, Điều khoản & Cẩm nang Hỏi đáp (TicketRush Policies & FAQ)

## 1. Quy định về Vé điện tử (Smart Ticket) & Check-in tại cổng
- Mỗi vé mua thành công trên TicketRush sẽ được cấp một Mã QR bảo mật duy nhất trong mục "Vé của tôi" và gửi về email đã đăng ký.
- Khi đến sự kiện, khán giả xuất trình mã QR trên ứng dụng hoặc email để nhân viên soát vé quét mã check-in.
- Mã QR chỉ có giá trị sử dụng một lần (Single-use). Hệ thống check-in sẽ ngay lập tức vô hiệu hóa mã sau khi quét thành công để tránh tình trạng làm giả hoặc quay vòng vé.
- Trường hợp màn hình điện thoại bị nứt vỡ hoặc hết pin: Khán giả có thể đến quầy Hỗ trợ Khách hàng (Help Desk) tại cổng sự kiện, xuất trình CCCD/Hộ chiếu trùng khớp với họ tên trên tài khoản TicketRush để được cấp lại vé giấy hỗ trợ.

## 2. Quy định Sang tên, Đổi trả & Chuyển nhượng vé (Ticket Transfer)
- TicketRush hỗ trợ tính năng chuyển nhượng vé trực tiếp cho người khác qua hệ thống để đảm bảo an toàn, phòng chống lừa đảo phe vé (Scalping).
- Để chuyển nhượng: Người gửi chọn vé trong mục "Vé của tôi", bấm "Chuyển nhượng", nhập email của người nhận. Người nhận phải có tài khoản TicketRush đã xác thực.
- Sau khi chuyển nhượng thành công, mã QR cũ của người gửi sẽ lập tức bị hủy và hệ thống sinh mã QR mới tinh cho tài khoản người nhận.
- Chính sách hoàn/hủy vé: Vé đã mua không thể hoàn trả tiền mặt trừ trường hợp Ban tổ chức thông báo hoãn hoặc hủy sự kiện do lý do bất khả kháng (thiên tai, dịch bệnh). Trong trường hợp đó, tiền vé sẽ được tự động hoàn về phương thức thanh toán ban đầu trong 7 - 14 ngày làm việc.

## 3. Danh mục Đồ vật Bị Cấm tại các Sự kiện (Prohibited Items)
- Cấm mang vào khu vực biểu diễn:
  - Vũ khí, vật liệu nổ, pháo sáng, hung khí nguy hiểm.
  - Các loại đồ uống có cồn, chất kích thích, ma túy, thuốc lá điện tử.
  - Bình xịt hơi cay, chai lọ bằng thủy tinh, đồ uống đóng lon kim loại.
  - Thiết bị ghi hình chuyên nghiệp (máy ảnh ống kính rời DSLR, flycam, chân máy tripod, gậy tự sướng selfie stick dài trên 30cm).
  - Băng rôn, biểu ngữ có kích thước lớn hơn khổ A3 hoặc chứa nội dung vi phạm thuần phong mỹ tục, chính trị.
  - Đèn laser, còi hơi, loa phóng thanh gây ồn.
- Đồ ăn nhẹ và nước lọc đóng chai nhựa dán nhãn sự kiện được phép mang vào tùy theo quy định riêng của từng ban tổ chức.

## 4. Cơ chế Hàng đợi ảo Flash Sale & Thời gian Giữ ghế
- Khi sự kiện "Hot" mở bán vé, hệ thống tự động kích hoạt Hàng đợi ảo (Virtual Queue) sử dụng công nghệ Redis ZSET theo nguyên tắc FIFO (Ai đến trước vào trước).
- Khán giả sẽ nhận được số thứ tự xếp hàng và ước tính thời gian chờ. Tuyệt đối không tắt trình duyệt hoặc tải lại trang liên tục để tránh bị mất vị trí trong hàng đợi.
- Khi đến lượt, hệ thống cấp Access Token và tự động chuyển khán giả vào Sơ đồ chọn ghế.
- Thời gian giữ ghế: Sau khi chọn ghế thành công, hệ thống khóa tạm thời ghế trong 10 phút. Khán giả có 10 phút để xác nhận thanh toán. Nếu quá 10 phút đơn hàng chưa hoàn tất, ghế sẽ tự động giải phóng (nhả ra) cho khán giả khác mua.
