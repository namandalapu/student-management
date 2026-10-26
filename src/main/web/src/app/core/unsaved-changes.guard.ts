import { CanDeactivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { ConfirmService } from './confirm.service';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/** Asks for confirmation before leaving a form (switching tabs, navigating away) with unsaved edits. */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (!component.hasUnsavedChanges()) {
    return true;
  }
  return inject(ConfirmService).confirm({
    title: 'Discard changes?',
    message: 'You have unsaved changes on this form. Leave anyway and lose them?',
    confirmText: 'Discard',
    cancelText: 'Keep editing',
    danger: true,
  });
};
