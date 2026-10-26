import { Injectable, inject } from '@angular/core';
import {
  BehaviorSubject,
  Observable,
  Subject,
  catchError,
  distinctUntilChanged,
  map,
  merge,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { AppError } from '../core/app-error';
import { DataChangeBus } from '../core/data-change-bus';
import { Course, CourseRequest, StudentSummary } from '../core/models';
import { CourseApiService } from './course-api.service';

interface CourseState {
  courses: Course[];
  loading: boolean;
  error: string | null;
}

const initialState: CourseState = { courses: [], loading: false, error: null };

@Injectable({ providedIn: 'root' })
export class CourseStore {
  private readonly api = inject(CourseApiService);
  private readonly bus = inject(DataChangeBus);

  private readonly state = new BehaviorSubject<CourseState>(initialState);
  private readonly reload$ = new Subject<void>();

  readonly courses$ = this.select((s) => s.courses);
  readonly loading$ = this.select((s) => s.loading);
  readonly error$ = this.select((s) => s.error);

  constructor() {
    // Enrollment changes on the student side change each course's enrolled count.
    merge(this.reload$, this.bus.on('students'))
      .pipe(
        tap(() => this.patch({ loading: true, error: null })),
        switchMap(() =>
          this.api.list().pipe(
            map((courses) => ({ courses, error: null })),
            catchError((err) => of({ courses: this.state.value.courses, error: AppError.from(err).message })),
          ),
        ),
      )
      .subscribe(({ courses, error }) => this.patch({ courses, error, loading: false }));
  }

  refresh(): void {
    this.reload$.next();
  }

  getById(id: number): Observable<Course> {
    return this.api.get(id);
  }

  roster(id: number): Observable<StudentSummary[]> {
    return this.api.students(id);
  }

  create(request: CourseRequest): Observable<Course> {
    return this.api.create(request).pipe(tap(() => this.changed()));
  }

  update(id: number, request: CourseRequest): Observable<Course> {
    return this.api.update(id, request).pipe(tap(() => this.changed()));
  }

  delete(id: number): Observable<void> {
    return this.api.delete(id).pipe(tap(() => this.changed()));
  }

  private changed(): void {
    this.refresh();
    this.bus.notify('courses');
  }

  private select<T>(project: (s: CourseState) => T): Observable<T> {
    return this.state.pipe(map(project), distinctUntilChanged());
  }

  private patch(partial: Partial<CourseState>): void {
    this.state.next({ ...this.state.value, ...partial });
  }
}
