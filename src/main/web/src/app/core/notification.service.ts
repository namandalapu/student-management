import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Shows short messages in a snackbar at the bottom of the screen. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  /** Confirms that an action worked, e.g. "Created CS101". Closes itself after 3 seconds. */
  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 3000, panelClass: 'snack-success' });
  }

  /** Reports a failure. Stays a little longer (6 seconds) so there's time to read it. */
  error(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 6000, panelClass: 'snack-error' });
  }

  /**
   * Shows an API error unless the HTTP interceptor already reported it (network failure or 5xx),
   * so the same failure never produces two snackbars.
   */
  apiError(error: { status: number; message: string }): void {
    if (error.status !== 0 && error.status < 500) {
      this.error(error.message);
    }
  }
}
