import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatTabsModule, MatIconModule],
  template: `
    <mat-toolbar color="primary" class="app-toolbar">
      <mat-icon>school</mat-icon>
      <span class="app-title">Student Management</span>
    </mat-toolbar>

    <!-- Each tab is a route; leaving a form with unsaved changes asks for confirmation first. -->
    <nav mat-tab-nav-bar [tabPanel]="tabPanel" class="app-tabs">
      @for (tab of tabs; track tab.path) {
        <a
          mat-tab-link
          [routerLink]="tab.path"
          routerLinkActive
          #rla="routerLinkActive"
          [active]="rla.isActive"
        >
          <mat-icon class="tab-icon">{{ tab.icon }}</mat-icon>
          {{ tab.label }}
        </a>
      }
    </nav>

    <mat-tab-nav-panel #tabPanel>
      <main class="app-content">
        <router-outlet />
      </main>
    </mat-tab-nav-panel>
  `,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  readonly tabs = [
    { path: '/students', label: 'Students', icon: 'people' },
    { path: '/courses', label: 'Courses', icon: 'menu_book' },
  ];
}
