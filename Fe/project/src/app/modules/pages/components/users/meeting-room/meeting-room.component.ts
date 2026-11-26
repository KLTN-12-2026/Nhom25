import { Component, OnInit, OnDestroy, ViewChild, ElementRef, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import RecordRTC from 'recordrtc';
import { JwtService } from 'src/app/services/jwt.service';
import { MeetingService } from 'src/app/services/meeting.service';
import { MeetingRolesDto, MeetingRoleCommand } from 'src/app/models/DTO/MeetingRolesDto';
import { ToastrService } from 'ngx-toastr';
import { ParticipantsService } from 'src/app/services/participants.service';
import { MeetingAiService } from 'src/app/services/meeting-ai.service';
import { Subscription } from 'rxjs';

declare global {
  interface MediaDevices {
    getDisplayMedia(constraints: MediaStreamConstraints): Promise<MediaStream>;
  }
}

@Component({
  selector: 'app-meeting-room',
  templateUrl: './meeting-room.component.html',
  styleUrls: ['./meeting-room.component.css']
})
export class MeetingRoomComponent implements OnInit, OnDestroy {
  isAiOpen = false;
  aiInput = '';
  aiLoading = false;
  aiReady = false;
  aiJoining = false;
  aiError = '';
  aiMessages: { role: string; text: string }[] = [];
  private aiRequest: Subscription;
  @ViewChild('aiMessagesContainer') aiMessagesContainer: ElementRef;

  toggleAi() {
    this.isAiOpen = !this.isAiOpen;
    if (this.isAiOpen) { this.isChatOpen = false; }
    this.scrollAi();
  }

  private scrollAi() {
    setTimeout(() => {
      if (this.aiMessagesContainer) {
        const element = this.aiMessagesContainer.nativeElement;
        element.scrollTop = element.scrollHeight;
      }
    }, 0);
  }

  sendAiQuestion() {
    const question = this.aiInput.trim();
    if (!question || question.length > 2000 || this.aiLoading || !this.aiReady) { return; }
    this.aiMessages.push({ role: 'user', text: question });
    this.aiInput = '';
    this.aiError = '';
    this.aiLoading = true;
    this.scrollAi();
    this.aiRequest = this.meetingAi.ask(this.roomId, question).subscribe({
      next: result => {
        this.aiMessages.push({ role: 'assistant', text: result.answer });
        this.aiLoading = false;
        this.scrollAi();
      },
      error: err => {
        this.aiLoading = false;
        this.aiInput = question;
        this.aiError = err.error && err.error.message
          ? err.error.message : 'Không thể kết nối trợ lý AI. Vui lòng thử lại.';
        this.scrollAi();
      }
    });
  }

  prepareAiChat() {
    if (this.aiJoining) { return; }
    this.aiJoining = true;
    this.aiError = '';
    this.participantsService.addParticipant(this.participantId, this.roomId).subscribe({
      next: () => {
        this.aiReady = true;
        this.aiJoining = false;
      },
      error: err => {
        this.aiReady = false;
        this.aiJoining = false;
        this.aiError = err.status === 401
          ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để hỏi AI.'
          : 'Chưa xác nhận được tham gia phòng. Hãy bấm Thử kết nối lại.';
      }
    });
  }
  currentTime: string = '';
  private intervalId: any;

  roomId: string = '';
  roomLink: string = '';
  videoEnabled: boolean = true;
  micEnabled: boolean = true;
  isScreenSharing: boolean = false;
  isChatOpen: boolean = false;
  hasNewMessages: boolean = false;
  isRecording: boolean = false;
  localStream: MediaStream | null = null;
  screenStream: MediaStream | null = null;
  peers: { id: string; videoEnabled: boolean; micEnabled: boolean }[] = [];
  ws: WebSocket | null = null;
  peerConnections: Map<string, RTCPeerConnection> = new Map();
  signalQueue: Map<string, Array<{ type: string; signal: any }>> = new Map();
  pendingIceCandidates: Map<string, RTCIceCandidate[]> = new Map();
  participantId: string;
  chatMessagesList: { id: number; senderId: string; message: string; timestamp: Date }[] = [];
  private chatHistoryLoaded = false;
  chatInput: string = '';
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  roles: MeetingRolesDto | null = null;
  rolesError = '';
  rolesLoading = true;
  private mediaReady: Promise<void> = Promise.resolve();
  private screenRequestVersion = 0;
  private rolesRefreshTimer: any;
  isParticipantsOpen = false;
  roleBusy = false;
  unmuteRequest = '';
  private leaving = false;
  private heartbeatTimer: any;
  private reconnectTimer: any;
  private lastHeartbeat = 0;
  errorMessage: string | null = null;
  private disconnectedPeers: Set<string> = new Set();
  private recorder: RecordRTC | null = null;
  @ViewChild('chatMessages') chatMessagesContainer!: ElementRef;

  constructor(
    private jwtService: JwtService,
    private route: ActivatedRoute,
    private meetingService: MeetingService,
    private toast: ToastrService,
    private participantsService: ParticipantsService,
    private meetingAi: MeetingAiService,
    private cdr: ChangeDetectorRef,
    private router: Router
  ) {
    this.participantId = this.jwtService.getName();
  }

  async ngOnInit() {
    this.updateTime();
    this.intervalId = setInterval(() => this.updateTime(), 60000);

    this.roomId = this.route.snapshot.paramMap.get('id') || '';
    this.roomLink = `${window.location.origin}/pages/components/meeting-room/${this.roomId}`;
    console.log('Joining room:', this.roomId, 'with participantId:', this.participantId);

    // Joining the room must not wait for the browser's camera permission dialog.
    this.mediaReady = this.startWebRTC();
    this.connectWebSocket();
    await this.mediaReady;
    if (this.leaving) {
      this.localStream?.getTracks().forEach(track => track.stop());
      return;
    }

  }

  loadChatHistory() {
    this.meetingService.getChatHistory(this.roomId).subscribe({
      next: (messages: any[]) => {
        const history = messages.map(msg => ({ ...msg, timestamp: new Date(msg.timestamp) }));
        const messagesById = new Map(history.concat(this.chatMessagesList).map(msg => [msg.id, msg]));
        this.chatMessagesList = Array.from(messagesById.values()).sort((a, b) =>
          a.timestamp.getTime() - b.timestamp.getTime() || a.id - b.id);
        this.cdr.detectChanges();
        this.scrollChatToBottom();
      },
      error: (err) => {
        this.chatHistoryLoaded = false;
        console.error('Error loading chat history:', err);
        this.errorMessage = 'Không thể tải lịch sử chat. Vui lòng thử lại.';
        this.cdr.detectChanges();
      }
    });
  }

  scrollChatToBottom() {
    setTimeout(() => {
      if (this.chatMessagesContainer) {
        const container = this.chatMessagesContainer.nativeElement;
        container.scrollTop = container.scrollHeight;
      }
    }, 0);
  }

  async startWebRTC() {
    try {
      const permissionStatus = await navigator.permissions.query({ name: 'camera' as PermissionName });
      if (this.leaving) return;
      if (permissionStatus.state === 'denied') {
        this.errorMessage = 'Quyền truy cập camera bị từ chối. Vui lòng cấp quyền trong cài đặt trình duyệt.';
        this.cdr.detectChanges();
        return;
      }

      this.localStream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: true,
      });
      this.localStream.getAudioTracks().forEach(track => track.enabled = this.micEnabled);
      this.localStream.getVideoTracks().forEach(track => track.enabled = this.videoEnabled);
      const localVideo = document.getElementById('localVideo') as HTMLVideoElement;
      localVideo.srcObject = this.localStream;
      console.log('Local stream initialized with tracks:', this.localStream.getTracks());
    } catch (err) {
      if (this.leaving) return;
      console.error('Error accessing media devices:', err);
      this.errorMessage = 'Không thể truy cập camera hoặc microphone. Vui lòng kiểm tra quyền truy cập.';
      this.cdr.detectChanges();
    }
  }

  async toggleScreenShare() {
    if (this.isScreenSharing) {
      this.stopScreenShare();
    } else {
      try {
        const requestVersion = ++this.screenRequestVersion;
        const stream = await navigator.mediaDevices.getDisplayMedia({
          video: true,
          audio: true,
        });
        if (this.leaving || requestVersion !== this.screenRequestVersion) {
          stream.getTracks().forEach(track => track.stop());
          return;
        }
        this.screenStream = stream;
        this.isScreenSharing = true;
        console.log('Screen stream initialized with tracks:', this.screenStream.getTracks());

        this.cdr.detectChanges();
        setTimeout(() => {
          const localScreen = document.getElementById('localScreen') as HTMLVideoElement;
          if (localScreen && this.screenStream) {
            localScreen.srcObject = this.screenStream;
            localScreen.play().catch((err) => console.error('Error playing local screen:', err));
            console.log('Assigned screen stream to localScreen element');
          } else {
            console.error('localScreen element or screenStream not found');
          }
        }, 100);

        const screenTrack = this.screenStream.getVideoTracks()[0];
        this.peerConnections.forEach((peerConnection, peerId) => {
          const videoSender = peerConnection.getSenders().find(sender => sender.track?.kind === 'video');
          if (videoSender && screenTrack) {
            videoSender.replaceTrack(screenTrack).then(() => {
              console.log('Replaced video track with screen track for peer:', peerId);
            }).catch(err => console.error('Error replacing track:', err));
          } else if (screenTrack) {
            peerConnection.addTrack(screenTrack, this.screenStream!);
            console.log('Added screen track to peer:', peerId);
          }
        });

        screenTrack.onended = () => {
          console.log('Screen sharing ended by user');
          this.stopScreenShare();
        };

        this.cdr.detectChanges();
      } catch (error) {
        if (this.leaving) return;
        console.error('Error accessing display media:', error);
        this.errorMessage = (error as Error).name === 'NotAllowedError'
          ? 'Quyền chia sẻ màn hình bị từ chối. Vui lòng cấp quyền.'
          : 'Không thể chia sẻ màn hình. Vui lòng thử lại.';
        this.isScreenSharing = false;
        this.screenStream = null;
        this.cdr.detectChanges();
      }
    }
  }

  stopScreenShare() {
    this.screenRequestVersion++;
    if (this.screenStream) {
      if (this.isRecording) {
        this.stopRecording();
      }
      this.screenStream.getTracks().forEach(track => track.stop());
      this.screenStream = null;
      this.isScreenSharing = false;
      console.log('Stopped screen sharing');

      const localScreen = document.getElementById('localScreen') as HTMLVideoElement;
      if (localScreen) {
        localScreen.srcObject = null;
        console.log('Cleared localScreen srcObject');
      }

      if (this.localStream) {
        const videoTrack = this.localStream.getVideoTracks()[0];
        if (videoTrack) {
          this.peerConnections.forEach((peerConnection, peerId) => {
            const videoSender = peerConnection.getSenders().find(sender => sender.track?.kind === 'video');
            if (videoSender) {
              videoSender.replaceTrack(videoTrack).then(() => {
                console.log('Restored camera video track for peer:', peerId);
              }).catch(err => console.error('Error restoring track:', err));
            }
          });
        }
      }

      this.cdr.detectChanges();
    }
  }

  startRecording() {
    if (!this.localStream && !this.screenStream) {
      this.errorMessage = 'Không có luồng video hoặc âm thanh để ghi. Vui lòng bật camera/microphone hoặc chia sẻ màn hình.';
      this.cdr.detectChanges();
      return;
    }

    try {
      let stream: MediaStream;
      let audioStream: MediaStream | null = null;

      if (this.localStream) {
        audioStream = new MediaStream(this.localStream.getAudioTracks());
      }

      if (this.isScreenSharing && this.screenStream) {
        stream = new MediaStream([
          ...this.screenStream.getVideoTracks(),
          ...(audioStream ? audioStream.getAudioTracks() : this.screenStream.getAudioTracks())
        ]);
      } else if (this.localStream) {
        stream = new MediaStream([
          ...this.localStream.getVideoTracks(),
          ...this.localStream.getAudioTracks()
        ]);
      } else {
        throw new Error('No valid stream available for recording');
      }

      this.recorder = new RecordRTC(stream, {
        type: 'video',
        mimeType: 'video/webm;codecs=vp8,opus',
        disableLogs: false,
        timeSlice: 1000,
        videoBitsPerSecond: 128000,
        audioBitsPerSecond: 128000,
      });

      this.recorder.startRecording();
      this.isRecording = true;
      console.log('Started recording with RecordRTC, stream tracks:', stream.getTracks());
      this.cdr.detectChanges();
    } catch (err) {
      console.error('Error starting recording:', err);
      this.errorMessage = 'Không thể bắt đầu ghi. Vui lòng kiểm tra quyền truy cập và thử lại.';
      this.cdr.detectChanges();
    }
  }

  stopRecording() {
    if (this.recorder && this.isRecording) {
      this.recorder.stopRecording(() => {
        const blob = this.recorder!.getBlob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `recording-${new Date().toISOString()}.webm`;
        a.click();
        URL.revokeObjectURL(url);
        this.recorder = null;
        console.log('Recording saved and downloaded');
      });
      this.isRecording = false;
      console.log('Stopped recording');
      this.cdr.detectChanges();
    }
  }

  toggleRecording() {
    if (this.isRecording) {
      this.stopRecording();
    } else {
      this.startRecording();
    }
  }

  connectWebSocket() {
    if (this.leaving) return;
    this.chatHistoryLoaded = false;
    const socket = new WebSocket('ws://localhost:8080/signaling');
    this.ws = socket;

    this.ws.onopen = () => {
      if (this.leaving || this.ws !== socket) return;
      this.reconnectAttempts = 0;
      this.lastHeartbeat = Date.now();
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = setInterval(() => {
        if (Date.now() - this.lastHeartbeat > 25000) {
          this.ws?.close();
        } else if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          this.ws.send(JSON.stringify({ type: 'heartbeat' }));
        }
      }, 10000);
      console.log('WebSocket connected for participant:', this.participantId);
      this.reconnectAttempts = 0;
      this.ws?.send(
        JSON.stringify({
          roomId: this.roomId,
          senderId: this.participantId,
          type: 'new-participant',
          token: this.jwtService.getToken(),
        })
      );
      // Recover a missed initial snapshot and expose HTTP errors in the member panel.
      clearTimeout(this.rolesRefreshTimer);
      this.rolesRefreshTimer = setTimeout(() => this.refreshRoles(), 1000);
    };

    this.ws.onmessage = async (event) => {
      if (this.leaving || this.ws !== socket) return;
      try {
        const data = event.data;
        if (typeof data !== 'string' || data.trim() === '') {
          console.warn('Received empty or invalid WebSocket message:', data);
          return;
        }

        let signal;
        try {
          signal = JSON.parse(data);
        } catch (err) {
          console.error('Error parsing WebSocket message:', data, err);
          return;
        }

        console.log('Received signal:', signal);

        if (signal.type === 'meeting-error') {
          this.handleMeetingEvent(signal);
          return;
        }

        if (signal.roomId !== this.roomId) {
          console.log('Ignoring signal for different room:', signal.roomId);
          return;
        }

        if (this.handleMeetingEvent(signal)) return;

        if (signal.type === 'chat-error') {
          this.toast.error(signal.message);
          return;
        }
        if (signal.type === 'chat-message') {
          if (!this.chatMessagesList.some(message => message.id === signal.id)) {
            this.chatMessagesList.push({
              id: signal.id,
              senderId: signal.senderId,
              message: signal.message,
              timestamp: new Date(signal.timestamp)
            });
          }
          this.hasNewMessages = !this.isChatOpen && signal.senderId !== this.participantId;
          this.cdr.detectChanges();
          this.scrollChatToBottom();
          return;
        }

        // Room state and heartbeats remain responsive while media permission is pending.
        if (['offer', 'answer', 'ice-candidate', 'new-participant'].includes(signal.type)) {
          await this.mediaReady;
          if (this.leaving || this.ws !== socket) return;
        }

        const peerId = signal.senderId;
        if (signal.type === 'new-participant') this.disconnectedPeers.delete(peerId);
        if (peerId === this.participantId) {
          console.log('Ignoring signal from self');
          return;
        }

        if (this.disconnectedPeers.has(peerId)) {
          console.log(`Ignoring signal from disconnected peer: ${peerId}, type: ${signal.type}`);
          return;
        }

        let peerConnection = this.peerConnections.get(peerId);
        if (!peerConnection && signal.type !== 'new-participant' && signal.type !== 'participant-left' && signal.type !== 'status-update' && signal.type !== 'chat-message') {
          console.log('Creating new peer connection for:', peerId);
          peerConnection = this.createPeerConnection(peerId);
          this.peerConnections.set(peerId, peerConnection);
        }

        if (signal.type === 'offer' || signal.type === 'answer') {
          if (!this.signalQueue.has(peerId)) {
            this.signalQueue.set(peerId, []);
          }
          this.signalQueue.get(peerId)!.push({ type: signal.type, signal });
          await this.processSignalQueue(peerId);
        } else if (signal.type === 'ice-candidate') {
          console.log('Received ICE candidate from:', peerId);
          if (peerConnection) {
            if (signal.candidate && signal.candidate.candidate) {
              if (peerConnection.remoteDescription) {
                await peerConnection.addIceCandidate(new RTCIceCandidate(signal.candidate));
              } else {
                if (!this.pendingIceCandidates.has(peerId)) {
                  this.pendingIceCandidates.set(peerId, []);
                }
                this.pendingIceCandidates.get(peerId)!.push(new RTCIceCandidate(signal.candidate));
                console.log('Stored ICE candidate for:', peerId);
              }
            } else {
              console.log('Received empty ICE candidate (end of candidates) for:', peerId);
            }
          } else {
            console.warn('No peer connection for ICE candidate from:', peerId);
          }
        } else if (signal.type === 'new-participant') {
          console.log('New participant joined:', peerId);
          if (!this.peers.some(peer => peer.id === peerId) && !this.disconnectedPeers.has(peerId)) {
            this.peers = [...this.peers, { id: peerId, videoEnabled: true, micEnabled: true }];
            this.cdr.detectChanges();
          }
          if (!this.peerConnections.has(peerId)) {
            peerConnection = this.createPeerConnection(peerId);
            this.peerConnections.set(peerId, peerConnection);
            const offer = await peerConnection.createOffer();
            await peerConnection.setLocalDescription(offer);
            this.ws?.send(
              JSON.stringify({
                roomId: this.roomId,
                senderId: this.participantId,
                targetParticipantId: peerId,
                type: 'offer',
                sdp: peerConnection.localDescription,
              })
            );
            console.log('Sent offer to:', peerId);
          }
        } else if (signal.type === 'participant-left') {
          console.log('Participant left:', peerId);
          this.cleanupPeer(peerId);
        } else if (signal.type === 'status-update') {
          console.log('Received status update from:', peerId, signal.status);
          this.peers = this.peers.map(peer =>
            peer.id === peerId
              ? { ...peer, videoEnabled: signal.status.videoEnabled, micEnabled: signal.status.micEnabled }
              : peer
          );
          this.cdr.detectChanges();

        }
      } catch (err) {
        console.error('Error processing WebSocket message:', err);
        this.errorMessage = 'Lỗi xử lý tin nhắn WebSocket. Vui lòng thử lại.';
        this.cdr.detectChanges();
      }
    };

    this.ws.onerror = (err) => {
      if (this.leaving || this.ws !== socket) return;
      console.error('WebSocket error:', err);
      this.errorMessage = 'Lỗi kết nối WebSocket. Vui lòng kiểm tra kết nối.';
      this.cdr.detectChanges();
    };

    this.ws.onclose = (event) => {
      if (this.ws !== socket) return;
      clearInterval(this.heartbeatTimer);
      clearTimeout(this.rolesRefreshTimer);
      if (this.leaving) return;
      this.rolesError = 'Mất kết nối phòng. Đang kết nối lại…';
      this.rolesLoading = false;
      if (event.code === 1008) {
        this.finishExit();
        return;
      }
      console.log('WebSocket closed, code:', event.code, 'reason:', event.reason);
      if (this.reconnectAttempts < this.maxReconnectAttempts) {
        console.log(`Reconnecting WebSocket, attempt ${this.reconnectAttempts + 1}`);
        this.reconnectTimer = setTimeout(() => {
          this.reconnectAttempts++;
          this.connectWebSocket();
        }, 3000);
      } else {
        console.error('Max reconnect attempts reached. Please refresh the page.');
        this.errorMessage = 'Mất kết nối với server. Vui lòng làm mới trang.';
        this.cdr.detectChanges();
      }
    };
  }

  sendChatMessage() {
    if (!this.chatInput.trim()) {
      return;
    }
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      this.errorMessage = 'Không thể gửi tin nhắn. Kết nối WebSocket không hoạt động.';
      this.cdr.detectChanges();
      return;
    }

    const message = this.chatInput.trim();
    const chatMessage = {
      roomId: this.roomId,
      senderId: this.participantId,
      type: 'chat-message',
      message: message
    };

    try {
      this.ws.send(JSON.stringify(chatMessage));

      this.chatInput = '';
      this.hasNewMessages = false;
      this.cdr.detectChanges();
      this.scrollChatToBottom();
    } catch (err) {
      console.error('Error sending chat message:', err);
      this.errorMessage = 'Lỗi khi gửi tin nhắn. Vui lòng thử lại.';
      this.cdr.detectChanges();
    }
  }

  isNoteOpen = false;
  noteLoaded = false;
  noteBusy = false;
  noteContent = '';
  savedNote = '';
  noteStatus = '';
  downloadingMeetingText = false;
  showDownloadFormats = false;

  downloadMeetingText(format: 'txt' | 'docx'): void {
    this.showDownloadFormats = false;
    this.downloadingMeetingText = true;
    this.meetingService.downloadMeetingText(this.roomId, format).subscribe(() => {
      this.downloadingMeetingText = false;
    }, error => {
      this.downloadingMeetingText = false;
      this.noteStatus = error instanceof Error ? error.message : 'Không tải được tệp. Vui lòng thử lại.';
    });
  }

  notePosition: { x: number; y: number } | null = null;
  private noteDrag: { pointerId: number; offsetX: number; offsetY: number } | null = null;

  startNoteDrag(event: PointerEvent, panel: HTMLElement): void {
    if (!event.isPrimary || event.button !== 0) {
      return;
    }
    const handle = event.currentTarget as HTMLElement;
    const bounds = panel.getBoundingClientRect();
    this.noteDrag = {
      pointerId: event.pointerId,
      offsetX: event.clientX - bounds.left,
      offsetY: event.clientY - bounds.top
    };
    handle.setPointerCapture(event.pointerId);
  }

  moveNoteDrag(event: PointerEvent, panel: HTMLElement): void {
    const drag = this.noteDrag;
    if (drag && drag.pointerId === event.pointerId) {
      this.positionNote(panel, event.clientX - drag.offsetX, event.clientY - drag.offsetY);
    }
  }

  endNoteDrag(event: PointerEvent): void {
    if (this.noteDrag?.pointerId === event.pointerId) {
      this.noteDrag = null;
    }
  }

  moveNoteWithKeyboard(event: KeyboardEvent, panel: HTMLElement): void {
    const directions = { ArrowLeft: [-16, 0], ArrowRight: [16, 0], ArrowUp: [0, -16], ArrowDown: [0, 16] };
    const movement = directions[event.key];
    if (!Array.isArray(movement)) {
      return;
    }
    event.preventDefault();
    const bounds = panel.getBoundingClientRect();
    this.positionNote(panel, bounds.left + movement[0], bounds.top + movement[1]);
  }

  keepNoteInView(panel: HTMLElement): void {
    if (this.notePosition) {
      this.positionNote(panel, this.notePosition.x, this.notePosition.y);
    }
  }

  private positionNote(panel: HTMLElement, x: number, y: number): void {
    const bounds = panel.getBoundingClientRect();
    this.notePosition = {
      x: Math.max(0, Math.min(x, document.documentElement.clientWidth - bounds.width)),
      y: Math.max(0, Math.min(y, document.documentElement.clientHeight - bounds.height))
    };
  }

  toggleNote(): void {
    this.noteDrag = null;
    this.notePosition = null;
    this.showDownloadFormats = false;
    this.isNoteOpen = !this.isNoteOpen;
    if (this.isNoteOpen && !this.noteLoaded && !this.noteBusy) {
      this.noteBusy = true;
      this.noteStatus = 'Đang tải ghi chú…';
      this.meetingService.getNote(this.roomId).subscribe(note => {
        this.noteContent = this.savedNote = note.content;
        this.noteLoaded = true;
        this.noteBusy = false;
        this.noteStatus = '';
      }, () => {
        this.noteBusy = false;
        this.noteStatus = 'Không tải được ghi chú. Đóng và mở lại để thử lại.';
      });
    }
  }

  saveNote(): void {
    if (!this.noteLoaded || this.noteBusy) {
      return;
    }
    this.noteBusy = true;
    this.noteStatus = 'Đang lưu…';
    this.meetingService.saveNote(this.roomId, this.noteContent).subscribe(note => {
      this.savedNote = note.content;
      this.noteBusy = false;
      this.noteStatus = 'Đã lưu ghi chú.';
    }, () => {
      this.noteBusy = false;
      this.noteStatus = 'Không lưu được. Bạn có thể thử lại.';
    });
  }

  toggleChat() {
    this.isChatOpen = !this.isChatOpen;
    if (this.isChatOpen) { this.isAiOpen = false; }
    if (this.isChatOpen) {
      this.hasNewMessages = false;
      this.scrollChatToBottom();
    }
    this.cdr.detectChanges();
  }

  cleanupPeer(peerId: string) {
    console.log(`Cleaning up peer: ${peerId}`);

    this.disconnectedPeers.add(peerId);

    const peerConnection = this.peerConnections.get(peerId);
    if (peerConnection) {
      peerConnection.close();
      this.peerConnections.delete(peerId);
      console.log(`Closed peer connection for peer: ${peerId}`);
    }

    this.signalQueue.delete(peerId);
    this.pendingIceCandidates.delete(peerId);

    const peerExists = this.peers.some(peer => peer.id === peerId);
    if (peerExists) {
      this.peers = this.peers.filter(peer => peer.id !== peerId);
      console.log(`Removed peer ${peerId} from peers array`);
    } else {
      console.log(`Peer ${peerId} not found in peers array`);
    }

    const videoElement = document.getElementById(`peerVideo-${peerId}`) as HTMLVideoElement;
    if (videoElement) {
      videoElement.srcObject = null;
      videoElement.remove();
      console.log(`Removed video element for peer: ${peerId}`);
    } else {
      console.log(`No video element found for peer: ${peerId}`);
    }

    this.cdr.detectChanges();
    console.log(`Completed cleanup for peer: ${peerId}`);
  }

  async processSignalQueue(peerId: string) {
    if (this.disconnectedPeers.has(peerId)) {
      console.log(`Skipping signal queue for disconnected peer: ${peerId}`);
      this.signalQueue.delete(peerId);
      return;
    }

    const queue = this.signalQueue.get(peerId);
    if (!queue || queue.length === 0) return;

    const peerConnection = this.peerConnections.get(peerId);
    if (!peerConnection) {
      console.warn('No peer connection for queue processing:', peerId);
      this.signalQueue.delete(peerId);
      return;
    }

    const { type, signal } = queue[0];

    try {
      if (type === 'offer') {
        console.log('Processing queued offer from:', peerId);
        if (!signal.sdp || !signal.sdp.type || !signal.sdp.sdp || typeof signal.sdp.sdp !== 'string' || !signal.sdp.sdp.startsWith('v=')) {
          console.warn('Invalid SDP in queued offer:', signal);
          queue.shift();
          await this.processSignalQueue(peerId);
          return;
        }
        if (peerConnection.signalingState !== 'stable') {
          console.log(`Delaying offer processing for peer ${peerId}, current signaling state: ${peerConnection.signalingState}`);
          setTimeout(() => this.processSignalQueue(peerId), 100);
          return;
        }
        await peerConnection.setRemoteDescription(new RTCSessionDescription(signal.sdp));
        if (this.pendingIceCandidates.has(peerId)) {
          const candidates = this.pendingIceCandidates.get(peerId)!;
          for (const candidate of candidates) {
            await peerConnection.addIceCandidate(candidate);
            console.log('Applied pending ICE candidate for:', peerId);
          }
          this.pendingIceCandidates.delete(peerId);
        }
        const answer = await peerConnection.createAnswer();
        await peerConnection.setLocalDescription(answer);
        this.ws?.send(
          JSON.stringify({
            roomId: this.roomId,
            senderId: this.participantId,
            targetParticipantId: peerId,
            type: 'answer',
            sdp: peerConnection.localDescription,
          })
        );
        console.log('Sent answer to:', peerId);
      } else if (type === 'answer') {
        console.log('Processing queued answer from:', peerId);
        if (!signal.sdp || !signal.sdp.type || !signal.sdp.sdp || typeof signal.sdp.sdp !== 'string' || !signal.sdp.sdp.startsWith('v=')) {
          console.warn('Invalid SDP in queued answer:', signal);
          queue.shift();
          await this.processSignalQueue(peerId);
          return;
        }
        if (peerConnection.signalingState !== 'have-local-offer') {
          console.log(`Delaying answer processing for peer ${peerId}, current signaling state: ${peerConnection.signalingState}`);
          setTimeout(() => this.processSignalQueue(peerId), 100);
          return;
        }
        await peerConnection.setRemoteDescription(new RTCSessionDescription(signal.sdp));
        if (this.pendingIceCandidates.has(peerId)) {
          const candidates = this.pendingIceCandidates.get(peerId)!;
          for (const candidate of candidates) {
            await peerConnection.addIceCandidate(candidate);
            console.log('Applied pending ICE candidate for:', peerId);
          }
          this.pendingIceCandidates.delete(peerId);
        }
      }
      queue.shift();
      await this.processSignalQueue(peerId);
    } catch (err) {
      console.error('Error processing queued signal:', err);
      queue.shift();
      await this.processSignalQueue(peerId);
    }
  }

  createPeerConnection(peerId: string): RTCPeerConnection {
    if (this.disconnectedPeers.has(peerId)) {
      console.log(`Prevented creating peer connection for disconnected peer: ${peerId}`);
      throw new Error(`Peer ${peerId} is disconnected`);
    }

    const peerConnection = new RTCPeerConnection({
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        // Thêm TURN server nếu cần
        // { urls: 'turn:your-turn-server.com', username: 'username', credential: 'credential' }
      ],
    });

    if (this.localStream) {
      this.localStream.getTracks().forEach((track) => {
        peerConnection.addTrack(track, this.localStream!);
        console.log('Added track to peer:', peerId, track);
      });
    } else {
      console.warn('No local stream available for peer:', peerId);
      this.errorMessage = 'Không có luồng video cục bộ. Vui lòng kiểm tra camera/microphone.';
      this.cdr.detectChanges();
    }

    if (this.screenStream) {
      this.screenStream.getTracks().forEach((track) => {
        peerConnection.addTrack(track, this.screenStream!);
        console.log('Added screen track to peer:', peerId, track);
      });
    }

    peerConnection.ontrack = (e) => {
      if (this.disconnectedPeers.has(peerId)) {
        console.log(`Ignoring ontrack event for disconnected peer: ${peerId}`);
        return;
      }

      console.log('Received remote stream for peer:', peerId, e.streams);
      if (e.streams && e.streams[0]) {
        const remoteStream = e.streams[0];
        let videoElement = document.getElementById(`peerVideo-${peerId}`) as HTMLVideoElement;

        if (!videoElement) {
          console.log('Creating video element for peer:', peerId);
          if (!this.peers.some(peer => peer.id === peerId)) {
            this.peers = [...this.peers, { id: peerId, videoEnabled: true, micEnabled: true }];
            this.cdr.detectChanges();
          }
          setTimeout(() => {
            videoElement = document.getElementById(`peerVideo-${peerId}`) as HTMLVideoElement;
            if (videoElement && !this.disconnectedPeers.has(peerId)) {
              if (videoElement.srcObject !== remoteStream) {
                videoElement.srcObject = remoteStream;
                videoElement.play().catch((err) => {
                  console.error('Error playing video for peer:', peerId, err);
                  if (err.name === 'NotAllowedError') {
                    this.errorMessage = 'Vui lòng tương tác với trang (nhấp chuột) để phát video.';
                    this.cdr.detectChanges();
                  }
                });
                console.log('Assigned stream to video element for peer:', peerId);
              }
            } else {
              console.error('Video element not found or peer disconnected for peer:', peerId);
            }
          }, 500);
        } else if (videoElement.srcObject !== remoteStream && !this.disconnectedPeers.has(peerId)) {
          videoElement.srcObject = remoteStream;
          videoElement.play().catch((err) => {
            console.error('Error playing video for peer:', peerId, err);
            if (err.name === 'NotAllowedError') {
              this.errorMessage = 'Vui lòng tương tác với trang (nhấp chuột) để phát video.';
              this.cdr.detectChanges();
            }
          });
          console.log('Assigned stream to existing video element for peer:', peerId);
        }
      } else {
        console.warn('No remote stream received for peer:', peerId);
      }
    };

    peerConnection.onicecandidate = (e) => {
      if (e.candidate) {
        console.log('Sending ICE candidate to:', peerId);
        this.ws?.send(
          JSON.stringify({
            roomId: this.roomId,
            senderId: this.participantId,
            targetParticipantId: peerId,
            type: 'ice-candidate',
            candidate: e.candidate,
          })
        );
      } else {
        console.log('End of ICE candidates for peer:', peerId);
      }
    };

    peerConnection.onconnectionstatechange = () => {
      console.log(`Peer ${peerId} connection state: ${peerConnection.connectionState}`);
      if (peerConnection.connectionState === 'disconnected' || peerConnection.connectionState === 'closed') {
        console.log(`Peer ${peerId} connection ${peerConnection.connectionState}, cleaning up`);
        this.cleanupPeer(peerId);
      } else if (peerConnection.connectionState === 'failed') {
        console.error('Connection failed for peer:', peerId);
        this.recreatePeerConnection(peerId);
      }
    };

    peerConnection.oniceconnectionstatechange = () => {
      console.log(`Peer ${peerId} ICE connection state: ${peerConnection.iceConnectionState}`);
      if (peerConnection.iceConnectionState === 'disconnected' || peerConnection.iceConnectionState === 'closed') {
        console.log(`Peer ${peerId} ICE connection ${peerConnection.iceConnectionState}, cleaning up`);
        this.cleanupPeer(peerId);
      } else if (peerConnection.iceConnectionState === 'failed') {
        console.error('ICE connection failed for peer:', peerId);
        this.recreatePeerConnection(peerId);
      }
    };

    return peerConnection;
  }

  async recreatePeerConnection(peerId: string) {
    if (this.disconnectedPeers.has(peerId)) {
      console.log(`Skipping recreate peer connection for disconnected peer: ${peerId}`);
      return;
    }

    console.log('Recreating peer connection for:', peerId);
    let peerConnection = this.peerConnections.get(peerId);
    if (peerConnection) {
      peerConnection.close();
      this.peerConnections.delete(peerId);
    }

    peerConnection = this.createPeerConnection(peerId);
    this.peerConnections.set(peerId, peerConnection);

    try {
      const offer = await peerConnection.createOffer();
      await peerConnection.setLocalDescription(offer);
      this.ws?.send(
        JSON.stringify({
          roomId: this.roomId,
          senderId: this.participantId,
          targetParticipantId: peerId,
          type: 'offer',
          sdp: peerConnection.localDescription,
        })
      );
      console.log('Sent new offer to:', peerId);
    } catch (err) {
      console.error('Error recreating peer connection:', err);
      this.errorMessage = 'Lỗi khi tạo lại kết nối với người tham gia. Vui lòng thử lại.';
      this.cdr.detectChanges();
    }
  }

  toggleMic() {
    this.micEnabled = !this.micEnabled;
    this.localStream?.getAudioTracks().forEach((track) => {
      track.enabled = this.micEnabled;
      console.log(`Audio track enabled: ${this.micEnabled}`);
    });
    this.sendStatusUpdate();
  }

  roleLabel(name: string): string {
    const member = this.roles && this.roles.participants.find(p => p.userName === name);
    return member && member.role === 'HOST' ? 'Host' : member && member.role === 'CO_HOST' ? 'Co-Host' : '';
  }

  refreshRoles(): void {
    if (this.leaving) return;
    this.meetingService.getRoles(this.roomId).subscribe(state => this.applyRoles(state), err => {
      if (this.leaving) return;
      this.rolesLoading = false;
      this.rolesError = err.error?.message || (err.status === 404
        ? 'API danh sách thành viên chưa sẵn sàng. Hãy kiểm tra phiên bản backend đang chạy.'
        : 'Chưa tải được danh sách thành viên. Vui lòng thử lại.');
      this.cdr.detectChanges();
    });
  }

  toggleParticipants(): void {
    this.isParticipantsOpen = !this.isParticipantsOpen;
    if (this.isParticipantsOpen) this.refreshRoles();
  }

  private applyRoles(state: MeetingRolesDto): void {
    if (this.leaving || (this.roles && state.version < this.roles.version)) return;
    const previous = this.roleLabel(this.participantId);
    this.roles = state;
    this.rolesError = '';
    this.rolesLoading = false;
    if (!state.ended && !this.aiReady && !this.aiJoining) this.prepareAiChat();
    const current = this.roleLabel(this.participantId);
    if (previous !== current && current) this.toast.info('Vai trò hiện tại của bạn là ' + current + '.');
    if (state.ended) {
      this.toast.info('Host đã kết thúc cuộc họp.');
      this.finishExit();
      return;
    }
    this.cdr.detectChanges();
  }

  changeMeetingRole(command: MeetingRoleCommand): void {
    if (this.roleBusy || this.leaving) return;
    this.roleBusy = true;
    this.meetingService.changeRoles(this.roomId, command).subscribe(state => {
      this.roleBusy = false;
      this.applyRoles(state);
      if (!state.ended) this.toast.success('Đã thực hiện thao tác.');
    }, err => {
      this.roleBusy = false;
      this.toast.error(err.error?.message || 'Không thực hiện được thao tác. Vui lòng thử lại.');
      this.refreshRoles();
    });
  }

  acceptUnmute(): void {
    this.unmuteRequest = '';
    if (!this.micEnabled) this.toggleMic();
  }

  private handleMeetingEvent(signal: any): boolean {
    if (signal.type === 'meeting-error') {
      this.toast.error(signal.message);
      this.finishExit();
      return true;
    }
    if (signal.type === 'heartbeat') {
      this.lastHeartbeat = Date.now();
      this.refreshRoles();
      return true;
    }
    if (signal.type === 'meeting-roles-updated') {
      this.applyRoles(signal.state);
      if (!this.chatHistoryLoaded) {
        this.chatHistoryLoaded = true;
        this.loadChatHistory();
      }
      return true;
    }
    if (signal.type !== 'meeting-command') return false;
    if (signal.action === 'REMOVE') {
      this.toast.warning('Bạn đã được quản lý phòng đưa ra khỏi cuộc họp.');
      this.finishExit();
    } else if (signal.action === 'MUTE') {
      if (this.micEnabled) this.toggleMic();
      this.toast.info(signal.actorName + ' đã tắt micro của bạn.');
    } else if (signal.action === 'REQUEST_UNMUTE') {
      this.unmuteRequest = signal.actorName + ' đề nghị bạn bật micro.';
    } else if (signal.action === 'STOP_SCREEN') {
      this.stopScreenShare();
      this.toast.info(signal.actorName + ' đã dừng chia sẻ màn hình của bạn.');
    }
    return true;
  }

  toggleVideo() {
    this.videoEnabled = !this.videoEnabled;
    this.localStream?.getVideoTracks().forEach((track) => {
      track.enabled = this.videoEnabled;
      console.log(`Video track enabled: ${this.videoEnabled}`);
    });
    this.sendStatusUpdate();
  }

  sendStatusUpdate() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(
        JSON.stringify({
          roomId: this.roomId,
          senderId: this.participantId,
          type: 'status-update',
          status: {
            videoEnabled: this.videoEnabled,
            micEnabled: this.micEnabled,
          },
        })
      );
      console.log('Sent status update:', { videoEnabled: this.videoEnabled, micEnabled: this.micEnabled });
    }
  }

  copyLink() {
    navigator.clipboard.writeText(this.roomLink);
    alert('Đã sao chép liên kết!');
  }

  disconnect() {
    if (this.roleBusy || this.leaving) return;
    this.roleBusy = true;
    this.meetingService.leaveMeeting(this.roomId).subscribe(() => {
      this.finishExit();
    }, err => {
      this.roleBusy = false;
      this.toast.error(err.error?.message || 'Không thể rời phòng. Hãy kiểm tra kết nối.');
      this.refreshRoles();
    });
  }

  private finishExit(): void {
    this.cleanupRoom();
    this.router.navigate(['/pages/components/home-main']);
  }

  private cleanupRoom(): void {
    this.leaving = true;
    if (this.aiRequest) { this.aiRequest.unsubscribe(); }
    clearInterval(this.intervalId);
    clearInterval(this.heartbeatTimer);
    clearTimeout(this.reconnectTimer);
    clearTimeout(this.rolesRefreshTimer);
    const socket = this.ws;
    this.ws = null;
    if (socket) {
      socket.onclose = null;
      socket.onmessage = null;
      socket.onopen = null;
      socket.close();
    }
    this.stopScreenShare();
    this.localStream?.getTracks().forEach(track => track.stop());
    this.peerConnections.forEach(pc => {
      pc.onconnectionstatechange = null;
      pc.oniceconnectionstatechange = null;
      pc.close();
    });
    this.peerConnections.clear();
    this.peers = [];
    this.signalQueue.clear();
    this.pendingIceCandidates.clear();
    if (this.isRecording) this.stopRecording();
  }

  ngOnDestroy() {
    this.cleanupRoom();
  }

  trackByPeerId(index: number, peer: { id: string }): string {
    return peer.id;
  }

  updateTime() {
    const now = new Date();
    const time = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const date = now.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' });
    this.currentTime = `${time} • ${date}`;
  }

}
