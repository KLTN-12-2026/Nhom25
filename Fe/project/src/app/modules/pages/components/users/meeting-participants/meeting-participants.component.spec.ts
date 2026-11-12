import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { MeetingParticipantsComponent } from './meeting-participants.component';

describe('MeetingParticipantsComponent', () => {
  let fixture: ComponentFixture<MeetingParticipantsComponent>;
  let component: MeetingParticipantsComponent;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [MeetingParticipantsComponent], imports: [CommonModule]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MeetingParticipantsComponent);
    component = fixture.componentInstance;
    component.myName = 'host';
    component.state = {
      meetingId: 1, roomCode: 'room', creatorId: 1, hostId: 1, coHostId: 2,
      version: 7, ended: false,
      participants: [
        { userId: 1, userName: 'host', displayName: 'Người tạo phòng', role: 'HOST' },
        { userId: 2, userName: 'cohost', displayName: 'Người hỗ trợ', role: 'CO_HOST' },
        { userId: 3, userName: 'member', displayName: 'Thành viên', role: 'PARTICIPANT' }
      ]
    };
    fixture.detectChanges();
  });

  it('renders all members and their roles from the server snapshot', () => {
    const rows = fixture.nativeElement.querySelectorAll('li');
    expect(rows.length).toBe(3);
    expect(rows[0].textContent).toContain('Người tạo phòng');
    expect(rows[1].textContent).toContain('Co-Host');
    expect(rows[2].textContent).toContain('Thành viên');
  });

  it('confirms the selected member before emitting a transfer', () => {
    const command = jasmine.createSpy('command');
    component.command.subscribe(command);
    const row = fixture.nativeElement.querySelectorAll('li')[2];
    row.querySelector('.member-toggle').click();
    fixture.detectChanges();
    Array.from(row.querySelectorAll('button')).find((button: HTMLButtonElement) =>
      button.textContent.trim() === 'Chuyển Host')['click']();
    fixture.detectChanges();
    expect(command).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="dialog"]').textContent).toContain('Thành viên');
    fixture.nativeElement.querySelector('.confirm').click();
    expect(command).toHaveBeenCalledWith({ action: 'TRANSFER_HOST', targetUserId: 3, version: 7 });
  });

  it('restricts the cohost controls to ordinary members', () => {
    component.myName = 'cohost';
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('li');
    expect(rows[0].querySelector('.member-actions')).toBeNull();
    expect(rows[1].querySelector('.member-actions')).toBeNull();
    rows[2].querySelector('.member-toggle').click();
    fixture.detectChanges();
    expect(rows[2].textContent).toContain('Tắt micro');
    expect(rows[2].textContent).not.toContain('Chuyển Host');
    expect(fixture.nativeElement.querySelector('.end-meeting')).toBeNull();
  });

  it('shows loading errors and emits a retry without hiding existing members', () => {
    const retry = jasmine.createSpy('retry');
    component.retry.subscribe(retry);
    component.error = 'Chưa tải được danh sách';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(component.error);
    fixture.nativeElement.querySelector('[role="alert"] button').click();
    expect(retry).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(3);
  });

  it('keeps actions collapsed on hover and when clicking the member name', () => {
    const row = fixture.nativeElement.querySelectorAll('li')[2];
    expect(row.querySelector('.member-actions')).toBeNull();
    row.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(row.querySelector('.member-actions')).toBeNull();
    row.querySelector('.member-name').click();
    fixture.detectChanges();
    expect(row.querySelector('.member-actions')).toBeNull();
    row.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(row.querySelector('.member-actions')).toBeNull();
  });

  it('opens only from the three-dot button and closes on Escape or outside click', () => {
    const row = fixture.nativeElement.querySelectorAll('li')[2];
    row.querySelector('.member-toggle').click();
    row.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(row.querySelector('.member-actions')).not.toBeNull();
    fixture.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(row.querySelector('.member-actions')).toBeNull();
    row.querySelector('.member-toggle').click();
    document.body.click();
    fixture.detectChanges();
    expect(row.querySelector('.member-actions')).toBeNull();
  });
});
