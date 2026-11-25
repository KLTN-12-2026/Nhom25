import { Component, HostListener, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PaymentService } from 'src/app/services/payment.service';
import { MeetingRewardsService } from 'src/app/services/meeting-rewards.service';
import { Subscription } from 'rxjs';
import { UserService } from 'src/app/services/user.service';

@Component({
  selector: 'app-navigation',
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.css']
})
export class NavigationComponent implements OnInit, OnDestroy {
  currentTime: string = '';
  private intervalId: any;
  dropdownVisible = false;
  adminDropdownVisible = false;

  // --- Biến hiển thị Ngọn lửa Streak ---
  isStudentUser = false;
  rewardBalance = 0;
  private rewardSubscription?: Subscription;
  showPlusOneBadge = false;

  constructor(
    private userService: UserService,
    private paymentService: PaymentService,
    private rewardService: MeetingRewardsService,
    private toastr: ToastrService,
    private router: Router
  ) {}

  ngOnInit() {
    this.updateTime();
    this.intervalId = setInterval(() => this.updateTime(), 60000);
    this.setupNavi();
    this.loadRewardBalance();
    this.rewardSubscription = this.rewardService.changed.subscribe(() => this.loadRewardBalance());
  }

  goToStreakPage(): void {
    this.router.navigate(['/pages/components/rewards']);
  }

  loadRewardBalance() {
    this.isStudentUser = !!localStorage.getItem('Name_key') && !this.setupNavi();
    if (!this.isStudentUser) return;
    this.rewardService.me().subscribe({next: res => {
      this.rewardBalance = res.balance;
      const recent = res.history.filter((e: any) => e.points > 0 && e.meetingCode && Date.now() - new Date(e.createdAt).getTime() < 120000);
      recent.forEach((entry: any) => {
        const key = 'reward-notice-' + entry.id;
        if (!sessionStorage.getItem(key)) {
          sessionStorage.setItem(key, '1');
          this.toastr.success('+' + entry.points + ' điểm: ' + entry.description, 'Điểm thưởng cuộc họp');
        }
      });
    }, error: () => {this.rewardBalance = 0;}});
  }

  updateTime() {
    const now = new Date();
    const time = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const date = now.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' });
    this.currentTime = `${time} • ${date}`;
  }

  ngOnDestroy() {
    clearInterval(this.intervalId);
    this.rewardSubscription?.unsubscribe();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.user-menu')) {
      this.dropdownVisible = false;
    }
  }

  toggleDropdown(): void {
    this.dropdownVisible = !this.dropdownVisible;
    this.adminDropdownVisible = false;
  }

  toggleAdminDropdown(): void {
    this.adminDropdownVisible = !this.adminDropdownVisible;
    this.dropdownVisible = false;
  }

  logout() {
    this.userService.logout();
  }

  setupNavi(): boolean {
    const roleData = localStorage.getItem('Role_Key');
    if (!roleData) return false;
    const roles = JSON.parse(roleData);
    for (let role of roles) {
      if (role.authority === 'ADMIN') {
        return true;
      }
    }
    return false;
  }
}
