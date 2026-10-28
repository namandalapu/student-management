import { Injectable } from '@angular/core';
import { Observable, Subject, filter } from 'rxjs';

export type Entity = 'students' | 'courses';

/**
 * Lets stores tell each other that data changed. Enrolling a student changes a course's enrolled count,
 * and deleting a course changes students' course lists, so each store refreshes when the other changes.
 */
@Injectable({ providedIn: 'root' })
export class DataChangeBus {
  private readonly changes = new Subject<Entity>();

  /** Announces that data of this kind changed (called by a store after a successful write). */
  notify(entity: Entity): void {
    this.changes.next(entity);
  }

  /** Emits every time data of this kind changes; a store subscribes to the *other* kind to stay current. */
  on(entity: Entity): Observable<Entity> {
    return this.changes.pipe(filter((e) => e === entity));
  }
}
