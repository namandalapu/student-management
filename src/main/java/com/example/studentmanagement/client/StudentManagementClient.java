package com.example.studentmanagement.client;

import com.example.studentmanagement.dto.CourseRequest;
import com.example.studentmanagement.dto.CourseResponse;
import com.example.studentmanagement.dto.StudentRequest;
import com.example.studentmanagement.dto.StudentResponse;
import com.example.studentmanagement.dto.StudentSummary;
import com.example.studentmanagement.exception.ApiError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * Java client for the Student Management REST API, built on Spring's {@link RestTemplate}.
 *
 * <p>Covers every endpoint under {@code /api/students} and {@code /api/courses}, using the same request and
 * response DTOs as the server, so a Java program (another service, a batch job, or the end-to-end tests)
 * can call the API without writing any HTTP code. Error responses are turned into {@link ApiClientException}
 * carrying the server's {@link ApiError} body.
 *
 * <p>As a Spring bean it points at {@code app.client.base-url} (default: this app on localhost). For any other
 * server, create one with {@link #StudentManagementClient(RestTemplateBuilder, String)}.
 */
@Component
public class StudentManagementClient {

    private final RestTemplate restTemplate;

    /**
     * Creates a client for the API at {@code baseUrl}, e.g. {@code http://localhost:8080}.
     *
     * @param builder Spring Boot's preconfigured builder (JSON converters, timeouts, observability)
     */
    @Autowired
    public StudentManagementClient(RestTemplateBuilder builder,
                                   @Value("${app.client.base-url:http://localhost:${server.port:8080}}")
                                   String baseUrl) {
        this(builder.rootUri(baseUrl).build());
    }

    /** Creates a client around an existing {@link RestTemplate}, whose root URI must already be set. */
    public StudentManagementClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ---- Students ----

    /** {@code GET /api/students}: every student, sorted by last name. */
    public List<StudentResponse> listStudents() {
        return call(() -> List.of(restTemplate.getForObject("/api/students", StudentResponse[].class)));
    }

    /** {@code GET /api/students?search=}: students whose name or email contains {@code search}. */
    public List<StudentResponse> searchStudents(String search) {
        // The {search} template variable is URL-encoded by RestTemplate, so spaces, & and + are safe.
        return call(() -> List.of(restTemplate.getForObject("/api/students?search={search}",
                StudentResponse[].class, search)));
    }

    /** {@code GET /api/students/{id}}: one student with their courses. */
    public StudentResponse getStudent(long id) {
        return call(() -> restTemplate.getForObject("/api/students/{id}", StudentResponse.class, id));
    }

    /** {@code POST /api/students}: creates a student (and optional enrollments); returns it with its new id. */
    public StudentResponse createStudent(StudentRequest request) {
        return call(() -> restTemplate.postForObject("/api/students", request, StudentResponse.class));
    }

    /** {@code PUT /api/students/{id}}: updates a student; non-null {@code courseIds} replaces enrollments. */
    public StudentResponse updateStudent(long id, StudentRequest request) {
        return call(() -> restTemplate.exchange("/api/students/{id}", HttpMethod.PUT, new HttpEntity<>(request),
                StudentResponse.class, id).getBody());
    }

    /** {@code DELETE /api/students/{id}}: deletes a student and their enrollments. */
    public void deleteStudent(long id) {
        run(() -> restTemplate.delete("/api/students/{id}", id));
    }

    /** {@code PUT /api/students/{id}/courses/{courseId}}: enrolls a student in one course. */
    public StudentResponse enroll(long studentId, long courseId) {
        return call(() -> restTemplate.exchange("/api/students/{id}/courses/{courseId}", HttpMethod.PUT,
                HttpEntity.EMPTY, StudentResponse.class, studentId, courseId).getBody());
    }

    /** {@code DELETE /api/students/{id}/courses/{courseId}}: drops a student from one course. */
    public StudentResponse unenroll(long studentId, long courseId) {
        return call(() -> restTemplate.exchange("/api/students/{id}/courses/{courseId}", HttpMethod.DELETE,
                HttpEntity.EMPTY, StudentResponse.class, studentId, courseId).getBody());
    }

    // ---- Courses ----

    /** {@code GET /api/courses}: every course, sorted by code, with enrolled counts. */
    public List<CourseResponse> listCourses() {
        return call(() -> List.of(restTemplate.getForObject("/api/courses", CourseResponse[].class)));
    }

    /** {@code GET /api/courses/{id}}: one course. */
    public CourseResponse getCourse(long id) {
        return call(() -> restTemplate.getForObject("/api/courses/{id}", CourseResponse.class, id));
    }

    /** {@code GET /api/courses/{id}/students}: the course roster, sorted by last name. */
    public List<StudentSummary> getRoster(long courseId) {
        return call(() -> List.of(restTemplate.getForObject("/api/courses/{id}/students",
                StudentSummary[].class, courseId)));
    }

    /** {@code POST /api/courses}: creates a course; returns it with its new id. */
    public CourseResponse createCourse(CourseRequest request) {
        return call(() -> restTemplate.postForObject("/api/courses", request, CourseResponse.class));
    }

    /** {@code PUT /api/courses/{id}}: updates a course. */
    public CourseResponse updateCourse(long id, CourseRequest request) {
        return call(() -> restTemplate.exchange("/api/courses/{id}", HttpMethod.PUT, new HttpEntity<>(request),
                CourseResponse.class, id).getBody());
    }

    /** {@code DELETE /api/courses/{id}}: deletes a course and drops it from every student. */
    public void deleteCourse(long id) {
        run(() -> restTemplate.delete("/api/courses/{id}", id));
    }

    // ---- Error handling ----

    /**
     * Runs one request. RestTemplate throws {@link RestClientResponseException} for 4xx/5xx responses; this
     * reads the API's JSON error body from it (with the same converters RestTemplate uses for normal
     * responses) and rethrows it as an {@link ApiClientException}.
     */
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            throw new ApiClientException(ex.getStatusCode(), readError(ex), ex);
        }
    }

    /** {@link #call} for requests with no response body. */
    private void run(Runnable request) {
        call(() -> {
            request.run();
            return null;
        });
    }

    /** The error body as {@link ApiError}, or {@code null} if it is empty or not the API's JSON shape. */
    private static ApiError readError(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(ApiError.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
