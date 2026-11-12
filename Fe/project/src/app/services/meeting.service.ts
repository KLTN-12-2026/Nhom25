import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { JwtService } from './jwt.service';
import { MeetingRolesDto, MeetingRoleCommand } from '../models/DTO/MeetingRolesDto';

@Injectable({
  providedIn: 'root'
})
export class MeetingService {
  private apiUrl = 'http://localhost:8080/api';
  private chatApiUrl = 'http://localhost:8080/api/chat';
  private paticipantApiUrl = 'http://localhost:8080/api/participants';

  constructor(private http: HttpClient, private jwt: JwtService) { }

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
    return this.http.get(`${this.chatApiUrl}/${roomId}`);
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
