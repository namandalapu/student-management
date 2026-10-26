import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { errorInterceptor } from '../core/app-error';
import { DataChangeBus } from '../core/data-change-bus';
import { Student } from '../core/models';
import { StudentStore } from './student.store';

const ada: Student = {
  id: 1,
  firstName: 'Ada',
  lastName: 'Lovelace',
  email: 'ada@example.edu',
  dateOfBirth: null,
  courses: [],
};

describe('StudentStore', () => {
  let store: StudentStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideNoopAnimations(),
      ],
    });
    store = TestBed.inject(StudentStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads students on refresh', () => {
    let students: Student[] = [];
    store.students$.subscribe((s) => (students = s));

    store.refresh();
    http.expectOne('/api/students').flush([ada]);

    expect(students).toEqual([ada]);
  });

  it('debounces search and sends the trimmed term', fakeAsync(() => {
    store.setSearch(' a');
    store.setSearch(' ad ');
    tick(300);

    const req = http.expectOne((r) => r.url === '/api/students');
    expect(req.request.params.get('search')).toBe('ad');
    req.flush([ada]);
  }));

  it('exposes the server message when loading fails', () => {
    let error: string | null = null;
    store.error$.subscribe((e) => (error = e));

    store.refresh();
    http
      .expectOne('/api/students')
      .flush({ message: 'Student with id 9 was not found' }, { status: 404, statusText: 'Not Found' });

    expect(error as string | null).toBe('Student with id 9 was not found');
  });

  it('reloads and notifies other stores after a delete', () => {
    const notified: string[] = [];
    TestBed.inject(DataChangeBus).on('students').subscribe((e) => notified.push(e));

    store.delete(1).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/students/1' }).flush(null);
    http.expectOne('/api/students').flush([]);

    expect(notified).toEqual(['students']);
  });

  it('reloads when courses change', () => {
    TestBed.inject(DataChangeBus).notify('courses');
    http.expectOne('/api/students').flush([ada]);
  });
});
