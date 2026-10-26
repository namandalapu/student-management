# Student Management

A full-stack app for managing students and the courses they're enrolled in, built as **one Spring Boot project**. The Angular web UI lives inside the project at `src/main/web`, and the Maven build compiles it into the application, so a single command builds and runs everything and the UI and REST API are served together from http://localhost:8080.

- **Server:** Java 21, Spring Boot 4.1 (Spring MVC, Spring Data JPA, Bean Validation), H2 embedded database
- **Web UI:** Angular 18 (standalone components), Angular Material 18, RxJS, in `src/main/web`
- **Build:** Maven (wrapper included). The `frontend-maven-plugin` downloads Node, runs `npm install` and `ng build`, and writes the UI into `target/classes/static`, where Spring Boot serves it.

Each student can be enrolled in many courses, and each course has many students. Both students and courses support full create, read, update, and delete.

![Students list](docs/students.png)

## Features

| Requirement | Where it is |
|---|---|
| CRUD operations | Students and courses: list, view, create, edit, and delete. Enroll and drop a student from a course. |
| Routing | `/students`, `/students/new`, `/students/:id`, `/students/:id/edit`, the same four for `/courses`, and a 404 page. Routes are lazy-loaded, and route params are bound to component inputs. |
| Multiple tabs with modal confirmation | The top-level **Students / Courses** tabs are routed tabs. Detail pages have inner tabs (**Profile / Courses** and **Details / Roster**). Leaving a form with unsaved changes, for example by switching tabs, opens a **Discard changes?** modal through a `CanDeactivate` guard. Every delete or drop asks for confirmation in a modal. |
| Error handling | **Server:** `@RestControllerAdvice` returns one consistent JSON error shape (`status`, `message`, `path`, `fieldErrors`) for validation (400), not found (404), duplicates and business rules (409), malformed JSON, type mismatches, and unexpected errors (500). **Web UI:** an HTTP interceptor turns every failure into an `AppError`. Network and 5xx errors show a global snackbar, field errors from the server appear under the matching form field, and list/detail pages show an inline error banner with a Retry button. |
| Multi-component communication | Parent to child through `@Input` / `@Output` (`StudentListComponent` → `StudentTableComponent`, `ErrorBannerComponent`). Shared RxJS stores carry state between pages. A `DataChangeBus` lets the stores refresh each other: enrolling a student updates the course's enrolled count, and deleting a course updates the students' course lists. |
| Form handling | Reactive forms with client-side validators that match the backend rules (required, max length, email, date in the past, course-code pattern, credits 1–6, capacity at least the current enrollment). Server-side errors are mapped back onto the form. |
| State management with RxJS | `StudentStore` and `CourseStore` keep state in a `BehaviorSubject` and expose `students$`, `loading$`, and `error$` streams. Search uses `debounceTime`, `distinctUntilChanged`, and `switchMap`, so stale responses are dropped. |
| Backend CRUD, validation, exceptions, transactions | `@Transactional` services. Creating or updating a student together with its course list is atomic: if any course is missing or full, nothing is saved. Deleting a course drops all its enrollments in the same transaction. `@Version` gives optimistic locking. |
| RestTemplate | Spring's RestTemplate (`TestRestTemplate`) drives the end-to-end API tests against the running server. |
| Backend unit tests | 44 tests: Mockito service tests, `@WebMvcTest` controller and error-handling tests, `@DataJpaTest` repository tests, full HTTP integration tests, and tests that the UI is served correctly. |

## Prerequisites

- **JDK 17 or newer** (built and tested with JDK 21).

That's all. You don't need Maven, Node.js, or the Angular CLI installed: the Maven Wrapper downloads Maven, and the build downloads its own copy of Node into `.node/` the first time it runs.

## Running locally

From the project folder:

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Open http://localhost:8080. This one command builds the Angular UI, starts the server, and serves the UI and the REST API (`/api/...`) together. The first run takes a few minutes while it downloads Node and the npm packages; later runs are faster.

