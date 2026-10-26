package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.dto.StudentSummary;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> findAll() {
        return courseRepository.findAllByOrderByCodeAsc().stream().map(CourseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse findById(Long id) {
        return CourseResponse.from(getCourse(id));
    }

    @Transactional(readOnly = true)
    public List<StudentSummary> findStudents(Long courseId) {
        return getCourse(courseId).getStudents().stream()
                .sorted(Comparator.comparing(Student::getLastName).thenComparing(Student::getFirstName))
                .map(StudentSummary::from)
                .toList();
    }

    public CourseResponse create(CourseRequest request) {
        String code = request.code().trim();
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("code", "A course with code " + code + " already exists");
        }
        Course course = new Course(code, request.title().trim(), trimToNull(request.description()),
                request.credits(), request.capacity());
        return CourseResponse.from(courseRepository.save(course));
    }

    public CourseResponse update(Long id, CourseRequest request) {
        Course course = getCourse(id);
        String code = request.code().trim();
        if (courseRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("code", "A course with code " + code + " already exists");
        }
        int enrolled = course.getStudents().size();
        if (request.capacity() < enrolled) {
            throw new BusinessRuleException("Capacity cannot be lower than the " + enrolled
                    + " students already enrolled");
        }
        course.setCode(code);
        course.setTitle(request.title().trim());
        course.setDescription(trimToNull(request.description()));
        course.setCredits(request.credits());
        course.setCapacity(request.capacity());
        return CourseResponse.from(course);
    }

    /** Deletes the course and drops it from every enrolled student in one transaction. */
    public void delete(Long id) {
        Course course = getCourse(id);
        for (Student student : Set.copyOf(course.getStudents())) {
            student.unenroll(course);
        }
        courseRepository.delete(course);
    }

    private Course getCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
