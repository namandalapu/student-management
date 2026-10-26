package com.example.studentmgmt.dto;

import com.example.studentmgmt.entity.Course;

public record CourseSummary(Long id, String code, String title, Integer credits) {

    public static CourseSummary from(Course course) {
        return new CourseSummary(course.getId(), course.getCode(), course.getTitle(), course.getCredits());
    }
}
