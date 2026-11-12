import { of, throwError } from 'rxjs';
import { MeetingRoomComponent } from './meeting-room.component';

describe('MeetingRoomComponent member flow', () => {
  let component: MeetingRoomComponent;
  let service: any;
  let socket: any;
  let resolveMedia: () => void;
  let initialization: Promise<void>;
  const state = {
    meetingId: 1, roomCode: 'room', creatorId: 1, hostId: 1, coHostId: null,
    version: 0, ended: false,
    participants: [{ userId: 1, userName: 'host', displayName: 'Host', role: 'HOST' }]
  };

  beforeEach(() => {
    service = jasmine.createSpyObj('MeetingService', ['getRoles', 'getChatHistory', 'changeRoles', 'leaveMeeting']);
    service.getRoles.and.returnValue(of(state));
    service.getChatHistory.and.returnValue(of([]));
    socket = {
      send: jasmine.createSpy('send'), close: jasmine.createSpy('close'), readyState: 1,
      onopen: null, onmessage: null, onclose: null, onerror: null
    };
    spyOn(window, 'WebSocket').and.returnValue(socket);
    (window.WebSocket as any).OPEN = 1;
    component = new MeetingRoomComponent(
      { getName: () => 'host', getToken: () => 'jwt' } as any,
      { snapshot: { paramMap: { get: () => 'room' } } } as any,
      service,
      jasmine.createSpyObj('toast', ['info', 'error', 'warning', 'success']),
      { detectChanges: () => {} } as any,
      { navigate: () => {} } as any
    );
    const media = new Promise<void>(resolve => resolveMedia = resolve);
    spyOn(component, 'startWebRTC').and.returnValue(media);
    initialization = component.ngOnInit();
  });

  afterEach(async () => {
    resolveMedia();
    await initialization;
    component.ngOnDestroy();
  });

  it('joins before camera permission is resolved', () => {
    expect(window.WebSocket).toHaveBeenCalledWith('ws://localhost:8080/signaling');
    socket.onopen();
    expect(JSON.parse(socket.send.calls.first().args[0])).toEqual({
      roomId: 'room', senderId: 'host', type: 'new-participant', token: 'jwt'
    });
  });

  it('renders member state while camera permission is still pending', async () => {
    await socket.onmessage({ data: JSON.stringify({
      type: 'meeting-roles-updated', roomId: 'room', state
    }) });
    expect(component.roles.participants.length).toBe(1);
    expect(component.roleLabel('host')).toBe('Host');
    expect(component.rolesLoading).toBe(false);
  });

  it('receives chat while camera permission is still pending', async () => {
    await socket.onmessage({ data: JSON.stringify({
      type: 'chat-message', roomId: 'room', senderId: 'member', message: 'Xin chào'
    }) });
    expect(component.chatMessagesList.length).toBe(1);
    expect(component.chatMessagesList[0].message).toBe('Xin chào');
  });

  it('fetches members again when the panel is opened', () => {
    component.toggleParticipants();
    expect(service.getRoles).toHaveBeenCalledWith('room');
    expect(component.isParticipantsOpen).toBe(true);
    expect(component.roles.participants.length).toBe(1);
  });

  it('shows API errors and allows a successful retry', () => {
    service.getRoles.and.returnValue(throwError({ status: 404 }));
    component.refreshRoles();
    expect(component.rolesError).toContain('backend');
    expect(component.rolesLoading).toBe(false);
    service.getRoles.and.returnValue(of(state));
    component.refreshRoles();
    expect(component.rolesError).toBe('');
    expect(component.roles.participants.length).toBe(1);
  });

  it('does not process events from an old socket after reconnecting', async () => {
    const previousHandler = socket.onmessage;
    (component as any).ws = {};
    await previousHandler({ data: JSON.stringify({
      type: 'meeting-roles-updated', roomId: 'room', state
    }) });
    expect(component.roles).toBe(null);
    (component as any).ws = socket;
  });

  it('keeps the user in the room if leaving has no successor', () => {
    service.leaveMeeting.and.returnValue(throwError({
      status: 409, error: { message: 'Hãy chọn Co-Host.' }
    }));
    component.disconnect();
    expect(service.leaveMeeting).toHaveBeenCalledWith('room');
    expect(socket.close).not.toHaveBeenCalled();
    expect(component.roleBusy).toBe(false);
  });

  it('ignores a stale HTTP snapshot after a newer participant event', async () => {
    await socket.onmessage({ data: JSON.stringify({
      type: 'meeting-roles-updated', roomId: 'room',
      state: { ...state, version: 4, participants: [
        ...state.participants, { userId: 2, userName: 'member', displayName: 'Member', role: 'PARTICIPANT' }
      ] }
    }) });
    service.getRoles.and.returnValue(of({ ...state, version: 3 }));
    component.refreshRoles();
    expect(component.roles.participants.length).toBe(2);
    expect(component.roles.version).toBe(4);
  });

  it('processes moderation while media permission is pending and asks before unmuting', async () => {
    await socket.onmessage({ data: JSON.stringify({
      type: 'meeting-command', roomId: 'room', action: 'MUTE', actorName: 'manager'
    }) });
    expect(component.micEnabled).toBe(false);
    await socket.onmessage({ data: JSON.stringify({
      type: 'meeting-command', roomId: 'room', action: 'REQUEST_UNMUTE', actorName: 'manager'
    }) });
    expect(component.micEnabled).toBe(false);
    expect(component.unmuteRequest).toContain('manager');
    component.acceptUnmute();
    expect(component.micEnabled).toBe(true);
  });

  it('cancels a pending screen capture when the host stops sharing', async () => {
    let resolveScreen: (stream: any) => void;
    spyOn(navigator.mediaDevices, 'getDisplayMedia').and.returnValue(
      new Promise<MediaStream>(resolve => resolveScreen = resolve));
    const stop = jasmine.createSpy('stop');
    const capture = component.toggleScreenShare();
    await socket.onmessage({ data: JSON.stringify({
      type: 'meeting-command', roomId: 'room', action: 'STOP_SCREEN', actorName: 'manager'
    }) });
    resolveScreen({ getTracks: () => [{ stop }] });
    await capture;
    expect(stop).toHaveBeenCalled();
    expect(component.isScreenSharing).toBe(false);
    expect(component.screenStream).toBe(null);
  });
});
