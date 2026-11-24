import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { JwtService } from './jwt.service';

@Injectable({ providedIn: 'root' })
export class MeetingAiService {
  constructor(private http: HttpClient, private jwt: JwtService) {}

  ask(roomCode: string, question: string) {
    return this.http.post<{ answer: string }>(
      `http://localhost:8080/api/meeting/rooms/${encodeURIComponent(roomCode)}/ai-chat`,
      { question }, { headers: this.jwt.generateHeader() });
  }
}
