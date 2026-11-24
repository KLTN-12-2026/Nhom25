import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { ScheduleDto } from 'src/app/models/DTO/ScheduleDto';
import { JwtService } from 'src/app/services/jwt.service';
import { MeetingService } from 'src/app/services/meeting.service';
import { ScheduleService } from 'src/app/services/schedule.service';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-home-main',
  templateUrl: './home-main.component.html',
  styleUrls: ['./home-main.component.css']
})
export class HomeMainComponent {
  meetingCode: string = '';
  hostId: any
  scheduleForm: FormGroup;
  creatingForm: FormGroup
  scheduleDto: ScheduleDto = null;
  constructor(
    private meetingService: MeetingService,
    private router: Router,
    private toastr: ToastrService,
    private userSer: UserService,
    private jwtService: JwtService,
    private scheduleService: ScheduleService
  ) {
    this.scheduleForm = new FormGroup({
      title: new FormControl('',[Validators.required]),
      email: new FormControl('',[Validators.required]),
      createAt: new FormControl('',[Validators.required])
    })
   }

   validation_messages = {
    title:[
      { type: 'required', message: 'Vui lòng nhập tiều đề cuộc họp.' }
    ],
    email: [
      { type: 'required', message: 'Vui lòng nhập email.' }
    ],
    createAt:[
      { type: 'required', message: 'Vui lòng chọn ngày tháng.' }
    ] 
  }

  ngOnInit() {
    this.Load();

    this.creatingForm = new FormGroup({
      title: new FormControl('', Validators.required),
    })
  }

  private handleCreateError(error: any) {
    if (error.status === 401) {
      this.toastr.error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      this.userSer.logout();
      return;
    }
    this.toastr.error(error.error?.message || error.error?.detail || error.error?.reason ||
      (error.status === 0 ? 'Không kết nối được backend.' : 'Không thể tạo phòng họp. Vui lòng thử lại.'));
  }

  createRoom() {
  if (!this.jwtService.verifyToken()) {
    this.handleCreateError({status: 401});
    return;
  }
  if (this.creatingForm.invalid || !this.creatingForm.get('title')?.value?.trim()) {
    this.creatingForm.markAllAsTouched();
    this.toastr.error('Vui lòng nhập tiêu đề cuộc họp.');
    return;
  }
  this.userSer.getByUserName().subscribe((user) => {
    this.hostId = user.id;
    const title = this.creatingForm.get('title')?.value;

    this.meetingService.createRoom(this.hostId, title).subscribe((meeting) => {
      this.router.navigate([`pages/components/meeting-room/${meeting.code}`]);
    }, (error) => {
      this.handleCreateError(error);
    });
  }, error => this.handleCreateError(error));
}


  joinMeeting() {
    try {
      const url = new URL(this.meetingCode.trim());
      window.location.href = url.href;
    } catch (err) {
      this.toastr.error('Đường link phòng không tồn tại!', 'Error');
    }
  }

  Load() {
    if (this.jwtService.verifyToken()) {
      this.router.navigateByUrl("/pages/components/home-main");
    }
  }

  submitSchedule(){
    this.scheduleDto = this.scheduleForm.value;
    this.scheduleDto.userName = localStorage.getItem("Name_key") || '';
    this.scheduleService.submit(this.scheduleDto).subscribe(
      next => {
        this.toastr.success(next.message);
        this.scheduleForm.reset();
      }
    )
  }

}
