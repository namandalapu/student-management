import { Component, Input, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { finalize } from 'rxjs';
import { AppError } from '../core/app-error';
import { StudentRequest } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { HasUnsavedChanges } from '../core/unsaved-changes.guard';
import { CourseStore } from '../courses/course.store';
import { ErrorBannerComponent } from '../shared/error-banner.component';
import { applyServerErrors, errorMessage, pastDateValidator } from '../shared/form-utils';
import { StudentStore } from './student.store';

@Component({
  selector: 'app-student-form',
  standalone: true,
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    ErrorBannerComponent,
  ],
  template: `
    <div class="page-header">
      <h1>{{ isEdit ? 'Edit student' : 'New student' }}</h1>
    </div>

    @if (loadError) {
      <app-error-banner [message]="loadError" (retry)="load()" />
    }

    <mat-card>
      @if (loading || saving) {
        <mat-progress-bar mode="indeterminate" />
      }
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()" class="form-grid" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>First name</mat-label>
            <input matInput formControlName="firstName" maxlength="50" required />
            <mat-error>{{ error('firstName', 'First name') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Last name</mat-label>
            <input matInput formControlName="lastName" maxlength="50" required />
            <mat-error>{{ error('lastName', 'Last name') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" required />
            <mat-error>{{ error('email', 'Email') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Date of birth</mat-label>
            <input matInput type="date" formControlName="dateOfBirth" [max]="today" />
            <mat-error>{{ error('dateOfBirth', 'Date of birth') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Enrolled courses</mat-label>
            <mat-select formControlName="courseIds" multiple>
              @for (course of courses.courses$ | async; track course.id) {
                <mat-option [value]="course.id" [disabled]="isFull(course.id, course.enrolledCount, course.capacity)">
                  {{ course.code }} · {{ course.title }} ({{ course.enrolledCount }}/{{ course.capacity }})
                </mat-option>
              }
            </mat-select>
            <mat-hint>Full courses can't be selected</mat-hint>
            <mat-error>{{ error('courseIds', 'Courses') }}</mat-error>
          </mat-form-field>

          <div class="form-actions span-2">
            <a mat-button [routerLink]="cancelLink">Cancel</a>
            <button mat-flat-button color="primary" type="submit" [disabled]="saving || loading">
              {{ isEdit ? 'Save changes' : 'Create student' }}
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
})
export class StudentFormComponent implements OnInit, HasUnsavedChanges {
  /** Bound from the :id route parameter (withComponentInputBinding); absent on /students/new. */
  @Input() id?: string;

  private readonly fb = inject(FormBuilder);
  private readonly store = inject(StudentStore);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  readonly courses = inject(CourseStore);

  readonly today = new Date().toISOString().slice(0, 10);
  readonly form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
    dateOfBirth: ['', [pastDateValidator]],
    courseIds: [[] as number[]],
  });

  loading = false;
  saving = false;
  loadError: string | null = null;
  private saved = false;
  private originalCourseIds = new Set<number>();

  get isEdit(): boolean {
    return !!this.id;
  }

  get cancelLink(): string[] {
    return this.isEdit ? ['/students', this.id!] : ['/students'];
  }

  ngOnInit(): void {
    this.courses.refresh();
    this.load();
  }

  load(): void {
    if (!this.isEdit) {
      return;
    }
    this.loading = true;
    this.loadError = null;
    this.store
      .getById(Number(this.id))
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (student) => {
          this.originalCourseIds = new Set(student.courses.map((c) => c.id));
          this.form.reset({
            firstName: student.firstName,
            lastName: student.lastName,
            email: student.email,
            dateOfBirth: student.dateOfBirth ?? '',
            courseIds: [...this.originalCourseIds],
          });
        },
        error: (err) => (this.loadError = AppError.from(err).message),
      });
  }

  /** A full course stays selectable if this student is already in it. */
  isFull(courseId: number, enrolled: number, capacity: number): boolean {
    return enrolled >= capacity && !this.originalCourseIds.has(courseId);
  }

  error(field: string, label: string): string {
    return errorMessage(this.form.get(field), label);
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saved;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: StudentRequest = {
      firstName: value.firstName!.trim(),
      lastName: value.lastName!.trim(),
      email: value.email!.trim(),
      dateOfBirth: value.dateOfBirth || null,
      courseIds: value.courseIds ?? [],
    };
    this.saving = true;
    const save$ = this.isEdit ? this.store.update(Number(this.id), request) : this.store.create(request);
    save$.pipe(finalize(() => (this.saving = false))).subscribe({
      next: (student) => {
        this.saved = true;
        this.notifications.success(`${this.isEdit ? 'Updated' : 'Created'} ${student.firstName} ${student.lastName}`);
        this.router.navigate(['/students', student.id]);
      },
      error: (err) => {
        const appError = AppError.from(err);
        // Field-level problems go next to the field; anything else goes to the snackbar.
        if (!applyServerErrors(this.form, appError.fieldErrors)) {
          this.notifications.apiError(appError);
        }
      },
    });
  }
}
