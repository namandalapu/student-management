package com.example.studentmgmt.dto;

import com.example.studentmgmt.entity.Course;

public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        Integer credits,
        Integer capacity,
        int enrolledCount) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(course.getId(), course.getCode(), course.getTitle(), course.getDescription(),
                course.getCredits(), course.getCapacity(), course.getStudents().size());
    }
}
