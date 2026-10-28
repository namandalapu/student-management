package com.example.studentmanagement.service;

import com.example.studentmanagement.dto.CourseRequest;
import com.example.studentmanagement.dto.CourseResponse;
import com.example.studentmanagement.entity.Course;
import com.example.studentmanagement.entity.Student;
import com.example.studentmanagement.exception.BusinessRuleException;
import com.example.studentmanagement.exception.DuplicateResourceException;
import com.example.studentmanagement.exception.ResourceNotFoundException;
import com.example.studentmanagement.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseService courseService;

    @Test
    void createSavesCourseAndBlankDescriptionBecomesNull() {
        when(courseRepository.existsByCodeIgnoreCase("CS101")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CourseResponse response = courseService.create(new CourseRequest("CS101", " Intro ", "  ", 4, 30));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Intro");
        assertThat(response.description()).isNull();
        assertThat(response.enrolledCount()).isZero();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(courseRepository.existsByCodeIgnoreCase("CS101")).thenReturn(true);

        assertThatThrownBy(() -> courseService.create(new CourseRequest("CS101", "Intro", null, 4, 30)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(courseRepository, never()).save(any());
    }

    @Test
    void updateRejectsCapacityBelowEnrollment() {
        Course course = course(1L, 5);
        new Student("A", "One", "a@example.edu", null).enroll(course);
        new Student("B", "Two", "b@example.edu", null).enroll(course);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(courseRepository.existsByCodeIgnoreCaseAndIdNot("CS101", 1L)).thenReturn(false);

        assertThatThrownBy(() -> courseService.update(1L, new CourseRequest("CS101", "Intro", null, 4, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 students already enrolled");
        assertThat(course.getCapacity()).isEqualTo(5);
    }

    @Test
    void updateChangesFields() {
        Course course = course(1L, 5);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));
        when(courseRepository.existsByCodeIgnoreCaseAndIdNot("CS102", 1L)).thenReturn(false);

        CourseResponse response = courseService.update(1L, new CourseRequest("CS102", "Intro II", "More", 3, 10));

        assertThat(response.code()).isEqualTo("CS102");
        assertThat(response.capacity()).isEqualTo(10);
        assertThat(response.description()).isEqualTo("More");
    }

    @Test
    void deleteUnenrollsStudentsFirst() {
        Course course = course(1L, 5);
        Student student = new Student("A", "One", "a@example.edu", null);
        student.enroll(course);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.delete(1L);

        assertThat(student.getCourses()).isEmpty();
        verify(courseRepository).delete(course);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(courseRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.findById(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findStudentsReturnsSortedRoster() {
        Course course = course(1L, 5);
        new Student("Zed", "Zulu", "z@example.edu", null).enroll(course);
        new Student("Amy", "Alpha", "a@example.edu", null).enroll(course);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThat(courseService.findStudents(1L)).extracting("lastName").containsExactly("Alpha", "Zulu");
    }

    private static Course course(Long id, int capacity) {
        Course course = new Course("CS101", "Intro", null, 4, capacity);
        course.setId(id);
        return course;
    }
}
