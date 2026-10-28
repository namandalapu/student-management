import { Injectable, inject } from '@angular/core';
import {
  BehaviorSubject,
  Observable,
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  merge,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { AppError } from '../core/app-error';
import { DataChangeBus } from '../core/data-change-bus';
import { Student, StudentRequest } from '../core/models';
import { StudentApiService } from './student-api.service';

interface StudentState {
  students: Student[];
  search: string;
  loading: boolean;
  error: string | null;
}

const initialState: StudentState = { students: [], search: '', loading: false, error: null };

/**
 * Single source of truth for the student list, built with RxJS.
 *
 * State lives in one `BehaviorSubject`; components read it through the `students$`, `loading$`, `error$` and
 * `search$` streams and change it only by calling methods here, never the API directly. Every write
 * (create, update, delete, enroll, drop) reloads the list and tells `CourseStore` through `DataChangeBus`,
 * because enrollments change course counts too.
 */
@Injectable({ providedIn: 'root' })
export class StudentStore {
  private readonly api = inject(StudentApiService);
  private readonly bus = inject(DataChangeBus);

  private readonly state = new BehaviorSubject<StudentState>(initialState);
  private readonly searchTerms = new Subject<string>();
  private readonly reload$ = new Subject<void>();

  /** The current (possibly filtered) list of students. */
  readonly students$ = this.select((s) => s.students);
  /** True while a list request is in flight. */
  readonly loading$ = this.select((s) => s.loading);
  /** Message from the last failed list request, or null. The previous list is kept on failure. */
  readonly error$ = this.select((s) => s.error);
  /** The search term the list is currently filtered by ('' for none). */
  readonly search$ = this.select((s) => s.search);

  /** Wires up the one pipeline that loads the list, fed by search typing, refresh() and course changes. */
  constructor() {
    const searchTerms$ = this.searchTerms.pipe(
      map((term) => term.trim()),
      debounceTime(300),
      distinctUntilChanged(),
      tap((search) => this.patch({ search })),
    );
    // Any change to courses (e.g. a course deleted) can change students' course lists.
    const reloads$ = merge(this.reload$, this.bus.on('courses')).pipe(map(() => this.state.value.search));

    merge(searchTerms$, reloads$)
      .pipe(
        tap(() => this.patch({ loading: true, error: null })),
        // switchMap drops stale responses when the user keeps typing.
        switchMap((search) =>
          this.api.list(search).pipe(
            map((students) => ({ students, error: null })),
            catchError((err) => of({ students: this.state.value.students, error: AppError.from(err).message })),
          ),
        ),
      )
      .subscribe(({ students, error }) => this.patch({ students, error, loading: false }));
  }

  /** Reloads the list with the current search term. */
  refresh(): void {
    this.reload$.next();
  }

  /** Filters the list. Waits 300 ms after the last keystroke and ignores repeats of the same term. */
  setSearch(term: string): void {
    this.searchTerms.next(term);
  }

  /** Loads one student for a detail or edit page. Doesn't touch the list state. */
  getById(id: number): Observable<Student> {
    return this.api.get(id);
  }

  /** Creates a student; on success reloads the list and notifies other stores. */
  create(request: StudentRequest): Observable<Student> {
    return this.api.create(request).pipe(tap(() => this.changed()));
  }

  /** Updates a student; on success reloads the list and notifies other stores. */
  update(id: number, request: StudentRequest): Observable<Student> {
    return this.api.update(id, request).pipe(tap(() => this.changed()));
  }

  /** Deletes a student; on success reloads the list and notifies other stores. */
  delete(id: number): Observable<void> {
    return this.api.delete(id).pipe(tap(() => this.changed()));
  }

  /** Enrolls a student in a course; on success reloads the list and notifies `CourseStore` (counts change). */
  enroll(studentId: number, courseId: number): Observable<Student> {
    return this.api.enroll(studentId, courseId).pipe(tap(() => this.changed()));
  }

  /** Drops a student from a course; on success reloads the list and notifies `CourseStore`. */
  unenroll(studentId: number, courseId: number): Observable<Student> {
    return this.api.unenroll(studentId, courseId).pipe(tap(() => this.changed()));
  }

  /** Called after every successful write. */
  private changed(): void {
    this.refresh();
    this.bus.notify('students');
  }

  /** A stream of one slice of state that only emits when that slice actually changes. */
  private select<T>(project: (s: StudentState) => T): Observable<T> {
    return this.state.pipe(map(project), distinctUntilChanged());
  }

  /** Merges a partial update into the state and emits the new state. */
  private patch(partial: Partial<StudentState>): void {
    this.state.next({ ...this.state.value, ...partial });
  }
}
