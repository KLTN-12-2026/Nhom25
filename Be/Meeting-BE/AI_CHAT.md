# Trợ lý AI trong phòng họp

Admin và user đăng nhập, tham gia phòng rồi bấm biểu tượng robot ở thanh điều khiển để hỏi AI. Câu hỏi và trả lời hiển thị riêng trong trình duyệt, không phát qua WebSocket và không lưu vào cơ sở dữ liệu. Tải lại trang sẽ xóa cuộc trò chuyện. Mỗi câu hỏi được xử lý độc lập, chưa có ngữ cảnh từ câu trước.

Dùng chung `GEMINI_API_KEY` và `GEMINI_MODEL` với chức năng tóm tắt. Khởi động lại backend sau khi đặt biến môi trường. API key chỉ ở backend. Câu hỏi được gửi đến Google Gemini.

Kiến thức dự án nằm trong `src/main/resources/ai/project-knowledge.txt`. Bổ sung nội dung đã xác nhận về dự án vào file này rồi khởi động lại backend. Phạm vi ban đầu là hệ thống DTU MEET; AI chưa nghe âm thanh hay đọc chat cuộc họp. Nếu cần trả lời về một dự án khác đang được thảo luận, cần bổ sung tài liệu dự án đó.

API: `POST /api/meeting/rooms/{code}/ai-chat`, JWT, JSON `{"question":"Làm sao chia sẻ màn hình?"}`. Kết quả `{"answer":"..."}`. Yêu cầu người dùng đang có bản ghi tham gia phòng chưa rời; tối đa 2000 ký tự và một yêu cầu đang xử lý mỗi tài khoản trên mỗi backend instance. Tham gia lại phòng sẽ cập nhật thời điểm tham gia và xóa thời điểm rời cũ.

Backend gửi chỉ dẫn hệ thống và tài liệu cố định, yêu cầu kết quả JSON có `inScope` và `answer`; nếu `inScope=false`, backend trả thông báo từ chối cố định. Đây là giới hạn dựa trên mô hình, không bảo đảm tuyệt đối chống mọi cách lách chỉ dẫn. Kết quả sai định dạng hoặc chưa hoàn tất không được hiển thị.

Kiểm tra thủ công sau khi cấu hình key: đăng nhập lần lượt bằng admin/user; vào phòng; hỏi cách bật mic; hỏi dự báo thời tiết và yêu cầu bỏ qua giới hạn (phải từ chối); thử key thiếu/sai; rời rồi vào lại phòng; thử API không JWT và tài khoản chưa tham gia (401/403).

Tài liệu API Gemini: https://ai.google.dev/api/generate-content
