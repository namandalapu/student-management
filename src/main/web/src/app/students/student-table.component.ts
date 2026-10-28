import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Student } from '../core/models';

/** Presentational table: gets students through an input and reports user actions through outputs. */
@Component({
  selector: 'app-student-table',
  standalone: true,
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatChipsModule, MatTooltipModule],
  template: `
    <div class="table-wrap">
    <table mat-table [dataSource]="students" class="full-width">
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let s">
          <a [routerLink]="['/students', s.id]" class="row-link">{{ s.lastName }}, {{ s.firstName }}</a>
        </td>
      </ng-container>

      <ng-container matColumnDef="email">
        <th mat-header-cell *matHeaderCellDef>Email</th>
        <td mat-cell *matCellDef="let s">{{ s.email }}</td>
      </ng-container>

      <ng-container matColumnDef="courses">
        <th mat-header-cell *matHeaderCellDef>Courses</th>
        <td mat-cell *matCellDef="let s">
          @if (s.courses.length) {
            <mat-chip-set aria-label="Enrolled courses">
              @for (c of s.courses; track c.id) {
                <mat-chip [matTooltip]="c.title">{{ c.code }}</mat-chip>
              }
            </mat-chip-set>
          } @else {
            <span class="muted">None</span>
          }
        </td>
      </ng-container>

      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
        <td mat-cell *matCellDef="let s" class="actions-col">
          <a mat-icon-button [routerLink]="['/students', s.id, 'edit']" matTooltip="Edit" aria-label="Edit student">
            <mat-icon>edit</mat-icon>
          </a>
          <button mat-icon-button color="warn" (click)="delete.emit(s)" matTooltip="Delete" aria-label="Delete student">
            <mat-icon>delete</mat-icon>
          </button>
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
    </div>
  `,
})
export class StudentTableComponent {
  @Input({ required: true }) students: Student[] = [];
  @Output() readonly delete = new EventEmitter<Student>();

  readonly columns = ['name', 'email', 'courses', 'actions'];
}
