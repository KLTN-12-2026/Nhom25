import { Component, OnInit } from '@angular/core';
import { MeetingRewardsService } from 'src/app/services/meeting-rewards.service';
@Component({selector:'app-streak-management',templateUrl:'./streak-management.component.html',styleUrls:['./streak-management.component.css']})
export class StreakManagementComponent implements OnInit {
  tab = 'rules'; rules: any; gifts: any[] = []; users: any[] = []; history: any[] = [];
  gift: any = null; selectedUser: any = null; points = 0; reason = ''; error = ''; message = ''; busy = false;
  loadingRules = false;
  constructor(private service: MeetingRewardsService) {}
  ngOnInit() { this.load(); }
  fail(e: any) {
    this.busy = false;
    this.error = e.status === 401 ? 'Phiên đăng nhập không hợp lệ. Vui lòng đăng xuất và đăng nhập lại.'
      : e.status === 403 ? 'Tài khoản không có quyền quản trị điểm thưởng.'
      : e.status === 404 ? 'Backend chưa có API điểm thưởng mới. Hãy khởi động lại backend từ source đã cập nhật.'
      : e.status === 0 ? 'Không kết nối được API điểm thưởng. Kiểm tra backend tại localhost:8080.'
      : e.name === 'TimeoutError' ? 'API điểm thưởng phản hồi quá lâu. Kiểm tra backend rồi bấm Tải lại dữ liệu.'
      : e.error?.message || 'Không tải/lưu được dữ liệu điểm thưởng. Kiểm tra log backend rồi thử lại.';
  }
  load() {
    this.error=''; this.loadingRules=true;
    this.service.rules().subscribe({next:r => {this.rules=r;this.loadingRules=false;if(!r) this.error='API trả về dữ liệu trống. Kiểm tra và khởi động lại backend.';},error:e=>{this.loadingRules=false;this.fail(e);}});
    this.service.gifts().subscribe({next:r => this.gifts=r,error:e=>this.fail(e)});
    this.service.users().subscribe({next:r => this.users=r,error:e=>this.fail(e)});
  }
  saveRules() { this.busy=true; this.error=''; this.service.saveRules(this.rules).subscribe({next:r=>{this.rules=r;this.busy=false;this.message='Đã lưu quy tắc. Áp dụng cho các lần ghi nhận tiếp theo.';},error:e=>this.fail(e)}); }
  edit(g: any = null) { this.gift=g ? {...g} : {name:'',description:'',type:'VIP',cost:300,vipDays:7,active:true}; this.error=''; }
  saveGift() { this.busy=true;this.error='';this.service.saveGift(this.gift).subscribe({next:()=>{this.busy=false;this.gift=null;this.message='Đã lưu quà đổi thưởng.';this.load();},error:e=>this.fail(e)}); }
  inspect(user: any) { this.selectedUser=user;this.points=0;this.reason='';this.service.history(user.userId).subscribe({next:h=>this.history=h,error:e=>this.fail(e)}); }
  adjust() { if(!this.selectedUser || !this.points || !this.reason.trim()) return; this.busy=true;this.error='';this.service.adjust(this.selectedUser.userId,+this.points,this.reason).subscribe({next:()=>{this.busy=false;this.message='Đã điều chỉnh điểm.';this.inspect(this.selectedUser);this.load();},error:e=>this.fail(e)}); }
}
