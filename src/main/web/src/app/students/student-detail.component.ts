import { Component, Input, OnInit, inject } from '@angular/core';
import { AsyncPipe, DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { BehaviorSubject, combineLatest, filter, finalize, map, switchMap } from 'rxjs';
import { AppError } from '../core/app-error';
import { ConfirmService } from '../core/confirm.service';
import { CourseSummary, Student } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { CourseStore } from '../courses/course.store';
import { ErrorBannerComponent } from '../shared/error-banner.component';
import { StudentStore } from './student.store';

@Component({
  selector: 'app-student-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatTabsModule,
    MatTableModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressBarModule,
    ErrorBannerComponent,
  ],
  template: `
    <a mat-button routerLink="/students" class="back-link"><mat-icon>arrow_back</mat-icon> All students</a>

    @if (loading) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (error) {
      <app-error-banner [message]="error" [retryable]="errorStatus !== 404" (retry)="load()" />
    }

    @if (student$ | async; as student) {
      <div class="page-header">
        <h1>{{ student.firstName }} {{ student.lastName }}</h1>
        <div class="header-actions">
          <a mat-stroked-button [routerLink]="['/students', student.id, 'edit']"><mat-icon>edit</mat-icon> Edit</a>
          <button mat-flat-button color="warn" (click)="deleteStudent(student)">
            <mat-icon>delete</mat-icon> Delete
          </button>
        </div>
      </div>

      <mat-card>
        <mat-tab-group animationDuration="150ms" [(selectedIndex)]="selectedTab">
          <mat-tab label="Profile">
            <dl class="detail-list">
              <dt>First name</dt>
              <dd>{{ student.firstName }}</dd>
              <dt>Last name</dt>
              <dd>{{ student.lastName }}</dd>
              <dt>Email</dt>
              <dd><a [href]="'mailto:' + student.email">{{ student.email }}</a></dd>
              <dt>Date of birth</dt>
              <dd>{{ student.dateOfBirth ? (student.dateOfBirth | date: 'longDate') : '—' }}</dd>
              <dt>Total credits</dt>
              <dd>{{ totalCredits(student) }}</dd>
            </dl>
          </mat-tab>

          <mat-tab [label]="'Courses (' + student.courses.length + ')'">
            <div class="tab-body">
              <div class="enroll-row">
                <mat-form-field appearance="outline" class="enroll-select">
                  <mat-label>Enroll in a course</mat-label>
                  <mat-select [formControl]="courseToAdd">
                    @for (course of availableCourses$ | async; track course.id) {
                      <mat-option [value]="course.id" [disabled]="course.enrolledCount >= course.capacity">
                        {{ course.code }} · {{ course.title }}
                        @if (course.enrolledCount >= course.capacity) { (full) }
                      </mat-option>
                    } @empty {
                      <mat-option disabled>No other courses available</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <button mat-flat-button color="primary" [disabled]="!courseToAdd.value || busy" (click)="enroll(student)">
                  Enroll
                </button>
              </div>

              @if (student.courses.length) {
                <table mat-table [dataSource]="student.courses" class="full-width">
                  <ng-container matColumnDef="code">
                    <th mat-header-cell *matHeaderCellDef>Code</th>
                    <td mat-cell *matCellDef="let c">
                      <a [routerLink]="['/courses', c.id]" class="row-link">{{ c.code }}</a>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="title">
                    <th mat-header-cell *matHeaderCellDef>Title</th>
                    <td mat-cell *matCellDef="let c">{{ c.title }}</td>
                  </ng-container>
                  <ng-container matColumnDef="credits">
                    <th mat-header-cell *matHeaderCellDef>Credits</th>
                    <td mat-cell *matCellDef="let c">{{ c.credits }}</td>
                  </ng-container>
                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
                    <td mat-cell *matCellDef="let c" class="actions-col">
                      <button mat-button color="warn" [disabled]="busy" (click)="unenroll(student, c)">Drop</button>
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="courseColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: courseColumns"></tr>
                </table>
              } @else {
                <p class="muted">Not enrolled in any courses.</p>
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      </mat-card>
    }
  `,
})
export class StudentDetailComponent implements OnInit {
  @Input({ required: true }) id!: string;

  private readonly store = inject(StudentStore);
  private readonly courseStore = inject(CourseStore);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly student$ = new BehaviorSubject<Student | null>(null);
  /** Courses the student isn't in yet; recomputed whenever either side changes. */
  readonly availableCourses$ = combineLatest([this.courseStore.courses$, this.student$]).pipe(
    map(([courses, student]) => {
      const enrolled = new Set(student?.courses.map((c) => c.id));
      return courses.filter((c) => !enrolled.has(c.id));
    }),
  );
  readonly courseToAdd = new FormControl<number | null>(null);
  readonly courseColumns = ['code', 'title', 'credits', 'actions'];

  selectedTab = 0;
  loading = false;
  busy = false;
  error: string | null = null;
  errorStatus: number | null = null;

  ngOnInit(): void {
    this.courseStore.refresh();
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.store
      .getById(Number(this.id))
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (student) => this.student$.next(student),
        error: (err) => {
          const appError = AppError.from(err);
          this.error = appError.message;
          this.errorStatus = appError.status;
        },
      });
  }

  totalCredits(student: Student): number {
    return student.courses.reduce((sum, c) => sum + c.credits, 0);
  }

  enroll(student: Student): void {
    const courseId = this.courseToAdd.value;
    if (!courseId) {
      return;
    }
    this.busy = true;
    this.store
      .enroll(student.id, courseId)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (updated) => {
          this.student$.next(updated);
          this.courseToAdd.reset();
          const added = updated.courses.find((c) => c.id === courseId);
          this.notifications.success(`Enrolled in ${added?.code ?? 'course'}`);
        },
        error: (err) => this.notifications.apiError(AppError.from(err)),
      });
  }

  unenroll(student: Student, course: CourseSummary): void {
    this.confirm
      .confirm({
        title: 'Drop course?',
        message: `Remove ${student.firstName} from ${course.code} · ${course.title}?`,
        confirmText: 'Drop course',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.busy = true;
          return this.store.unenroll(student.id, course.id).pipe(finalize(() => (this.busy = false)));
        }),
      )
      .subscribe({
        next: (updated) => {
          this.student$.next(updated);
          this.notifications.success(`Dropped ${course.code}`);
        },
        error: (err) => this.notifications.apiError(AppError.from(err)),
      });
  }

  deleteStudent(student: Student): void {
    this.confirm
      .confirm({
        title: 'Delete student?',
        message: `Delete ${student.firstName} ${student.lastName}? This cannot be undone.`,
        confirmText: 'Delete',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.store.delete(student.id)),
      )
      .subscribe({
        next: () => {
          this.notifications.success(`Deleted ${student.firstName} ${student.lastName}`);
          this.router.navigate(['/students']);
        },
        error: (err) => this.notifications.apiError(AppError.from(err)),
      });
  }
}
