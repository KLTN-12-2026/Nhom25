import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root'
})
export class StreakClientService {
  private baseUrl = `${(environment as any).apiUrl || 'http://localhost:8080'}/api/client/streak`;

  constructor(private http: HttpClient) { }

  dailyCheckin(userName: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/checkin/${userName}`, {});
  }
}
