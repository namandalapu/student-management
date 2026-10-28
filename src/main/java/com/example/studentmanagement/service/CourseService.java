package com.example.studentmanagement.service;

import com.example.studentmanagement.dto.CourseRequest;
import com.example.studentmanagement.dto.CourseResponse;
import com.example.studentmanagement.dto.StudentSummary;
import com.example.studentmanagement.entity.Course;
import com.example.studentmanagement.entity.Student;
import com.example.studentmanagement.exception.BusinessRuleException;
import com.example.studentmanagement.exception.DuplicateResourceException;
import com.example.studentmanagement.exception.ResourceNotFoundException;
import com.example.studentmanagement.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Business logic for courses and their rosters.
 *
 * <p>Every public method runs in its own transaction (class-level {@code @Transactional}), so a failure rolls
 * back everything that call changed. Enrolling and dropping students lives in {@link StudentService}, because
 * {@code Student} owns the enrollment relationship.
 *
 * <p>Errors are reported with exceptions that {@code GlobalExceptionHandler} turns into HTTP responses:
 * {@link ResourceNotFoundException} (404), {@link DuplicateResourceException} and {@link BusinessRuleException}
 * (both 409).
 */
@Service
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /** Lists every course, sorted by course code, with its current enrolled count. */
    @Transactional(readOnly = true)
    public List<CourseResponse> findAll() {
        return courseRepository.findAllByOrderByCodeAsc().stream().map(CourseResponse::from).toList();
    }

    /**
     * Gets one course with its enrolled count.
     *
     * @throws ResourceNotFoundException if no course has this id
     */
    @Transactional(readOnly = true)
    public CourseResponse findById(Long id) {
        return CourseResponse.from(getCourse(id));
    }

    /**
     * Gets the course roster: the students enrolled in it, sorted by last name, then first name.
     *
     * @throws ResourceNotFoundException if no course has this id
     */
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudents(Long courseId) {
        return getCourse(courseId).getStudents().stream()
                .sorted(Comparator.comparing(Student::getLastName).thenComparing(Student::getFirstName))
                .map(StudentSummary::from)
                .toList();
    }

    /**
     * Creates a course. Code and title are trimmed; a blank description is stored as {@code null}.
     *
     * @return the saved course, including the generated id
     * @throws DuplicateResourceException if another course already uses the code (case-insensitive)
     */
    public CourseResponse create(CourseRequest request) {
        String code = request.code().trim();
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("code", "A course with code " + code + " already exists");
        }
        Course course = new Course(code, request.title().trim(), trimToNull(request.description()),
                request.credits(), request.capacity());
        return CourseResponse.from(courseRepository.save(course));
    }

    /**
     * Updates a course. Changes are saved when the transaction commits (the course is a managed entity).
     *
     * @return the updated course
     * @throws ResourceNotFoundException  if no course has this id
     * @throws DuplicateResourceException if a different course already uses the code
     * @throws BusinessRuleException      if the new capacity is lower than the number of students enrolled
     */
    public CourseResponse update(Long id, CourseRequest request) {
        // Locked so a concurrent enrollment can't slip in between the capacity check and the save.
        Course course = courseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
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

    /**
     * Deletes the course and drops it from every enrolled student in one transaction.
     *
     * @throws ResourceNotFoundException if no course has this id
     */
    public void delete(Long id) {
        Course course = getCourse(id);
        // Student owns the join table, so remove the rows from the student side. Copy the set because
        // unenroll() modifies course.getStudents() while we loop.
        for (Student student : Set.copyOf(course.getStudents())) {
            student.unenroll(course);
        }
        courseRepository.delete(course);
    }

    /** Loads a course or throws {@link ResourceNotFoundException}. */
    private Course getCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    /** Returns the trimmed value, or {@code null} if it is {@code null} or only whitespace. */
    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
