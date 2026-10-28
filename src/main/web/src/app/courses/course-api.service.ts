import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Course, CourseRequest, StudentSummary } from '../core/models';

/**
 * Thin HTTP client for the `/api/courses` endpoints. One method per endpoint, no state.
 *
 * Components don't call this directly; they go through `CourseStore`, which keeps the list in sync.
 * Failed requests reach subscribers as an `AppError` (converted by `errorInterceptor`).
 */
@Injectable({ providedIn: 'root' })
export class CourseApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/courses';

  /** `GET /api/courses`: every course, sorted by code, with enrolled counts. */
  list(): Observable<Course[]> {
    return this.http.get<Course[]>(this.baseUrl);
  }

  /** `GET /api/courses/:id`: one course. 404 if the id doesn't exist. */
  get(id: number): Observable<Course> {
    return this.http.get<Course>(`${this.baseUrl}/${id}`);
  }

  /** `GET /api/courses/:id/students`: the course roster, sorted by last name. */
  students(id: number): Observable<StudentSummary[]> {
    return this.http.get<StudentSummary[]>(`${this.baseUrl}/${id}/students`);
  }

  /** `POST /api/courses`: creates a course. 409 if the code is already taken. */
  create(request: CourseRequest): Observable<Course> {
    return this.http.post<Course>(this.baseUrl, request);
  }

  /** `PUT /api/courses/:id`: updates a course. 409 if capacity drops below the enrolled count. */
  update(id: number, request: CourseRequest): Observable<Course> {
    return this.http.put<Course>(`${this.baseUrl}/${id}`, request);
  }

  /** `DELETE /api/courses/:id`: deletes a course and drops it from every student. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
