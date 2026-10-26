import { Routes } from '@angular/router';
import { unsavedChangesGuard } from './core/unsaved-changes.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'students' },
  {
    path: 'students',
    children: [
      {
        path: '',
        title: 'Students',
        loadComponent: () => import('./students/student-list.component').then((m) => m.StudentListComponent),
      },
      {
        path: 'new',
        title: 'New student',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./students/student-form.component').then((m) => m.StudentFormComponent),
      },
      {
        path: ':id',
        title: 'Student',
        loadComponent: () => import('./students/student-detail.component').then((m) => m.StudentDetailComponent),
      },
      {
        path: ':id/edit',
        title: 'Edit student',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./students/student-form.component').then((m) => m.StudentFormComponent),
      },
    ],
  },
  {
    path: 'courses',
    children: [
      {
        path: '',
        title: 'Courses',
        loadComponent: () => import('./courses/course-list.component').then((m) => m.CourseListComponent),
      },
      {
        path: 'new',
        title: 'New course',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./courses/course-form.component').then((m) => m.CourseFormComponent),
      },
      {
        path: ':id',
        title: 'Course',
        loadComponent: () => import('./courses/course-detail.component').then((m) => m.CourseDetailComponent),
      },
      {
        path: ':id/edit',
        title: 'Edit course',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./courses/course-form.component').then((m) => m.CourseFormComponent),
      },
    ],
  },
  {
    path: '**',
    title: 'Not found',
    loadComponent: () => import('./shared/not-found.component').then((m) => m.NotFoundComponent),
  },
];
