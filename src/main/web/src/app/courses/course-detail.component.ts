import { Component, Input, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { BehaviorSubject, filter, finalize, forkJoin, switchMap } from 'rxjs';
import { AppError } from '../core/app-error';
import { ConfirmService } from '../core/confirm.service';
import { Course, StudentSummary } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { StudentStore } from '../students/student.store';
import { ErrorBannerComponent } from '../shared/error-banner.component';
import { CourseStore } from './course.store';

@Component({
  selector: 'app-course-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatTabsModule,
    MatTableModule,
    MatIconModule,
    MatProgressBarModule,
    ErrorBannerComponent,
  ],
  template: `
    <a mat-button routerLink="/courses" class="back-link"><mat-icon>arrow_back</mat-icon> All courses</a>

    @if (loading) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (error) {
      <app-error-banner [message]="error" [retryable]="errorStatus !== 404" (retry)="load()" />
    }

    @if (course$ | async; as course) {
      <div class="page-header">
        <h1>{{ course.code }} · {{ course.title }}</h1>
        <div class="header-actions">
          <a mat-stroked-button [routerLink]="['/courses', course.id, 'edit']"><mat-icon>edit</mat-icon> Edit</a>
          <button mat-flat-button color="warn" (click)="deleteCourse(course)">
            <mat-icon>delete</mat-icon> Delete
          </button>
        </div>
      </div>

      <mat-card>
        <mat-tab-group animationDuration="150ms">
          <mat-tab label="Details">
            <dl class="detail-list">
              <dt>Code</dt>
              <dd>{{ course.code }}</dd>
              <dt>Title</dt>
              <dd>{{ course.title }}</dd>
              <dt>Description</dt>
              <dd>{{ course.description || '—' }}</dd>
              <dt>Credits</dt>
              <dd>{{ course.credits }}</dd>
              <dt>Enrollment</dt>
              <dd [class.full]="course.enrolledCount >= course.capacity">
                {{ course.enrolledCount }} of {{ course.capacity }} seats
                @if (course.enrolledCount >= course.capacity) { (full) }
              </dd>
            </dl>
          </mat-tab>

          <mat-tab [label]="'Roster (' + course.enrolledCount + ')'">
            <div class="tab-body">
              @if (roster$ | async; as roster) {
                @if (roster.length) {
                  <table mat-table [dataSource]="roster" class="full-width">
                    <ng-container matColumnDef="name">
                      <th mat-header-cell *matHeaderCellDef>Name</th>
                      <td mat-cell *matCellDef="let s">
                        <a [routerLink]="['/students', s.id]" class="row-link">{{ s.lastName }}, {{ s.firstName }}</a>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="email">
                      <th mat-header-cell *matHeaderCellDef>Email</th>
                      <td mat-cell *matCellDef="let s">{{ s.email }}</td>
                    </ng-container>
                    <ng-container matColumnDef="actions">
                      <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
                      <td mat-cell *matCellDef="let s" class="actions-col">
                        <button mat-button color="warn" (click)="drop(course, s)">Remove</button>
                      </td>
                    </ng-container>
                    <tr mat-header-row *matHeaderRowDef="rosterColumns"></tr>
                    <tr mat-row *matRowDef="let row; columns: rosterColumns"></tr>
                  </table>
                } @else {
                  <p class="muted">No students enrolled yet.</p>
                }
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      </mat-card>
    }
  `,
})
export class CourseDetailComponent implements OnInit {
  @Input({ required: true }) id!: string;

  private readonly store = inject(CourseStore);
  private readonly studentStore = inject(StudentStore);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly course$ = new BehaviorSubject<Course | null>(null);
  readonly roster$ = new BehaviorSubject<StudentSummary[] | null>(null);
  readonly rosterColumns = ['name', 'email', 'actions'];

  loading = false;
  error: string | null = null;
  errorStatus: number | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = Number(this.id);
    this.loading = true;
    this.error = null;
    forkJoin({ course: this.store.getById(id), roster: this.store.roster(id) })
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: ({ course, roster }) => {
          this.course$.next(course);
          this.roster$.next(roster);
        },
        error: (err) => {
          const appError = AppError.from(err);
          this.error = appError.message;
          this.errorStatus = appError.status;
        },
      });
  }

  drop(course: Course, student: StudentSummary): void {
    this.confirm
      .confirm({
        title: 'Remove student?',
        message: `Remove ${student.firstName} ${student.lastName} from ${course.code}?`,
        confirmText: 'Remove',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.studentStore.unenroll(student.id, course.id)),
      )
      .subscribe({
        next: () => {
          this.notifications.success(`Removed ${student.firstName} from ${course.code}`);
          this.load();
        },
        error: (err) => this.notifications.apiError(AppError.from(err)),
      });
  }

  deleteCourse(course: Course): void {
    this.confirm
      .confirm({
        title: 'Delete course?',
        message: `Delete ${course.code}? ${course.enrolledCount} enrolled student(s) will be dropped. This cannot be undone.`,
        confirmText: 'Delete',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.store.delete(course.id)),
      )
      .subscribe({
        next: () => {
          this.notifications.success(`Deleted ${course.code}`);
          this.router.navigate(['/courses']);
        },
        error: (err) => this.notifications.apiError(AppError.from(err)),
      });
  }
}
