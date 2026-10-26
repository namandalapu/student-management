import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Student, StudentRequest } from '../core/models';

@Injectable({ providedIn: 'root' })
export class StudentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/students';

  list(search = ''): Observable<Student[]> {
    const params = search ? new HttpParams().set('search', search) : undefined;
    return this.http.get<Student[]>(this.baseUrl, { params });
  }

  get(id: number): Observable<Student> {
    return this.http.get<Student>(`${this.baseUrl}/${id}`);
  }

  create(request: StudentRequest): Observable<Student> {
    return this.http.post<Student>(this.baseUrl, request);
  }

  update(id: number, request: StudentRequest): Observable<Student> {
    return this.http.put<Student>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  enroll(studentId: number, courseId: number): Observable<Student> {
    return this.http.put<Student>(`${this.baseUrl}/${studentId}/courses/${courseId}`, null);
  }

  unenroll(studentId: number, courseId: number): Observable<Student> {
    return this.http.delete<Student>(`${this.baseUrl}/${studentId}/courses/${courseId}`);
  }
}
