package com.example.studentmanagement.controller;

import com.example.studentmanagement.dto.CourseSummary;
import com.example.studentmanagement.dto.StudentRequest;
import com.example.studentmanagement.dto.StudentResponse;
import com.example.studentmanagement.exception.BusinessRuleException;
import com.example.studentmanagement.exception.DuplicateResourceException;
import com.example.studentmanagement.exception.ResourceNotFoundException;
import com.example.studentmanagement.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
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

@WebMvcTest(StudentController.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    private static final StudentResponse ADA = new StudentResponse(1L, "Ada", "Lovelace", "ada@example.edu",
            LocalDate.of(2004, 12, 10), List.of(new CourseSummary(3L, "CS101", "Intro", 4)));

    @Test
    void listReturnsStudents() throws Exception {
        when(studentService.findAll(null)).thenReturn(List.of(ADA));

        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ada@example.edu"))
                .andExpect(jsonPath("$[0].dateOfBirth").value("2004-12-10"))
                .andExpect(jsonPath("$[0].courses[0].code").value("CS101"));
    }

    @Test
    void listPassesSearchTerm() throws Exception {
        when(studentService.findAll("ada")).thenReturn(List.of(ADA));

        mockMvc.perform(get("/api/students").param("search", "ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(studentService.create(any(StudentRequest.class))).thenReturn(ADA);

        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.edu",
                         "dateOfBirth":"2004-12-10","courseIds":[3]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/students/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"","lastName":"Lovelace","email":"not-an-email","dateOfBirth":"2999-01-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth must be in the past"));
        verifyNoInteractions(studentService);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(studentService.create(any(StudentRequest.class)))
                .thenThrow(new DuplicateResourceException("email", "A student with email ada@example.edu already exists"));

        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.edu"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void getMissingStudentReturns404() throws Exception {
        when(studentService.findById(9L)).thenThrow(new ResourceNotFoundException("Student", 9L));

        mockMvc.perform(get("/api/students/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Student with id 9 was not found"))
                .andExpect(jsonPath("$.path").value("/api/students/9"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/students/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDelegatesToService() throws Exception {
        when(studentService.update(eq(1L), any(StudentRequest.class))).thenReturn(ADA);

        mockMvc.perform(put("/api/students/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.edu"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/students/1")).andExpect(status().isNoContent());
        verify(studentService).delete(1L);
    }

    @Test
    void enrollInFullCourseReturns409() throws Exception {
        doThrow(new BusinessRuleException("Course CS101 is full (capacity 1)"))
                .when(studentService).enroll(1L, 3L);

        mockMvc.perform(put("/api/students/1/courses/3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Course CS101 is full (capacity 1)"));
    }

    @Test
    void unsupportedContentTypeReturns415NotA500() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        verifyNoInteractions(studentService);
    }

    @Test
    void nullCourseIdReturns400() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.edu","courseIds":[null]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
        verifyNoInteractions(studentService);
    }
}
