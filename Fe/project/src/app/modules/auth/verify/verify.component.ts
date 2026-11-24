import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-verify',
  templateUrl: './verify.component.html',
  styleUrls: ['./verify.component.css']
})
export class VerifyComponent implements OnInit {
  isLoading = false;
  otpForm: FormGroup;
  constructor(private userService: UserService, private router: Router, private toast: ToastrService) { 
    this.otpForm = new FormGroup({
      otp1: new FormControl(),
      otp2: new FormControl(),
      otp3: new FormControl(),
      otp4: new FormControl()
    })
  }

  ngOnInit(): void {
  }

  submitOtp(){
    this.isLoading = true;
    const otp = ['otp1', 'otp2', 'otp3', 'otp4'].map(key => this.otpForm.get(key)?.value || '').join('');
    const email = localStorage.getItem('email-forgot');
    if (!email || !/^\d{4}$/.test(otp)) {
      this.isLoading = false;
      this.toast.error('Vui lòng nhập đủ 4 chữ số OTP và thực hiện bước quên mật khẩu trước.');
      return;
    }
    this.userService.verifyOtp(email, otp).subscribe(
      (res) => {
        sessionStorage.setItem('password-reset-token', res.resetToken);
        this.isLoading = false;
        this.toast.success(res.message); 
        this.router.navigateByUrl("/auth/change-password");
      },
      (err) => {
        const errorMessage = err.error?.message || 'OTP không hợp lệ hoặc đã hết hạn';
        this.toast.error(errorMessage);
        this.isLoading = false;
      }
    );
  }

}
