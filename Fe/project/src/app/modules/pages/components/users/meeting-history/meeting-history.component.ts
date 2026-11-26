import { Component, OnInit } from '@angular/core';
import { MeetingService } from 'src/app/services/meeting.service';
import { ParticipantsService } from 'src/app/services/participants.service';
import { UserService } from '../../../../../services/user.service';
import { FormControl, FormGroup } from '@angular/forms';
import { MeetingRecordService } from 'src/app/services/meeting-record.service';
import { ToastrService } from 'ngx-toastr';
import * as RecordRTC from 'recordrtc';
import { Meeting } from '../../../../../models/Meeting';
import { AngularFireStorage } from '@angular/fire/storage';
import { MeetingSummariesService, MeetingAISummary } from 'src/app/services/meeting-summaries.service';

@Component({
  selector: 'app-meeting-history',
  templateUrl: './meeting-history.component.html',
  styleUrls: ['./meeting-history.component.css']
})
export class MeetingHistoryComponent implements OnInit {
  currentPage = 0;
  totalPages = 0; totalElements = 0;
  pageSize = 5;
  noRecord: any;
  userId: any
  lists: any[] = [];
  pageRange: number[] = [];
  date: string;
  name: string;
  firstTimeSearch = false;
  pages: boolean = false;
  form: FormGroup;
  formRecord: FormGroup;
  recordUpdate: any;
  selectedFile: File | null = null;
  meetId: any;
  aiResult: MeetingAISummary | null = null;
  aiLoading = false;
  aiReading = false;
  aiError = '';
  meetFile: any=null;
  private recorder: RecordRTC | null = null;



  downloadingMeetingText = false;
  showDownloadFormats = false;

  downloadMeetingText(format: 'txt' | 'docx'): void {
    const roomCode = this.recordUpdate?.meeting?.code;
    if (!roomCode || this.downloadingMeetingText) {
      return;
    }
    this.showDownloadFormats = false;
    this.downloadingMeetingText = true;
    this.meetingService.downloadMeetingText(roomCode, format).subscribe(() => {
      this.downloadingMeetingText = false;
    }, error => {
      this.downloadingMeetingText = false;
      this.toast.error(error instanceof Error ? error.message : 'Không tải được tệp ghi chú và chat.');
    });
  }

  constructor(
    private meetingService: MeetingService,
    private recordSer: MeetingRecordService,
    private service: ParticipantsService,
    private toast: ToastrService,
    private userService: UserService,
    private summaries: MeetingSummariesService,
  ) {
    this.form = new FormGroup({
      joinAt: new FormControl(''),
      leftAt: new FormControl(''),
      meeting: new FormControl(''),
    })

    this.formRecord = new FormGroup({
      recordUrl: new FormControl(''),
      meetingId: new FormControl(''),
    })


  }

  ngOnInit(): void {
    this.userService.getByUserName().subscribe(data => {
      this.userId = data;
      this.loadData(this.currentPage);
    });


  }

  getIdBlog(item) {
    this.showDownloadFormats = false;
    this.selectedFile = null;
    this.aiResult = null;
    this.aiError = '';
    this.recordUpdate = item
    this.meetId = item.meeting.id;
    const summaryMeetingId = this.meetId;
    this.aiReading = true;
    this.summaries.getSummary(summaryMeetingId).subscribe(result => {
      if (this.meetId === summaryMeetingId) {
        this.aiResult = result;
        this.aiReading = false;
      }
    }, err => {
      if (this.meetId === summaryMeetingId) {
        this.aiReading = false;
        this.aiError = err.error?.message || 'Không tải được bản tóm tắt đã lưu.';
      }
    });
    this.meetFile = null;
    this.form.patchValue({
      joinAt: this.recordUpdate.joinAt,
      leftAt: this.recordUpdate.leftAt,
      meeting: this.recordUpdate.meeting,
    })
    this.formRecord.patchValue({
      meetingId: this.recordUpdate.meeting.id,
    })
    this.recordSer.findByMeetingId(this.meetId).subscribe((data: any) => {
      if (this.meetId === summaryMeetingId && !this.selectedFile && data?.recordUrl != null) {
        this.meetFile = data.recordUrl;
      }
    }, () => {
      if (this.meetId === summaryMeetingId && !this.selectedFile) this.meetFile = null;
    })
  }


  onFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (file) {
      this.selectedFile = file;
      this.meetFile = file.name;
    }
  }

  onSubmit(): void {
    console.log("RECORD:" + this.formRecord.value.recordUrl, this.formRecord.value.meetingId);
    this.recordSer.addNewRecord(this.formRecord.value).subscribe((data: any) => {
      this.toast.success("Đã up file record thành công");
      window.location.reload();

    })


  }


  downloadFile() {
    if (this.meetFile) {
      const link = document.createElement('a');
      link.href = this.meetFile;
      link.download = ''; // Tên file sẽ được lấy từ URL hoặc bạn có thể chỉ định tên
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } else {
      console.error('Không có file để tải');
    }
  }


  summarizeByAI(): void {
    if (this.aiLoading || this.aiReading) return;
    if (!this.selectedFile) {
      this.toast.error('Hãy chọn file bản ghi để tóm tắt.');
      return;
    }
    if (this.selectedFile.size === 0 || this.selectedFile.size > 14 * 1024 * 1024) {
      this.toast.error('Chọn bản ghi có dữ liệu, tối đa 14 MB.');
      return;
    }
    const meetingId = this.meetId;
    this.aiLoading = true;
    this.aiError = '';
    this.summaries.summarize(meetingId, this.selectedFile).subscribe(result => {
      this.aiLoading = false;
      if (this.meetId === meetingId) this.aiResult = result;
      this.toast.success('Đã tạo và lưu bản tóm tắt AI.');
    }, err => {
      this.aiLoading = false;
      if (this.meetId === meetingId) {
        this.aiError = err.error?.message || 'Không xử lý được bản ghi. Vui lòng thử lại.';
        this.toast.error(this.aiError);
      }
    });
  }




  loadData(page: number): void {
    this.service.getAllByUserId(this.userId.id, page, this.pageSize).subscribe((response: any) => {
      this.lists = response.content;
      this.currentPage = response.pageable.pageNumber;
      this.totalPages = response.totalPages;
      this.pages = this.totalPages > 1;
      this.countPageCanShow();
      this.noRecord = this.lists.length === 0;
    }, error => {
      this.noRecord = error.status === 404;
      this.lists = [];
      this.pageRange = [];
    });
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.loadData(this.currentPage - 1);
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.loadData(this.currentPage + 1);
    }
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.loadData(page);
    }
  }

  countPageCanShow(): void {
    const rangeStart = Math.max(0, this.currentPage - 2);
    const rangeEnd = Math.min(this.totalPages - 1, this.currentPage + 2);
    this.pageRange = Array.from({ length: rangeEnd - rangeStart + 1 }, (_, i) => i + rangeStart);
  }
}

