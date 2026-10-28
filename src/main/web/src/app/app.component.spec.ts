import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), provideNoopAnimations()],
    }).compileComponents();
  });

  it('renders the Students and Courses tabs', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const links = Array.from(fixture.nativeElement.querySelectorAll('a[mat-tab-link]')) as HTMLElement[];
    expect(links.map((a) => a.textContent?.trim())).toEqual(['people Students', 'menu_book Courses']);
  });

  it('renders sidebar links to both sections and the create forms', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const hrefs = Array.from(fixture.nativeElement.querySelectorAll('mat-sidenav a') as NodeListOf<HTMLAnchorElement>)
      .map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/students', '/students', '/courses', '/students/new', '/courses/new']);
  });
});
