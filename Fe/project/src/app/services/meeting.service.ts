import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { JwtService } from './jwt.service';
import { MeetingRolesDto, MeetingRoleCommand } from '../models/DTO/MeetingRolesDto';

@Injectable({
  providedIn: 'root'
})
export class MeetingService {
  private apiUrl = 'http://localhost:8080/api';

  private paticipantApiUrl = 'http://localhost:8080/api/participants';

  constructor(private http: HttpClient, private jwt: JwtService) { }

  getNote(roomCode: string): Observable<{ content: string }> {
    return this.http.get<{ content: string }>(this.noteUrl(roomCode),
      { headers: this.jwt.generateHeader() });
  }

  saveNote(roomCode: string, content: string): Observable<{ content: string }> {
    return this.http.put<{ content: string }>(this.noteUrl(roomCode), { content },
      { headers: this.jwt.generateHeader() });
  }

  downloadMeetingText(roomCode: string, format: 'txt' | 'docx'): Observable<Blob> {
    return this.http.get(this.noteUrl(roomCode) + '/export',
      { headers: this.jwt.generateHeader(), params: { format }, responseType: 'blob' }).pipe(tap(file => {
      const expectedType = format === 'docx'
        ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        : 'text/plain';
      if (file.type.split(';')[0].trim().toLowerCase() !== expectedType) {
        throw new Error('Backend trả về sai định dạng. Hãy khởi động lại MeetingBeApplication rồi tải lại.');
      }
      const url = URL.createObjectURL(file);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'cuoc-hop-' + roomCode.replace(/[^a-zA-Z0-9_-]/g, '_') + '.' + format;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }));
  }

  private noteUrl(roomCode: string): string {
    return `${this.apiUrl}/meetings/${encodeURIComponent(roomCode)}/note`;
  }

  getRoles(roomCode: string): Observable<MeetingRolesDto> {
    return this.http.get<MeetingRolesDto>(`${this.apiUrl}/meetings/${encodeURIComponent(roomCode)}/roles`,
      { headers: this.jwt.generateHeader() });
  }

  changeRoles(roomCode: string, command: MeetingRoleCommand): Observable<MeetingRolesDto> {
    return this.http.post<MeetingRolesDto>(
      `${this.apiUrl}/meetings/${encodeURIComponent(roomCode)}/actions/${command.action}`,
      { targetUserId: command.targetUserId, version: command.version },
      { headers: this.jwt.generateHeader() });
  }

  leaveMeeting(roomCode: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/meetings/${encodeURIComponent(roomCode)}/leave`, {},
      { headers: this.jwt.generateHeader() });
  }

createRoom(hostId: number, title: string): Observable<any> {
  return this.http.post(`${this.apiUrl}/meetings`, { hostId, title },
    { headers: this.jwt.generateHeader() });
}


  getRoom(roomId: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/${roomId}`);
  }
  

  joinRoom(roomId: string, participantId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/${roomId}/join`, { userId: participantId }, {
      headers: { 'Content-Type': 'application/json' }
    });
  }
  

  getChatHistory(roomId: string): Observable<any> {
    return this.http.get(this.noteUrl(roomId) + '/chat', { headers: this.jwt.generateHeader() });
  }

  leaveRoom(userName: string, meetingCode: string): Observable<any> {
  const body = new URLSearchParams();
  body.set('userName', userName);
  body.set('meetingCode', meetingCode);

  return this.http.put(`${this.paticipantApiUrl}/leave`, body.toString(), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  });
}
}
