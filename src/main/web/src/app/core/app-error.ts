import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from './notification.service';

/** The error shape every HTTP failure is turned into, mirroring the backend's ApiError. */
export class AppError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }

  static from(error: unknown): AppError {
    if (error instanceof AppError) {
      return error;
    }
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return new AppError(0, 'Cannot reach the server. Is the backend running on port 8080?');
      }
      const body = error.error;
      const message = typeof body?.message === 'string' ? body.message : `Request failed (${error.status})`;
      return new AppError(error.status, message, body?.fieldErrors ?? {});
    }
    return new AppError(-1, error instanceof Error ? error.message : 'Unexpected error');
  }
}

/**
 * Converts HttpErrorResponse into AppError, and reports failures no screen can handle itself
 * (server unreachable, 5xx) through a global snackbar.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);
  return next(req).pipe(
    catchError((err: unknown) => {
      const appError = AppError.from(err);
      if (appError.status === 0 || appError.status >= 500) {
        notifications.error(appError.message);
      }
      return throwError(() => appError);
    }),
  );
};
