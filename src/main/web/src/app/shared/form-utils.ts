import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Rejects dates today or later (mirrors the backend's @Past). Empty values pass. */
export const pastDateValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (!control.value) {
    return null;
  }
  const today = new Date().toISOString().slice(0, 10);
  return control.value < today ? null : { pastDate: true };
};

/** Puts backend field errors (e.g. "email already exists") onto the matching form controls. */
export function applyServerErrors(form: FormGroup, fieldErrors: Record<string, string>): boolean {
  let applied = false;
  for (const [field, message] of Object.entries(fieldErrors)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}

/** Returns the first message to show for a control, in priority order. */
export function errorMessage(control: AbstractControl | null, label: string): string {
  const errors = control?.errors;
  if (!errors) {
    return '';
  }
  if (errors['server']) return errors['server'];
  if (errors['required']) return `${label} is required`;
  if (errors['email']) return `${label} must be a valid email address`;
  if (errors['maxlength']) return `${label} must be at most ${errors['maxlength'].requiredLength} characters`;
  if (errors['min']) return `${label} must be at least ${errors['min'].min}`;
  if (errors['max']) return `${label} must be at most ${errors['max'].max}`;
  if (errors['pattern']) return `${label} has an invalid format`;
  if (errors['pastDate']) return `${label} must be in the past`;
  if (errors['belowEnrollment']) return `${label} can't be lower than the ${errors['belowEnrollment']} enrolled students`;
  return `${label} is invalid`;
}
