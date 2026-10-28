import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Student, StudentRequest } from '../core/models';

/**
 * Thin HTTP client for the `/api/students` endpoints. One method per endpoint, no state.
 *
 * Components don't call this directly; they go through `StudentStore`, which keeps the list in sync.
 * Failed requests reach subscribers as an `AppError` (converted by `errorInterceptor`).
 */
@Injectable({ providedIn: 'root' })
export class StudentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/students';

  /** `GET /api/students[?search=]`: all students, or those whose name or email contains `search`. */
  list(search = ''): Observable<Student[]> {
    const params = search ? new HttpParams().set('search', search) : undefined;
    return this.http.get<Student[]>(this.baseUrl, { params });
  }

  /** `GET /api/students/:id`: one student with their courses. 404 if the id doesn't exist. */
  get(id: number): Observable<Student> {
    return this.http.get<Student>(`${this.baseUrl}/${id}`);
  }

  /** `POST /api/students`: creates a student, optionally enrolled in `courseIds`. */
  create(request: StudentRequest): Observable<Student> {
    return this.http.post<Student>(this.baseUrl, request);
  }

  /** `PUT /api/students/:id`: updates a student; `courseIds` replaces their enrollments if present. */
  update(id: number, request: StudentRequest): Observable<Student> {
    return this.http.put<Student>(`${this.baseUrl}/${id}`, request);
  }

  /** `DELETE /api/students/:id`: deletes a student and their enrollments. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** `PUT /api/students/:id/courses/:courseId`: enrolls in one course. 409 if full or already enrolled. */
  enroll(studentId: number, courseId: number): Observable<Student> {
    return this.http.put<Student>(`${this.baseUrl}/${studentId}/courses/${courseId}`, null);
  }

  /** `DELETE /api/students/:id/courses/:courseId`: drops one course. Returns the updated student. */
  unenroll(studentId: number, courseId: number): Observable<Student> {
    return this.http.delete<Student>(`${this.baseUrl}/${studentId}/courses/${courseId}`);
  }
}
