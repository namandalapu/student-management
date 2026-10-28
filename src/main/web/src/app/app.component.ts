import { Component, inject, viewChild } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { filter, map } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatTabsModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatSidenavModule,
  ],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav
        #sidenav
        class="sidebar"
        [mode]="isMobile() ? 'over' : 'side'"
        [opened]="!isMobile()"
        [fixedInViewport]="isMobile()"
      >
        <a class="brand" routerLink="/students">
          <mat-icon class="brand-mark">school</mat-icon>
          <span class="brand-name">Student Management</span>
        </a>

        <mat-nav-list class="side-nav" aria-label="Main">
          @for (tab of tabs; track tab.path) {
            <a
              mat-list-item
              [routerLink]="tab.path"
              routerLinkActive
              #rla="routerLinkActive"
              [activated]="rla.isActive"
            >
              <mat-icon matListItemIcon>{{ tab.icon }}</mat-icon>
              <span matListItemTitle>{{ tab.label }}</span>
            </a>
          }
        </mat-nav-list>

        <div class="side-actions">
          <a mat-stroked-button class="side-action" routerLink="/students/new">
            <mat-icon>person_add</mat-icon> New student
          </a>
          <a mat-stroked-button class="side-action" routerLink="/courses/new">
            <mat-icon>add</mat-icon> New course
          </a>
        </div>
      </mat-sidenav>

      <mat-sidenav-content class="main">
        @if (isMobile()) {
          <mat-toolbar class="mobile-bar">
            <button mat-icon-button (click)="sidenav.toggle()" aria-label="Open menu">
              <mat-icon>menu</mat-icon>
            </button>
            <span class="brand-name">Student Management</span>
          </mat-toolbar>
        }

        <!-- Each tab is a route; leaving a form with unsaved changes asks for confirmation first. -->
        <nav mat-tab-nav-bar [tabPanel]="tabPanel" class="app-tabs" mat-stretch-tabs="false" mat-align-tabs="start">
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
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  readonly tabs = [
    { path: '/students', label: 'Students', icon: 'people' },
    { path: '/courses', label: 'Courses', icon: 'menu_book' },
  ];

  private readonly sidenav = viewChild.required<MatSidenav>('sidenav');

  /** Below 960px the sidebar becomes a slide-out menu and the tabs carry the navigation. */
  readonly isMobile = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 959.98px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  constructor() {
    // Close the slide-out menu after picking a destination on small screens.
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        if (this.isMobile()) {
          this.sidenav().close();
        }
      });
  }
}
