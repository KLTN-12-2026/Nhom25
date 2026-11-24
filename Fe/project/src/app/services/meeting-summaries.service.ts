import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { JwtService } from './jwt.service';

export interface MeetingAISummary {
  id: number;
  summary: string;
  transcript: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class MeetingSummariesService {

  private url = 'http://localhost:8080/api/meeting';
  constructor(private http: HttpClient, private jwt: JwtService) { }

  getSummary(meetingId: number) {
    return this.http.get<MeetingAISummary | null>(`${this.url}/${meetingId}/summary`,
      { headers: this.jwt.generateHeader() });
  }

  summarize(meetingId: number, audio: File) {
    const form = new FormData();
    form.append('audio', audio);
    form.append('meetingId', String(meetingId));
    return this.http.post<MeetingAISummary>(`${this.url}/upload-audio`, form,
      { headers: this.jwt.generateHeader() });
  }
}
