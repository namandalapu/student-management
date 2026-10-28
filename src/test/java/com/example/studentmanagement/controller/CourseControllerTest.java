package com.example.studentmanagement.controller;

import com.example.studentmanagement.dto.CourseRequest;
import com.example.studentmanagement.dto.CourseResponse;
import com.example.studentmanagement.dto.StudentSummary;
import com.example.studentmanagement.exception.BusinessRuleException;
import com.example.studentmanagement.exception.DuplicateResourceException;
import com.example.studentmanagement.exception.ResourceNotFoundException;
import com.example.studentmanagement.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;

    private static final CourseResponse CS101 =
            new CourseResponse(1L, "CS101", "Intro to Programming", "Basics", 4, 30, 2);

    private static final String VALID_BODY = """
            {"code":"CS101","title":"Intro to Programming","description":"Basics","credits":4,"capacity":30}
            """;

    @Test
    void listReturnsCourses() throws Exception {
        when(courseService.findAll()).thenReturn(List.of(CS101));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CS101"))
                .andExpect(jsonPath("$[0].enrolledCount").value(2));
    }

    @Test
    void getMissingCourseReturns404() throws Exception {
        when(courseService.findById(9L)).thenThrow(new ResourceNotFoundException("Course", 9L));

        mockMvc.perform(get("/api/courses/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Course with id 9 was not found"));
    }

    @Test
    void rosterReturnsStudents() throws Exception {
        when(courseService.findStudents(1L))
                .thenReturn(List.of(new StudentSummary(5L, "Ada", "Lovelace", "ada@example.edu")));

        mockMvc.perform(get("/api/courses/1/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ada@example.edu"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(courseService.create(any(CourseRequest.class))).thenReturn(CS101);

        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/courses/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"cs-1","title":"","credits":0,"capacity":501}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.credits").value("Credits must be at least 1"))
                .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at most 500"));
        verifyNoInteractions(courseService);
    }

    @Test
    void duplicateCodeReturns409WithFieldError() throws Exception {
        when(courseService.create(any(CourseRequest.class)))
                .thenThrow(new DuplicateResourceException("code", "A course with code CS101 already exists"));

        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.code").value("A course with code CS101 already exists"));
    }

    @Test
    void capacityBelowEnrollmentReturns409() throws Exception {
        when(courseService.update(eq(1L), any(CourseRequest.class)))
                .thenThrow(new BusinessRuleException("Capacity cannot be lower than the 2 students already enrolled"));

        mockMvc.perform(put("/api/courses/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already enrolled")));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/courses/1")).andExpect(status().isNoContent());
        verify(courseService).delete(1L);
    }

    @Test
    void deleteMissingCourseReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Course", 9L)).when(courseService).delete(9L);

        mockMvc.perform(delete("/api/courses/9")).andExpect(status().isNotFound());
    }
}
