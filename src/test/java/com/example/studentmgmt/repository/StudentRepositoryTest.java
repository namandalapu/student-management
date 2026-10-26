package com.example.studentmgmt.repository;

import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    void setUp() {
        studentRepository.save(new Student("Ada", "Lovelace", "ada@example.edu", null));
        studentRepository.save(new Student("Alan", "Turing", "alan@example.edu", null));
        studentRepository.save(new Student("Grace", "Hopper", "grace@navy.mil", null));
    }

    @Test
    void searchMatchesNameOrEmailCaseInsensitively() {
        assertThat(studentRepository.search("ADA")).extracting(Student::getFirstName).containsExactly("Ada");
        assertThat(studentRepository.search("navy")).extracting(Student::getFirstName).containsExactly("Grace");
        assertThat(studentRepository.search("example.edu")).extracting(Student::getLastName)
                .containsExactly("Lovelace", "Turing");
    }

    @Test
    void existsByEmailIgnoresCase() {
        assertThat(studentRepository.existsByEmailIgnoreCase("ADA@EXAMPLE.EDU")).isTrue();
        Long adaId = studentRepository.search("ada").getFirst().getId();
        assertThat(studentRepository.existsByEmailIgnoreCaseAndIdNot("ada@example.edu", adaId)).isFalse();
    }

    @Test
    void uniqueEmailIsEnforcedByDatabase() {
        assertThatThrownBy(() -> studentRepository.saveAndFlush(new Student("X", "Y", "ada@example.edu", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enrollmentIsPersistedInJoinTable() {
        Course course = courseRepository.save(new Course("CS101", "Intro", null, 4, 10));
        Student ada = studentRepository.search("ada").getFirst();
        ada.enroll(course);
        studentRepository.flush();

        Student reloaded = studentRepository.findById(ada.getId()).orElseThrow();
        assertThat(reloaded.getCourses()).extracting(Course::getCode).containsExactly("CS101");
    }
}
