package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private StudentService studentService;

    private Course cs101;
    private Course math150;

    @BeforeEach
    void setUp() {
        cs101 = new Course("CS101", "Intro to Programming", null, 4, 30);
        cs101.setId(1L);
        math150 = new Course("MATH150", "Calculus I", null, 3, 1);
        math150.setId(2L);
    }

    @Test
    void createNormalizesEmailAndEnrollsInRequestedCourses() {
        when(studentRepository.existsByEmailIgnoreCase("ada@example.edu")).thenReturn(false);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(cs101));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        StudentResponse response = studentService.create(
                new StudentRequest(" Ada ", "Lovelace", " ADA@example.edu ", LocalDate.of(2004, 1, 1), Set.of(1L)));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.email()).isEqualTo("ada@example.edu");
        assertThat(response.courses()).extracting("code").containsExactly("CS101");
        assertThat(cs101.getStudents()).hasSize(1);
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(studentRepository.existsByEmailIgnoreCase("ada@example.edu")).thenReturn(true);

        assertThatThrownBy(() -> studentService.create(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createFailsWhenCourseIsFullAndDoesNotSave() {
        Student existing = new Student("Grace", "Hopper", "grace@example.edu", null);
        existing.enroll(math150); // capacity 1, now full
        when(studentRepository.existsByEmailIgnoreCase("ada@example.edu")).thenReturn(false);
        when(courseRepository.findById(2L)).thenReturn(Optional.of(math150));

        assertThatThrownBy(() -> studentService.create(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(2L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is full");
        verify(studentRepository, never()).save(any());
    }

    @Test
    void createFailsWhenCourseDoesNotExist() {
        when(studentRepository.existsByEmailIgnoreCase("ada@example.edu")).thenReturn(false);
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.create(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(99L))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course with id 99 was not found");
    }

    @Test
    void updateReplacesEnrollments() {
        Student ada = student(5L);
        ada.enroll(cs101);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(ada));
        when(studentRepository.existsByEmailIgnoreCaseAndIdNot("ada@example.edu", 5L)).thenReturn(false);
        when(courseRepository.findById(2L)).thenReturn(Optional.of(math150));

        StudentResponse response = studentService.update(5L,
                new StudentRequest("Ada", "King", "ada@example.edu", null, Set.of(2L)));

        assertThat(response.lastName()).isEqualTo("King");
        assertThat(response.courses()).extracting("code").containsExactly("MATH150");
        assertThat(cs101.getStudents()).isEmpty();
    }

    @Test
    void updateWithNullCourseIdsKeepsEnrollments() {
        Student ada = student(5L);
        ada.enroll(cs101);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(ada));
        when(studentRepository.existsByEmailIgnoreCaseAndIdNot("ada@example.edu", 5L)).thenReturn(false);

        StudentResponse response = studentService.update(5L,
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, null));

        assertThat(response.courses()).extracting("code").containsExactly("CS101");
    }

    @Test
    void updateRejectsEmailUsedByAnotherStudent() {
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student(5L)));
        when(studentRepository.existsByEmailIgnoreCaseAndIdNot("alan@example.edu", 5L)).thenReturn(true);

        assertThatThrownBy(() -> studentService.update(5L,
                new StudentRequest("Ada", "Lovelace", "alan@example.edu", null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(studentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.findById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Student with id 42 was not found");
    }

    @Test
    void findAllUsesSearchWhenTermGiven() {
        when(studentRepository.search("ada")).thenReturn(List.of(student(5L)));

        assertThat(studentService.findAll("  ada ")).hasSize(1);
        verify(studentRepository, never()).findAllByOrderByLastNameAscFirstNameAsc();
    }

    @Test
    void enrollRejectsDuplicateEnrollment() {
        Student ada = student(5L);
        ada.enroll(cs101);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(ada));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(cs101));

        assertThatThrownBy(() -> studentService.enroll(5L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already enrolled");
    }

    @Test
    void unenrollRemovesCourse() {
        Student ada = student(5L);
        ada.enroll(cs101);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(ada));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(cs101));

        StudentResponse response = studentService.unenroll(5L, 1L);

        assertThat(response.courses()).isEmpty();
        assertThat(cs101.getStudents()).isEmpty();
    }

    @Test
    void deleteRemovesEnrollmentsThenStudent() {
        Student ada = student(5L);
        ada.enroll(cs101);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(ada));

        studentService.delete(5L);

        assertThat(cs101.getStudents()).isEmpty();
        verify(studentRepository).delete(ada);
    }

    private static Student student(Long id) {
        Student student = new Student("Ada", "Lovelace", "ada@example.edu", null);
        student.setId(id);
        return student;
    }
}
