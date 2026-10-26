package com.example.studentmgmt.dto;

import com.example.studentmgmt.entity.Student;

public record StudentSummary(Long id, String firstName, String lastName, String email) {

    public static StudentSummary from(Student student) {
        return new StudentSummary(student.getId(), student.getFirstName(), student.getLastName(), student.getEmail());
    }
}
