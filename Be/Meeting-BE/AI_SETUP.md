# AI tóm tắt cuộc họp

Backend gọi Google Gemini trực tiếp, không cần Python. Không dùng mật khẩu Gmail cho AI.

## Cấu hình một lần

1. Tạo API key tại https://aistudio.google.com/apikey.
2. IntelliJ → Run → Edit Configurations → MeetingBeApplication → Environment variables:
   thêm `GEMINI_API_KEY` với giá trị key thật. Không đưa key vào Git hoặc frontend.
3. Model mặc định: `gemini-3.5-flash`; có thể đổi bằng `GEMINI_MODEL` sang model hỗ trợ audio và generateContent trong tài khoản.
4. Khởi động lại backend. Hibernate tự bổ sung cột `transcript` nhờ `ddl-auto=update`.

## Sử dụng

Đăng nhập → Lịch sử cuộc họp → Chi tiết → chọn bản ghi → Tóm tắt bằng AI.
Bản ghi được gửi tới Google. Hỗ trợ MP3/WAV/WEBM/OGG/FLAC/M4A/MP4, tối đa 14 MB mỗi lần.
File MP4 cần có âm thanh. Không cần bấm Lưu bản ghi trước khi tóm tắt.
Kết quả lưu trong bảng `meeting_summaries`, gồm summary và transcript; mở lại chi tiết để đọc.
Chỉ chủ phòng và người đã tham gia phòng mới truy cập được API này.
AI có thể nhận dạng sai; cần kiểm tra nội dung trước khi dùng làm biên bản chính thức.

## API

- POST `/api/meeting/upload-audio`: multipart `audio`, `meetingId`, yêu cầu JWT.
- GET `/api/meeting/{meetingId}/summary`: trả bản mới nhất; 204 nếu chưa có.
- API cũ `/api/meeting/save-summary` đã được thay bằng việc lưu tự động sau xử lý thành công.

Lỗi thiếu key: 503. Key/quyền/file bị Google từ chối: 502. Quá hạn mức: 429.
Timeout: 504. Không nhận diện lời nói: 422. File quá lớn: 413.
Không ghi API key hay nội dung cuộc họp vào log. Chi phí/hạn mức phụ thuộc tài khoản Google.

Tài liệu: https://ai.google.dev/gemini-api/docs/audio và https://ai.google.dev/api/generate-content.
