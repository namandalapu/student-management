import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Course, CourseRequest, StudentSummary } from '../core/models';

@Injectable({ providedIn: 'root' })
export class CourseApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/courses';

  list(): Observable<Course[]> {
    return this.http.get<Course[]>(this.baseUrl);
  }

  get(id: number): Observable<Course> {
    return this.http.get<Course>(`${this.baseUrl}/${id}`);
  }

  students(id: number): Observable<StudentSummary[]> {
    return this.http.get<StudentSummary[]>(`${this.baseUrl}/${id}/students`);
  }

  create(request: CourseRequest): Observable<Course> {
    return this.http.post<Course>(this.baseUrl, request);
  }

  update(id: number, request: CourseRequest): Observable<Course> {
    return this.http.put<Course>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
