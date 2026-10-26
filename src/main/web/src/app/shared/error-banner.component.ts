import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Inline error message with an optional Retry action, used by list and detail pages. */
@Component({
  selector: 'app-error-banner',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="error-banner" role="alert">
      <mat-icon>error_outline</mat-icon>
      <span class="error-text">{{ message }}</span>
      @if (retryable) {
        <button mat-button (click)="retry.emit()">Retry</button>
      }
    </div>
  `,
})
export class ErrorBannerComponent {
  @Input({ required: true }) message!: string;
  @Input() retryable = true;
  @Output() readonly retry = new EventEmitter<void>();
}
