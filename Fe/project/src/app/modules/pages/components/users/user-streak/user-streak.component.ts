import { Component, OnInit } from '@angular/core';
import { MeetingRewardsService } from 'src/app/services/meeting-rewards.service';
@Component({selector: 'app-user-streak', templateUrl: './user-streak.component.html', styleUrls: ['./user-streak.component.css']})
export class UserStreakComponent implements OnInit {
  data: any;
  error = '';
  message = '';
  busy = false;
  loading = true;
  selected: any = null;
  constructor(private rewards: MeetingRewardsService) {}
  ngOnInit() { this.load(); }
  load() {
    this.error = ''; this.loading = true;
    this.rewards.me().subscribe({next: d => {this.data = d; this.loading = false;}, error: e => {this.error = e.error?.message || 'Không tải được điểm thưởng. Vui lòng thử lại.'; this.loading = false;}});
  }
  get nextMilestone(): number {
    if (!this.data) return 0;
    const r = this.data.rules;
    return this.data.meetingCount < r.firstMilestone ? r.firstMilestone : r.secondMilestone;
  }
  get progress(): number { return this.data ? Math.min(100, this.data.meetingCount / this.nextMilestone * 100) : 0; }
  owned(gift: any): boolean { return gift.type === 'BADGE' && this.data.claims.some((c: any) => c.giftId === gift.id); }
  redeem() {
    if (!this.selected || this.busy) return;
    this.busy = true; this.error = ''; this.message = '';
    this.rewards.redeem(this.selected.id).subscribe({next: d => {
      this.data = d; this.busy = false; this.selected = null; this.message = 'Đổi thưởng thành công!'; this.rewards.changed.next();
    }, error: e => {this.busy = false; this.error = e.error?.message || 'Đổi thưởng thất bại. Vui lòng thử lại.';}});
  }
}
