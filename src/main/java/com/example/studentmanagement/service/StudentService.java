package com.example.studentmanagement.service;

import com.example.studentmanagement.dto.StudentRequest;
import com.example.studentmanagement.dto.StudentResponse;
import com.example.studentmanagement.entity.Course;
import com.example.studentmanagement.entity.Student;
import com.example.studentmanagement.exception.BusinessRuleException;
import com.example.studentmanagement.exception.DuplicateResourceException;
import com.example.studentmanagement.exception.ResourceNotFoundException;
import com.example.studentmanagement.repository.CourseRepository;
import com.example.studentmanagement.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business logic for students and their course enrollments.
 *
 * <p>Every public method runs in its own transaction (class-level {@code @Transactional}): if any step throws,
 * all changes made by that call are rolled back. Entities never leave this class; callers get response DTOs
 * that are built while the transaction is still open, so lazy collections can be read safely.
 *
 * <p>Errors are reported with exceptions that {@code GlobalExceptionHandler} turns into HTTP responses:
 * {@link ResourceNotFoundException} (404), {@link DuplicateResourceException} and {@link BusinessRuleException}
 * (both 409).
 */
@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;

    public StudentService(StudentRepository studentRepository, CourseRepository courseRepository) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Lists students sorted by last name, then first name.
     *
     * @param search optional text matched case-insensitively against first name, last name, and email;
     *               {@code null} or blank returns every student
     * @return matching students, each with their enrolled courses
     */
    @Transactional(readOnly = true)
    public List<StudentResponse> findAll(String search) {
        List<Student> students = (search == null || search.isBlank())
                ? studentRepository.findAllByOrderByLastNameAscFirstNameAsc()
                : studentRepository.search(search.trim());
        return students.stream().map(StudentResponse::from).toList();
    }

    /**
     * Gets one student with their enrolled courses.
     *
     * @throws ResourceNotFoundException if no student has this id
     */
    @Transactional(readOnly = true)
    public StudentResponse findById(Long id) {
        return StudentResponse.from(getStudent(id));
    }

    /**
     * Creates a student and, optionally, enrolls them in courses, all in one transaction.
     *
     * <p>Names are trimmed and the email is trimmed and lowercased before saving. If any requested course is
     * missing or full, the whole call fails and the student is not created.
     *
     * @param request the student's details; {@code courseIds} may be {@code null} or empty for no enrollments
     * @return the saved student, including the generated id
     * @throws DuplicateResourceException if another student already uses the email (case-insensitive)
     * @throws ResourceNotFoundException  if a requested course doesn't exist
     * @throws BusinessRuleException      if a requested course is full
     */
    public StudentResponse create(StudentRequest request) {
        String email = normalizeEmail(request.email());
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("email", "A student with email " + email + " already exists");
        }
        Student student = new Student(request.firstName().trim(), request.lastName().trim(), email,
                request.dateOfBirth());
        syncCourses(student, request.courseIds());
        return StudentResponse.from(studentRepository.save(student));
    }

    /**
     * Updates a student's details and, if {@code courseIds} is given, replaces their enrollments.
     *
     * <p>{@code courseIds == null} leaves enrollments as they are; an empty set drops every course. Changes
     * are saved when the transaction commits (the student is a managed entity, so no explicit save is needed).
     *
     * @return the updated student
     * @throws ResourceNotFoundException  if the student, or a requested course, doesn't exist
     * @throws DuplicateResourceException if a different student already uses the email
     * @throws BusinessRuleException      if a newly requested course is full
     */
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = getStudent(id);
        String email = normalizeEmail(request.email());
        if (studentRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateResourceException("email", "A student with email " + email + " already exists");
        }
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setEmail(email);
        student.setDateOfBirth(request.dateOfBirth());
        // A null list leaves enrollments alone; an empty list clears them.
        if (request.courseIds() != null) {
            syncCourses(student, request.courseIds());
        }
        return StudentResponse.from(student);
    }

    /**
     * Deletes a student. Their enrollments are removed first, in the same transaction, so each course's
     * enrolled count drops accordingly.
     *
     * @throws ResourceNotFoundException if no student has this id
     */
    public void delete(Long id) {
        Student student = getStudent(id);
        // Copy the set: unenroll() modifies student.getCourses() while we loop.
        for (Course course : Set.copyOf(student.getCourses())) {
            student.unenroll(course);
        }
        studentRepository.delete(student);
    }

    /**
     * Enrolls a student in one course.
     *
     * <p>The course row is locked first, so two requests racing for the last seat can't both succeed.
     *
     * @return the student with their updated course list
     * @throws ResourceNotFoundException if the student or course doesn't exist
     * @throws BusinessRuleException     if the student is already enrolled or the course is full
     */
    public StudentResponse enroll(Long studentId, Long courseId) {
        Student student = getStudent(studentId);
        Course course = getCourseForEnrollment(courseId);
        if (student.getCourses().contains(course)) {
            throw new BusinessRuleException("Student is already enrolled in " + course.getCode());
        }
        ensureNotFull(course);
        student.enroll(course);
        return StudentResponse.from(student);
    }

    /**
     * Drops a student from one course.
     *
     * @return the student with their updated course list
     * @throws ResourceNotFoundException if the student or course doesn't exist
     * @throws BusinessRuleException     if the student isn't enrolled in that course
     */
    public StudentResponse unenroll(Long studentId, Long courseId) {
        Student student = getStudent(studentId);
        Course course = getCourse(courseId);
        if (!student.getCourses().contains(course)) {
            throw new BusinessRuleException("Student is not enrolled in " + course.getCode());
        }
        student.unenroll(course);
        return StudentResponse.from(student);
    }

    /**
     * Makes the student's enrollments match {@code courseIds}. Runs inside the caller's transaction, so if any
     * course is missing or full, nothing about the student (including a new student) is saved.
     *
     * <p>Courses the student already has are kept without a capacity check, so saving a student who is in a
     * full course doesn't fail.
     */
    private void syncCourses(Student student, Set<Long> courseIds) {
        Set<Long> wanted = courseIds == null ? Set.of() : new HashSet<>(courseIds);
        // First drop courses that are no longer wanted (frees their seats).
        for (Course course : Set.copyOf(student.getCourses())) {
            if (!wanted.contains(course.getId())) {
                student.unenroll(course);
            }
        }
        // Then add new ones. Sorted so concurrent requests always lock courses in the same order (no deadlocks).
        for (Long courseId : wanted.stream().sorted().toList()) {
            Course course = getCourseForEnrollment(courseId);
            if (!student.getCourses().contains(course)) {
                ensureNotFull(course);
                student.enroll(course);
            }
        }
    }

    /** Throws {@link BusinessRuleException} if the course has no free seats. */
    private void ensureNotFull(Course course) {
        if (course.isFull()) {
            throw new BusinessRuleException("Course " + course.getCode() + " is full (capacity "
                    + course.getCapacity() + ")");
        }
    }

    /** Loads a student or throws {@link ResourceNotFoundException}. */
    private Student getStudent(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    /** Loads a course without locking it (for reads and unenrolling, which never overbook). */
    private Course getCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    /** Locks the course row so the capacity check and the enrollment happen atomically. */
    private Course getCourseForEnrollment(Long id) {
        return courseRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    /** Emails are stored trimmed and lowercased, so uniqueness checks aren't fooled by case or spaces. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
