package com.example.studentmanagement.dto;

import com.example.studentmanagement.entity.Student;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record StudentResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        List<CourseSummary> courses) {

    public static StudentResponse from(Student student) {
        List<CourseSummary> courses = student.getCourses().stream()
                .map(CourseSummary::from)
                .sorted(Comparator.comparing(CourseSummary::code))
                .toList();
        return new StudentResponse(student.getId(), student.getFirstName(), student.getLastName(),
                student.getEmail(), student.getDateOfBirth(), courses);
    }
}
