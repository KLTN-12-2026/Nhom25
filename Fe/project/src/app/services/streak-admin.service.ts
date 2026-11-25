import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root'
})
export class StreakAdminService {
  // Nếu environment không có apiUrl, đổi thành 'http://localhost:8081/api/admin/streak'
  private baseUrl = `${(environment as any).apiUrl || 'http://localhost:8080'}/api/admin/streak`;

  constructor(private http: HttpClient) { }

  // --- Quản lý Mốc thưởng ---
  getAllRewards(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/rewards`);
  }

  createReward(data: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/rewards`, data);
  }

  updateReward(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/rewards/${id}`, data);
  }

  toggleRewardStatus(id: number): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/rewards/${id}/toggle`, {});
  }

  deleteReward(id: number): Observable<any> {
    return this.http.delete<any>(`${this.baseUrl}/rewards/${id}`);
  }

  // --- Thống kê Chuỗi học viên ---
  getAllUserStreaks(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/users`);
  }
}
