import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  template: `
    <div class="empty-state">
      <h1>Page not found</h1>
      <p>The page you asked for doesn't exist.</p>
      <a mat-flat-button color="primary" routerLink="/students">Go to students</a>
    </div>
  `,
})
export class NotFoundComponent {}
