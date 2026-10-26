import { Component, Input, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { finalize } from 'rxjs';
import { AppError } from '../core/app-error';
import { CourseRequest } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { HasUnsavedChanges } from '../core/unsaved-changes.guard';
import { ErrorBannerComponent } from '../shared/error-banner.component';
import { applyServerErrors, errorMessage } from '../shared/form-utils';
import { CourseStore } from './course.store';

@Component({
  selector: 'app-course-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    ErrorBannerComponent,
  ],
  template: `
    <div class="page-header">
      <h1>{{ isEdit ? 'Edit course' : 'New course' }}</h1>
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
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" placeholder="CS101" (input)="upperCaseCode()" required />
            <mat-hint>2–4 uppercase letters + 3 digits</mat-hint>
            <mat-error>{{ error('code', 'Code') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Title</mat-label>
            <input matInput formControlName="title" maxlength="100" required />
            <mat-error>{{ error('title', 'Title') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Description</mat-label>
            <textarea matInput formControlName="description" rows="3" maxlength="500"></textarea>
            <mat-hint align="end">{{ form.controls.description.value?.length ?? 0 }} / 500</mat-hint>
            <mat-error>{{ error('description', 'Description') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Credits</mat-label>
            <input matInput type="number" formControlName="credits" min="1" max="6" required />
            <mat-error>{{ error('credits', 'Credits') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Capacity</mat-label>
            <input matInput type="number" formControlName="capacity" [min]="minCapacity" max="500" required />
            @if (enrolledCount) {
              <mat-hint>{{ enrolledCount }} currently enrolled</mat-hint>
            }
            <mat-error>{{ error('capacity', 'Capacity') }}</mat-error>
          </mat-form-field>

          <div class="form-actions span-2">
            <a mat-button [routerLink]="cancelLink">Cancel</a>
            <button mat-flat-button color="primary" type="submit" [disabled]="saving || loading">
              {{ isEdit ? 'Save changes' : 'Create course' }}
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
})
export class CourseFormComponent implements OnInit, HasUnsavedChanges {
  @Input() id?: string;

  private readonly fb = inject(FormBuilder);
  private readonly store = inject(CourseStore);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z]{2,4}[0-9]{3}$/)]],
    title: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', [Validators.maxLength(500)]],
    credits: [3 as number | null, [Validators.required, Validators.min(1), Validators.max(6)]],
    capacity: [30 as number | null, [Validators.required, Validators.min(1), Validators.max(500)]],
  });

  loading = false;
  saving = false;
  loadError: string | null = null;
  enrolledCount = 0;
  private saved = false;

  get isEdit(): boolean {
    return !!this.id;
  }

  get minCapacity(): number {
    return Math.max(1, this.enrolledCount);
  }

  get cancelLink(): string[] {
    return this.isEdit ? ['/courses', this.id!] : ['/courses'];
  }

  ngOnInit(): void {
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
        next: (course) => {
          this.enrolledCount = course.enrolledCount;
          // Capacity can't drop below the number of students already enrolled.
          this.form.controls.capacity.setValidators([
            Validators.required,
            Validators.min(this.minCapacity),
            Validators.max(500),
          ]);
          this.form.reset({
            code: course.code,
            title: course.title,
            description: course.description ?? '',
            credits: course.credits,
            capacity: course.capacity,
          });
        },
        error: (err) => (this.loadError = AppError.from(err).message),
      });
  }

  upperCaseCode(): void {
    const control = this.form.controls.code;
    const upper = (control.value ?? '').toUpperCase();
    if (upper !== control.value) {
      control.setValue(upper);
    }
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
    const request: CourseRequest = {
      code: value.code!.trim(),
      title: value.title!.trim(),
      description: value.description?.trim() || null,
      credits: Number(value.credits),
      capacity: Number(value.capacity),
    };
    this.saving = true;
    const save$ = this.isEdit ? this.store.update(Number(this.id), request) : this.store.create(request);
    save$.pipe(finalize(() => (this.saving = false))).subscribe({
      next: (course) => {
        this.saved = true;
        this.notifications.success(`${this.isEdit ? 'Updated' : 'Created'} ${course.code}`);
        this.router.navigate(['/courses', course.id]);
      },
      error: (err) => {
        const appError = AppError.from(err);
        if (!applyServerErrors(this.form, appError.fieldErrors)) {
          this.notifications.apiError(appError);
        }
      },
    });
  }
}
