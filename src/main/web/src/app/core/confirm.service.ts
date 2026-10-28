import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

/**
 * Shows modal "Are you sure?" dialogs. Used before every delete or drop, and by `unsavedChangesGuard`
 * before leaving a form with unsaved changes.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(MatDialog);

  /**
   * Opens a modal confirmation and emits once when it closes: `true` only if the user clicked the confirm
   * button, `false` for Cancel, Escape, or clicking outside the dialog.
   *
   * Typical use: `confirm(...).pipe(filter(Boolean), switchMap(() => doTheAction()))`.
   */
  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px', autoFocus: 'dialog' })
      .afterClosed()
      .pipe(map((result) => result === true));
  }
}
