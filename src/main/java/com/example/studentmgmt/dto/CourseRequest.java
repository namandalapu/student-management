package com.example.studentmgmt.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CourseRequest(
        @NotBlank(message = "Course code is required")
        @Pattern(regexp = "^[A-Z]{2,4}[0-9]{3}$", message = "Course code must be 2-4 uppercase letters followed by 3 digits, e.g. CS101")
        String code,

        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title must be at most 100 characters")
        String title,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotNull(message = "Credits are required")
        @Min(value = 1, message = "Credits must be at least 1")
        @Max(value = 6, message = "Credits must be at most 6")
        Integer credits,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 500, message = "Capacity must be at most 500")
        Integer capacity) {
}
