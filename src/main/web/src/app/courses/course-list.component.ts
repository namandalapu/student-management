import { Component, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { filter, switchMap } from 'rxjs';
import { AppError } from '../core/app-error';
import { ConfirmService } from '../core/confirm.service';
import { Course } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { ErrorBannerComponent } from '../shared/error-banner.component';
import { CourseStore } from './course.store';

@Component({
  selector: 'app-course-list',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    MatProgressBarModule,
    ErrorBannerComponent,
  ],
  template: `
    <div class="page-header">
      <h1>Courses</h1>
      <a mat-flat-button color="primary" routerLink="/courses/new"><mat-icon>add</mat-icon> New course</a>
    </div>

    @if (store.loading$ | async) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (store.error$ | async; as error) {
      <app-error-banner [message]="error" (retry)="store.refresh()" />
    }

    @if (store.courses$ | async; as courses) {
      @if (courses.length) {
        <table mat-table [dataSource]="courses" class="mat-elevation-z1 full-width">
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
          <ng-container matColumnDef="enrollment">
            <th mat-header-cell *matHeaderCellDef>Enrolled</th>
            <td mat-cell *matCellDef="let c">
              <span [class.full]="c.enrolledCount >= c.capacity">{{ c.enrolledCount }} / {{ c.capacity }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
            <td mat-cell *matCellDef="let c" class="actions-col">
              <a mat-icon-button [routerLink]="['/courses', c.id, 'edit']" matTooltip="Edit" aria-label="Edit course">
                <mat-icon>edit</mat-icon>
              </a>
              <button mat-icon-button color="warn" (click)="confirmDelete(c)" matTooltip="Delete" aria-label="Delete course">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      } @else if (!(store.loading$ | async)) {
        <div class="empty-state"><p>No courses yet. Add the first one.</p></div>
      }
    }
  `,
})
export class CourseListComponent implements OnInit {
  readonly store = inject(CourseStore);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);

  readonly columns = ['code', 'title', 'credits', 'enrollment', 'actions'];

  ngOnInit(): void {
    this.store.refresh();
  }

  confirmDelete(course: Course): void {
    const enrolledNote = course.enrolledCount
      ? ` ${course.enrolledCount} enrolled student(s) will be dropped from it.`
      : '';
    this.confirm
      .confirm({
        title: 'Delete course?',
        message: `Delete ${course.code} · ${course.title}?${enrolledNote} This cannot be undone.`,
        confirmText: 'Delete',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.store.delete(course.id)),
      )
      .subscribe({
        next: () => this.notifications.success(`Deleted ${course.code}`),
        error: (err) => this.notifications.apiError(AppError.from(err)),
      });
  }
}
