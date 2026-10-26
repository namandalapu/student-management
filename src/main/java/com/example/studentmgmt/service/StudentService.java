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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;

    public StudentService(StudentRepository studentRepository, CourseRepository courseRepository) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> findAll(String search) {
        List<Student> students = (search == null || search.isBlank())
                ? studentRepository.findAllByOrderByLastNameAscFirstNameAsc()
                : studentRepository.search(search.trim());
        return students.stream().map(StudentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse findById(Long id) {
        return StudentResponse.from(getStudent(id));
    }

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

    public void delete(Long id) {
        Student student = getStudent(id);
        for (Course course : Set.copyOf(student.getCourses())) {
            student.unenroll(course);
        }
        studentRepository.delete(student);
    }

    public StudentResponse enroll(Long studentId, Long courseId) {
        Student student = getStudent(studentId);
        Course course = getCourse(courseId);
        if (student.getCourses().contains(course)) {
            throw new BusinessRuleException("Student is already enrolled in " + course.getCode());
        }
        ensureNotFull(course);
        student.enroll(course);
        return StudentResponse.from(student);
    }

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
     */
    private void syncCourses(Student student, Set<Long> courseIds) {
        Set<Long> wanted = courseIds == null ? Set.of() : new HashSet<>(courseIds);
        for (Course course : Set.copyOf(student.getCourses())) {
            if (!wanted.contains(course.getId())) {
                student.unenroll(course);
            }
        }
        for (Long courseId : wanted) {
            Course course = getCourse(courseId);
            if (!student.getCourses().contains(course)) {
                ensureNotFull(course);
                student.enroll(course);
            }
        }
    }

    private void ensureNotFull(Course course) {
        if (course.isFull()) {
            throw new BusinessRuleException("Course " + course.getCode() + " is full (capacity "
                    + course.getCapacity() + ")");
        }
    }

    private Student getStudent(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    private Course getCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
