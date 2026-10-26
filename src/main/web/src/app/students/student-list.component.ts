import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { filter, switchMap, take } from 'rxjs';
import { AppError } from '../core/app-error';
import { ConfirmService } from '../core/confirm.service';
import { Student } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { ErrorBannerComponent } from '../shared/error-banner.component';
import { StudentStore } from './student.store';
import { StudentTableComponent } from './student-table.component';

@Component({
  selector: 'app-student-list',
  standalone: true,
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
    ErrorBannerComponent,
    StudentTableComponent,
  ],
  template: `
    <div class="page-header">
      <h1>Students</h1>
      <a mat-flat-button color="primary" routerLink="/students/new">
        <mat-icon>person_add</mat-icon> New student
      </a>
    </div>

    <mat-form-field appearance="outline" class="search-field">
      <mat-label>Search by name or email</mat-label>
      <mat-icon matPrefix>search</mat-icon>
      <input matInput [formControl]="search" autocomplete="off" />
      @if (search.value) {
        <button matSuffix mat-icon-button aria-label="Clear search" (click)="search.setValue('')">
          <mat-icon>close</mat-icon>
        </button>
      }
    </mat-form-field>

    @if (store.loading$ | async) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (store.error$ | async; as error) {
      <app-error-banner [message]="error" (retry)="store.refresh()" />
    }

    @if (store.students$ | async; as students) {
      @if (students.length) {
        <app-student-table [students]="students" (delete)="confirmDelete($event)" />
      } @else if (!(store.loading$ | async)) {
        <div class="empty-state">
          @if (store.search$ | async) {
            <p>No students match your search.</p>
          } @else {
            <p>No students yet. Add the first one.</p>
          }
        </div>
      }
    }
  `,
})
export class StudentListComponent implements OnInit {
  readonly store = inject(StudentStore);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly search = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    // Keep the search box in sync with the store when coming back to this tab.
    this.store.search$.pipe(take(1)).subscribe((term) => this.search.setValue(term, { emitEvent: false }));
    this.search.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((term) => this.store.setSearch(term));
    this.store.refresh();
  }

  confirmDelete(student: Student): void {
    this.confirm
      .confirm({
        title: 'Delete student?',
        message: `Delete ${student.firstName} ${student.lastName}? They will be removed from all ${student.courses.length} course(s). This cannot be undone.`,
        confirmText: 'Delete',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.store.delete(student.id)),
      )
      .subscribe({
        next: () => this.notifications.success(`Deleted ${student.firstName} ${student.lastName}`),
        error: (err) => this.notifications.apiError(AppError.from(err)),
      });
  }
}
