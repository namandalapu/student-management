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

/**
 * Single source of truth for the course list, built with RxJS.
 *
 * State lives in one `BehaviorSubject`; components read `courses$`, `loading$` and `error$` and change data
 * only through the methods here. Every write reloads the list and tells `StudentStore` through
 * `DataChangeBus`, because deleting or renaming a course changes students' course lists. It also reloads
 * when students change, since enrollments change each course's enrolled count.
 */
@Injectable({ providedIn: 'root' })
export class CourseStore {
  private readonly api = inject(CourseApiService);
  private readonly bus = inject(DataChangeBus);

  private readonly state = new BehaviorSubject<CourseState>(initialState);
  private readonly reload$ = new Subject<void>();

  /** Every course, sorted by code, with enrolled counts. */
  readonly courses$ = this.select((s) => s.courses);
  /** True while a list request is in flight. */
  readonly loading$ = this.select((s) => s.loading);
  /** Message from the last failed list request, or null. The previous list is kept on failure. */
  readonly error$ = this.select((s) => s.error);

  /** Wires up the one pipeline that loads the list, fed by refresh() and student changes. */
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

  /** Reloads the course list. switchMap drops the older request if a newer one starts. */
  refresh(): void {
    this.reload$.next();
  }

  /** Loads one course for a detail or edit page. Doesn't touch the list state. */
  getById(id: number): Observable<Course> {
    return this.api.get(id);
  }

  /** Loads the students enrolled in a course. */
  roster(id: number): Observable<StudentSummary[]> {
    return this.api.students(id);
  }

  /** Creates a course; on success reloads the list and notifies other stores. */
  create(request: CourseRequest): Observable<Course> {
    return this.api.create(request).pipe(tap(() => this.changed()));
  }

  /** Updates a course; on success reloads the list and notifies other stores. */
  update(id: number, request: CourseRequest): Observable<Course> {
    return this.api.update(id, request).pipe(tap(() => this.changed()));
  }

  /** Deletes a course; on success reloads the list and notifies `StudentStore` (students lose the course). */
  delete(id: number): Observable<void> {
    return this.api.delete(id).pipe(tap(() => this.changed()));
  }

  /** Called after every successful write. */
  private changed(): void {
    this.refresh();
    this.bus.notify('courses');
  }

  /** A stream of one slice of state that only emits when that slice actually changes. */
  private select<T>(project: (s: CourseState) => T): Observable<T> {
    return this.state.pipe(map(project), distinctUntilChanged());
  }

  /** Merges a partial update into the state and emits the new state. */
  private patch(partial: Partial<CourseState>): void {
    this.state.next({ ...this.state.value, ...partial });
  }
}
