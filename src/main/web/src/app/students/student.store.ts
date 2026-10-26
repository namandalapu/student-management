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

/** Holds the student list in a BehaviorSubject; components read slices and call methods, never the API. */
@Injectable({ providedIn: 'root' })
export class StudentStore {
  private readonly api = inject(StudentApiService);
  private readonly bus = inject(DataChangeBus);

  private readonly state = new BehaviorSubject<StudentState>(initialState);
  private readonly searchTerms = new Subject<string>();
  private readonly reload$ = new Subject<void>();

  readonly students$ = this.select((s) => s.students);
  readonly loading$ = this.select((s) => s.loading);
  readonly error$ = this.select((s) => s.error);
  readonly search$ = this.select((s) => s.search);

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

  refresh(): void {
    this.reload$.next();
  }

  setSearch(term: string): void {
    this.searchTerms.next(term);
  }

  getById(id: number): Observable<Student> {
    return this.api.get(id);
  }

  create(request: StudentRequest): Observable<Student> {
    return this.api.create(request).pipe(tap(() => this.changed()));
  }

  update(id: number, request: StudentRequest): Observable<Student> {
    return this.api.update(id, request).pipe(tap(() => this.changed()));
  }

  delete(id: number): Observable<void> {
    return this.api.delete(id).pipe(tap(() => this.changed()));
  }

  enroll(studentId: number, courseId: number): Observable<Student> {
    return this.api.enroll(studentId, courseId).pipe(tap(() => this.changed()));
  }

  unenroll(studentId: number, courseId: number): Observable<Student> {
    return this.api.unenroll(studentId, courseId).pipe(tap(() => this.changed()));
  }

  private changed(): void {
    this.refresh();
    this.bus.notify('students');
  }

  private select<T>(project: (s: StudentState) => T): Observable<T> {
    return this.state.pipe(map(project), distinctUntilChanged());
  }

  private patch(partial: Partial<StudentState>): void {
    this.state.next({ ...this.state.value, ...partial });
  }
}