Refreshing the page or opening a deep link such as http://localhost:8080/students/1/edit works, because the server sends client-side routes to `index.html`. Unknown `/api/...` paths still return a JSON 404.

On the first start the app creates an H2 database file in `data/` and loads sample students and courses. To start over with fresh data, stop the app and delete that folder.

- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/studentdb`, user `sa`, empty password)

### Build a runnable jar

```bash
./mvnw clean package
java -jar target/student-management-0.0.1-SNAPSHOT.jar
```

The jar contains the server and the compiled UI.

### Working on the UI with live reload (optional)

When you're changing Angular code, you can run the Angular dev server next to the running app for instant reloads:

```bash
./mvnw spring-boot:run -Dskip.npm -Dskip.installnodenpm   # server only, on :8080
cd src/main/web
npm install
npm start                                                 # UI on :4200, proxies /api to :8080
```

Then open http://localhost:4200. This needs Node.js 18.19+ or 20.11+ installed locally. The `-Dskip.npm -Dskip.installnodenpm` flags skip the UI build in any Maven command.

## Running tests

```bash
./mvnw test                   # 44 backend tests
```

`WebUiIntegrationTest` checks that the built UI is served from `/`, that deep links fall back to `index.html`, and that API and missing-file 404s are left alone. It is skipped when the UI build is turned off with `-Dskip.npm`.

```bash
cd src/main/web
npm test -- --watch=false --browsers=ChromeHeadless
```

## REST API

Base URL: `http://localhost:8080/api`

| Method | Path | Description |
|---|---|---|
| GET | `/students?search=` | List students, optionally filtered by name or email |
| GET | `/students/{id}` | Get one student with their courses |
| POST | `/students` | Create a student (optionally with `courseIds`) |
| PUT | `/students/{id}` | Update a student. `courseIds` replaces the enrollments; if omitted or null, they stay as they are |
| DELETE | `/students/{id}` | Delete a student |
| PUT | `/students/{id}/courses/{courseId}` | Enroll a student in a course |
| DELETE | `/students/{id}/courses/{courseId}` | Drop a student from a course |
| GET | `/courses` | List courses with enrolled counts |
| GET | `/courses/{id}` | Get one course |
| GET | `/courses/{id}/students` | Course roster |
| POST | `/courses` | Create a course |
| PUT | `/courses/{id}` | Update a course |
| DELETE | `/courses/{id}` | Delete a course and remove it from all students |

Example request body for a student:

```json
{
  "firstName": "Ada",
  "lastName": "Lovelace",
  "email": "ada@example.edu",
  "dateOfBirth": "2004-12-10",
  "courseIds": [1, 3]
}
```

Example error response:

```json
{
  "timestamp": "2026-10-20T22:10:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/students",
  "fieldErrors": { "email": "Email must be a valid email address" }
}
```

## Project structure

One Maven project. The Angular UI is a folder inside it, not a separate project.

```
student-management/
  pom.xml                 Maven build: Spring Boot app + Angular UI build
  mvnw, mvnw.cmd          Maven Wrapper
  src/
    main/
      java/com/example/studentmgmt/
        controller/       REST controllers
        service/          Transactional business logic
        repository/       Spring Data JPA repositories
        entity/           JPA entities (Student, Course; many-to-many through student_courses)
        dto/              Request/response records with validation annotations
        exception/        Custom exceptions and the global exception handler
        config/           Web config (serves the UI, deep-link fallback, CORS) and sample data seeding
      resources/
        application.yaml
      web/                Angular 18 web UI (angular.json, package.json)
        src/app/
          core/           Models, error interceptor, confirm dialog, unsaved-changes guard, notifications, change bus
          students/       Student API service, store, list/table/form/detail components
          courses/        Course API service, store, list/form/detail components
          shared/         Error banner, form helpers, 404 page
    test/java/...         Unit, slice, and integration tests
  data/                   H2 database files (created on first run, not committed)
  target/classes/static/  Compiled UI (build output, not committed)
```
