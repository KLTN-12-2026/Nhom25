# Điểm thưởng cuộc họp

## Mở giao diện

- Người dùng: nút ngôi sao trên thanh điều hướng → `/pages/components/rewards`.
- Admin: bánh răng → Quản lý điểm & Phần thưởng → `/pages/components/rewards-management`.
- Các đường dẫn `user-streak` và `streak-management` cũ chuyển hướng sang trang mới.

Khởi động lại backend để Hibernate (`ddl-auto=update` hiện có) tạo các bảng `reward_*` và cột `user.reward_vip_expires_at`. Frontend cần được build/chạy lại. Danh mục trống được khởi tạo với huy hiệu 100 điểm, VIP 7 ngày 300 điểm, VIP 30 ngày 1.000 điểm; admin có thể sửa hoặc tạm tắt quà.

## Quy tắc mặc định

- Thời gian tham gia cộng dồn tối thiểu 15 phút, trong phòng từng có ít nhất hai tài khoản đăng nhập khác nhau.
- Cộng 10 điểm/cuộc hợp lệ, thêm 5 điểm cho cuộc hợp lệ đầu ngày.
- Tối đa ba cuộc được tính điểm mỗi ngày theo `Asia/Ho_Chi_Minh`.
- Thưởng thêm 30 điểm khi đạt 10 cuộc và 100 điểm khi đạt 30 cuộc; mỗi mốc thưởng chỉ được cấp một lần cho mỗi tài khoản.
- Chủ phòng và người tham gia dùng cùng điều kiện. Admin không nhận điểm họp hoặc đổi quà.
- Thời gian lấy từ vòng đời WebSocket xác thực trên backend, không nhận số phút do trình duyệt gửi lên. Điểm được quyết toán khi rời/kết thúc phòng hoặc mất kết nối. Mở nhiều tab không nhân thời gian; vào lại cộng dồn thời gian và không nhận lại điểm cho cùng phiên.
- Mở trang không còn cộng điểm điểm danh. Điểm chuỗi cũ được chuyển một lần khi tạo tài khoản điểm mới, có bút toán lịch sử; không sửa/xóa dữ liệu chuỗi cũ.
- Đổi quà trừ điểm khả dụng, không trừ tổng điểm đã kiếm. VIP được gia hạn từ ngày hết hạn còn hiệu lực; VIP cũ không thời hạn vẫn được giữ. Huy hiệu lưu trong lịch sử quà đã nhận, mỗi quà huy hiệu chỉ đổi một lần.
- Admin có thể điều chỉnh điểm kèm lý do. Không cho số dư âm. Thay đổi quy tắc không tính lại lịch sử.

## Dữ liệu và quyền

`RewardAccount`: số dư, tổng điểm, số cuộc và cờ mốc đã nhận.
`RewardAttendance`: thời gian tích lũy, lần kết nối hiện tại, trạng thái và ngày tính điểm; khóa duy nhất người dùng/cuộc họp.
`RewardEntry`: lịch sử cộng/trừ điểm.
`RewardGift`, `RewardClaim`: danh mục và thông tin quà tại lúc nhận.
`RewardRules`: quy tắc do admin cấu hình.

API `/api/rewards/me` và `/api/rewards/redeem/{id}` lấy danh tính từ phiên JWT. `/api/rewards/admin/**` kiểm tra vai trò admin. Cộng điểm, đổi quà, chuyển điểm cũ và điều chỉnh sử dụng giao dịch và khóa tài khoản trên database.

## Phạm vi hiện tại

- Cuộc họp là một bản ghi `Meeting`; phòng tái sử dụng cùng ID chỉ được tính một lần/người.
- Điều kiện hai tài khoản dựa trên tài khoản đã tham gia phiên, chưa yêu cầu hai người cùng ở đủ 15 phút.
- Khi backend khởi động lại, các đoạn kết nối chưa chốt được xóa mốc bắt đầu để không tính thời gian máy chủ ngừng chạy; thời gian đã chốt trước đó vẫn giữ. Cơ chế phiên họp hiện tại dành cho một backend instance.
- Danh sách hiển thị 100 giao dịch, 100 quà đã nhận và 30 phiên gần nhất. Kiểm tra trùng huy hiệu truy vấn toàn bộ lịch sử trên database.
- Huy hiệu hiển thị trong trang quà đã nhận, chưa gắn vào avatar/hồ sơ.

## Kiểm tra

`MeetingRewardServiceTest` kiểm tra điểm, giới hạn ngày, vào lại phòng, mốc thưởng, đổi VIP, huy hiệu, chuyển điểm cũ, số dư và khởi động lại.
`MeetingRewardControllerTest` kiểm tra danh tính và quyền truy cập.
Kiểm tra thêm `MeetingRolesServiceTest`, `MeetingRolesControllerTest`, `WebSocketHandlerTest` để phát hiện ảnh hưởng đến luồng họp.

Kiểm tra giao diện bằng TypeScript, Angular compiler và `ng build --aot`. Với Node hiện đại chạy Angular 9/Webpack 4 có thể cần `node --openssl-legacy-provider node_modules/@angular/cli/bin/ng build --aot`.
