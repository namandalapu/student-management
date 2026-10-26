import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 3000, panelClass: 'snack-success' });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 6000, panelClass: 'snack-error' });
  }

  /** Shows an API error unless the HTTP interceptor already reported it (network failure or 5xx). */
  apiError(error: { status: number; message: string }): void {
    if (error.status !== 0 && error.status < 500) {
      this.error(error.message);
    }
  }
}
