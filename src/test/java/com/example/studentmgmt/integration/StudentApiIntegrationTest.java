package com.example.studentmgmt.integration;

import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.dto.StudentSummary;
import com.example.studentmgmt.exception.ApiError;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real HTTP API end to end with RestTemplate against an in-memory H2 database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class StudentApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    void cleanDatabase() {
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void fullStudentAndCourseLifecycle() {
        CourseResponse course = createCourse("CS101", 10);

        ResponseEntity<StudentResponse> created = rest.postForEntity("/api/students",
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(course.id())),
                StudentResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long studentId = created.getBody().id();
        assertThat(created.getBody().courses()).extracting("code").containsExactly("CS101");

        StudentSummary[] roster = rest.getForObject("/api/courses/" + course.id() + "/students",
                StudentSummary[].class);
        assertThat(roster).extracting(StudentSummary::email).containsExactly("ada@example.edu");

        rest.put("/api/students/" + studentId,
                new StudentRequest("Ada", "King", "ada@example.edu", null, Set.of()));
        StudentResponse updated = rest.getForObject("/api/students/" + studentId, StudentResponse.class);
        assertThat(updated.lastName()).isEqualTo("King");
        assertThat(updated.courses()).isEmpty();

        ResponseEntity<Void> deleted = rest.exchange("/api/students/" + studentId, HttpMethod.DELETE,
                HttpEntity.EMPTY, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/students/" + studentId, ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void failedEnrollmentRollsBackTheWholeCreate() {
        CourseResponse open = createCourse("CS101", 10);
        CourseResponse full = createCourse("PHYS120", 1);
        rest.postForEntity("/api/students",
                new StudentRequest("Grace", "Hopper", "grace@example.edu", null, Set.of(full.id())),
                StudentResponse.class);

        ResponseEntity<ApiError> response = rest.postForEntity("/api/students",
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(open.id(), full.id())),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("PHYS120 is full");
        // Transaction rolled back: no student was created and CS101 has no enrollments.
        assertThat(studentRepository.existsByEmailIgnoreCase("ada@example.edu")).isFalse();
        CourseResponse openAfter = rest.getForObject("/api/courses/" + open.id(), CourseResponse.class);
        assertThat(openAfter.enrolledCount()).isZero();
    }

    @Test
    void deletingCourseRemovesItFromStudents() {
        CourseResponse course = createCourse("CS101", 10);
        StudentResponse student = rest.postForObject("/api/students",
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(course.id())),
                StudentResponse.class);

        rest.delete("/api/courses/" + course.id());

        StudentResponse after = rest.getForObject("/api/students/" + student.id(), StudentResponse.class);
        assertThat(after.courses()).isEmpty();
    }

    @Test
    void invalidCourseReturnsFieldErrors() {
        ResponseEntity<ApiError> response = rest.postForEntity("/api/courses",
                new CourseRequest("bad", "", null, 9, 0), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKeys("code", "title", "credits", "capacity");
    }

    @Test
    void enrollAndUnenrollEndpoints() {
        CourseResponse course = createCourse("CS101", 10);
        StudentResponse student = rest.postForObject("/api/students",
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, null), StudentResponse.class);

        ResponseEntity<StudentResponse> enrolled = rest.exchange(
                "/api/students/" + student.id() + "/courses/" + course.id(), HttpMethod.PUT, HttpEntity.EMPTY,
                StudentResponse.class);
        assertThat(enrolled.getBody().courses()).hasSize(1);

        ResponseEntity<ApiError> again = rest.exchange(
                "/api/students/" + student.id() + "/courses/" + course.id(), HttpMethod.PUT, HttpEntity.EMPTY,
                ApiError.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<StudentResponse> dropped = rest.exchange(
                "/api/students/" + student.id() + "/courses/" + course.id(), HttpMethod.DELETE, HttpEntity.EMPTY,
                StudentResponse.class);
        assertThat(dropped.getBody().courses()).isEmpty();
    }

    private CourseResponse createCourse(String code, int capacity) {
        ResponseEntity<CourseResponse> response = rest.postForEntity("/api/courses",
                new CourseRequest(code, code + " title", null, 3, capacity), CourseResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
