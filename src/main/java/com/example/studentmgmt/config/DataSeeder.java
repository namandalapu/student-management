package com.example.studentmgmt.config;

import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.StudentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Loads a few sample records the first time the app starts against an empty database. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;

    public DataSeeder(StudentRepository studentRepository, CourseRepository courseRepository) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (studentRepository.count() > 0 || courseRepository.count() > 0) {
            return;
        }
        List<Course> courses = courseRepository.saveAll(List.of(
                new Course("CS101", "Introduction to Programming", "Fundamentals of programming in Java.", 4, 30),
                new Course("CS201", "Data Structures", "Lists, trees, graphs and hash tables.", 4, 25),
                new Course("MATH150", "Calculus I", "Limits, derivatives and integrals.", 3, 40),
                new Course("ENG110", "Academic Writing", "Writing clear, well-argued essays.", 3, 20),
                new Course("PHYS120", "Physics I", "Mechanics and motion.", 4, 2)));

        Student ada = new Student("Ada", "Lovelace", "ada.lovelace@example.edu", LocalDate.of(2004, 12, 10));
        Student alan = new Student("Alan", "Turing", "alan.turing@example.edu", LocalDate.of(2003, 6, 23));
        Student grace = new Student("Grace", "Hopper", "grace.hopper@example.edu", LocalDate.of(2005, 12, 9));
        Student katherine = new Student("Katherine", "Johnson", "katherine.johnson@example.edu",
                LocalDate.of(2004, 8, 26));

        ada.enroll(courses.get(0));
        ada.enroll(courses.get(2));
        alan.enroll(courses.get(0));
        alan.enroll(courses.get(1));
        alan.enroll(courses.get(4));
        grace.enroll(courses.get(1));
        grace.enroll(courses.get(4));
        katherine.enroll(courses.get(2));

        studentRepository.saveAll(List.of(ada, alan, grace, katherine));
    }
}
