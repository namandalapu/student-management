package com.example.studentmanagement.integration;

import com.example.studentmanagement.client.ApiClientException;
import com.example.studentmanagement.client.StudentManagementClient;
import com.example.studentmanagement.dto.CourseRequest;
import com.example.studentmanagement.dto.CourseResponse;
import com.example.studentmanagement.dto.StudentRequest;
import com.example.studentmanagement.dto.StudentResponse;
import com.example.studentmanagement.dto.StudentSummary;
import com.example.studentmanagement.repository.CourseRepository;
import com.example.studentmanagement.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real HTTP API end to end against an in-memory H2 database, calling it through
 * {@link StudentManagementClient}, the application's RestTemplate-based client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    private StudentManagementClient client;

    @BeforeEach
    void setUp() {
        client = new StudentManagementClient(restTemplateBuilder, "http://localhost:" + port);
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void fullStudentAndCourseLifecycle() {
        CourseResponse course = createCourse("CS101", 10);

        StudentResponse created = client.createStudent(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(course.id())));
        assertThat(created.id()).isNotNull();
        assertThat(created.courses()).extracting("code").containsExactly("CS101");

        assertThat(client.getRoster(course.id())).extracting(StudentSummary::email)
                .containsExactly("ada@example.edu");

        StudentResponse updated = client.updateStudent(created.id(),
                new StudentRequest("Ada", "King", "ada@example.edu", null, Set.of()));
        assertThat(updated.lastName()).isEqualTo("King");
        assertThat(updated.courses()).isEmpty();
        assertThat(client.getStudent(created.id()).lastName()).isEqualTo("King");

        client.deleteStudent(created.id());
        assertThatThrownBy(() -> client.getStudent(created.id()))
                .isInstanceOfSatisfying(ApiClientException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("Student with id " + created.id() + " was not found");
                });
    }

    @Test
    void courseCrudThroughTheClient() {
        CourseResponse created = createCourse("CS101", 10);

        CourseResponse updated = client.updateCourse(created.id(),
                new CourseRequest("CS102", "Data Structures", "Trees and graphs", 4, 20));
        assertThat(updated.code()).isEqualTo("CS102");
        assertThat(client.listCourses()).extracting(CourseResponse::code).containsExactly("CS102");

        client.deleteCourse(created.id());
        assertThat(client.listCourses()).isEmpty();
    }

    @Test
    void searchFindsStudentsByNameOrEmail() {
        client.createStudent(new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, null));
        client.createStudent(new StudentRequest("Alan", "Turing", "alan@navy.mil", null, null));

        assertThat(client.searchStudents("navy")).extracting(StudentResponse::firstName).containsExactly("Alan");
        assertThat(client.listStudents()).hasSize(2);
    }

    @Test
    void failedEnrollmentRollsBackTheWholeCreate() {
        CourseResponse open = createCourse("CS101", 10);
        CourseResponse full = createCourse("PHYS120", 1);
        client.createStudent(new StudentRequest("Grace", "Hopper", "grace@example.edu", null, Set.of(full.id())));

        assertThatThrownBy(() -> client.createStudent(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(open.id(), full.id()))))
                .isInstanceOfSatisfying(ApiClientException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("PHYS120 is full");
                });

        // Transaction rolled back: no student was created and CS101 has no enrollments.
        assertThat(studentRepository.existsByEmailIgnoreCase("ada@example.edu")).isFalse();
        assertThat(client.getCourse(open.id()).enrolledCount()).isZero();
    }

    @Test
    void deletingCourseRemovesItFromStudents() {
        CourseResponse course = createCourse("CS101", 10);
        StudentResponse student = client.createStudent(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(course.id())));

        client.deleteCourse(course.id());

        assertThat(client.getStudent(student.id()).courses()).isEmpty();
    }

    @Test
    void invalidCourseReturnsFieldErrors() {
        assertThatThrownBy(() -> client.createCourse(new CourseRequest("bad", "", null, 9, 0)))
                .isInstanceOfSatisfying(ApiClientException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getFieldErrors()).containsKeys("code", "title", "credits", "capacity");
                });
    }

    @Test
    void enrollAndUnenrollEndpoints() {
        CourseResponse course = createCourse("CS101", 10);
        StudentResponse student = client.createStudent(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, null));

        assertThat(client.enroll(student.id(), course.id()).courses()).hasSize(1);

        assertThatThrownBy(() -> client.enroll(student.id(), course.id()))
                .isInstanceOfSatisfying(ApiClientException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(client.unenroll(student.id(), course.id()).courses()).isEmpty();
    }

    @Test
    void concurrentEnrollmentsNeverOverbookACourse() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            // Several rounds, since a race doesn't show up on every attempt.
            for (int round = 0; round < 10; round++) {
                CourseResponse course = createCourse("RC" + (100 + round), 1);
                List<Long> studentIds = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    studentIds.add(client.createStudent(new StudentRequest("S" + i, "Racer",
                            "racer" + round + "-" + i + "@example.edu", null, null)).id());
                }

                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (Long id : studentIds) {
                    results.add(pool.submit(() -> {
                        start.await();
                        try {
                            client.enroll(id, course.id());
                            return true;
                        } catch (ApiClientException full) {
                            return false;
                        }
                    }));
                }
                start.countDown();
                long succeeded = 0;
                for (Future<Boolean> result : results) {
                    if (result.get(30, TimeUnit.SECONDS)) {
                        succeeded++;
                    }
                }

                assertThat(client.getCourse(course.id()).enrolledCount())
                        .as("round %d enrolled count", round).isEqualTo(1);
                assertThat(succeeded).as("round %d successful enrollments", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private CourseResponse createCourse(String code, int capacity) {
        return client.createCourse(new CourseRequest(code, code + " title", null, 3, capacity));
    }
}
