import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { environment } from 'src/environments/environment';
import { JwtService } from './jwt.service';
import { timeout } from 'rxjs/operators';
@Injectable({providedIn: 'root'})
export class MeetingRewardsService {
  private base = `${(environment as any).apiUrl || 'http://localhost:8080'}/api/rewards`;
  changed = new Subject<void>();
  constructor(private http: HttpClient, private jwt: JwtService) {}
  private get options() { return {headers: this.jwt.generateHeader()}; }
  me() { return this.http.get<any>(`${this.base}/me`, this.options).pipe(timeout(15000)); }
  redeem(id: number) { return this.http.post<any>(`${this.base}/redeem/${id}`, {}, this.options).pipe(timeout(15000)); }
  rules() { return this.http.get<any>(`${this.base}/admin/rules`, this.options).pipe(timeout(15000)); }
  saveRules(data: any) { return this.http.put<any>(`${this.base}/admin/rules`, data, this.options).pipe(timeout(15000)); }
  gifts() { return this.http.get<any[]>(`${this.base}/admin/gifts`, this.options).pipe(timeout(15000)); }
  saveGift(data: any) { return (data.id ? this.http.put<any>(`${this.base}/admin/gifts/${data.id}`, data, this.options) : this.http.post<any>(`${this.base}/admin/gifts`, data, this.options)).pipe(timeout(15000)); }
  users() { return this.http.get<any[]>(`${this.base}/admin/users`, this.options).pipe(timeout(15000)); }
  history(id: number) { return this.http.get<any[]>(`${this.base}/admin/users/${id}/history`, this.options).pipe(timeout(15000)); }
  adjust(id: number, points: number, reason: string) { return this.http.post(`${this.base}/admin/users/${id}/adjust`, {points, reason}, this.options).pipe(timeout(15000)); }
}
