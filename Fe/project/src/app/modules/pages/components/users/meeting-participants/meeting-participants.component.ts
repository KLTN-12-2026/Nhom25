import { Component, ElementRef, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { avatarColor, avatarInitial } from '../participant-avatar';
import { MeetingAction, MeetingParticipant, MeetingRoleCommand, MeetingRolesDto } from 'src/app/models/DTO/MeetingRolesDto';

@Component({
  selector: 'app-meeting-participants',
  templateUrl: './meeting-participants.component.html',
  styleUrls: ['./meeting-participants.component.css']
})
export class MeetingParticipantsComponent {
  @Input() state: MeetingRolesDto | null = null;
  @Input() myName = '';
  @Input() busy = false;
  @Input() loading = false;
  @Input() error = '';
  @Output() retry = new EventEmitter<void>();
  @Output() command = new EventEmitter<MeetingRoleCommand>();
  pending: MeetingRoleCommand | null = null;
  confirmation = '';
  openMenuId: number | null = null;
  readonly avatarColor = avatarColor;
  readonly avatarInitial = avatarInitial;

  constructor(private element: ElementRef) {}

  toggleMenu(member: MeetingParticipant): void {
    if (this.busy || !this.canManage(member)) return;
    this.openMenuId = this.openMenuId === member.userId ? null : member.userId;
  }

  @HostListener('document:click', ['$event'])
  closeOutside(event: MouseEvent): void {
    if (!this.element.nativeElement.contains(event.target)) this.closeMenu();
  }

  @HostListener('keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (this.pending) {
      this.pending = null;
      event.stopPropagation();
    } else if (this.openMenuId !== null) {
      this.closeMenu();
      event.stopPropagation();
    }
  }

  closeMenu(): void {
    this.openMenuId = null;
  }

  get isHost(): boolean {
    return !!this.state && this.state.participants.some(p => p.userName === this.myName && p.role === 'HOST');
  }

  get isCoHost(): boolean {
    return !!this.state && this.state.participants.some(p => p.userName === this.myName && p.role === 'CO_HOST');
  }

  canManage(member: MeetingParticipant): boolean {
    return member.userName !== this.myName && member.role !== 'HOST'
      && (this.isHost || (this.isCoHost && member.role === 'PARTICIPANT'));
  }

  roleName(role: string): string {
    return role === 'HOST' ? 'Host' : role === 'CO_HOST' ? 'Co-Host' : 'Người tham gia';
  }

  ask(action: MeetingAction, member?: MeetingParticipant): void {
    if (!this.state) return;
    this.closeMenu();
    const labels = {
      TRANSFER_HOST: 'Chuyển quyền Host cho', SET_CO_HOST: 'Bổ nhiệm Co-Host cho',
      REVOKE_CO_HOST: 'Thu hồi quyền Co-Host của', REMOVE: 'Xóa khỏi phòng',
      MUTE: 'Tắt micro của', REQUEST_UNMUTE: 'Gửi yêu cầu bật micro cho',
      STOP_SCREEN: 'Dừng chia sẻ màn hình của', END: 'Kết thúc cuộc họp cho tất cả mọi người'
    };
    this.confirmation = labels[action] + (member ? ' ' + member.displayName : '') + '?';
    if (action === 'TRANSFER_HOST') this.confirmation += ' Bạn sẽ trở thành người tham gia.';
    this.pending = { action, targetUserId: member ? member.userId : null, version: this.state.version };
  }

  confirm(): void {
    if (!this.pending || this.busy) return;
    const command = this.pending;
    this.pending = null;
    this.command.emit(command);
  }
}
