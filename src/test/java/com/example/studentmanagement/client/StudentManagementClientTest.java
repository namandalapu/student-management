package com.example.studentmanagement.client;

import com.example.studentmanagement.dto.StudentRequest;
import com.example.studentmanagement.dto.StudentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link StudentManagementClient}: the RestTemplate is pointed at a mock server, so these check
 * the requests the client sends and how it reads responses and errors, without starting the application.
 */
class StudentManagementClientTest {

    private MockRestServiceServer server;
    private StudentManagementClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().rootUri("http://api.test").build();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new StudentManagementClient(restTemplate);
    }

    @Test
    void getStudentReadsTheResponse() {
        server.expect(requestTo("http://api.test/api/students/1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":1,"firstName":"Ada","lastName":"Lovelace","email":"ada@example.edu",
                         "dateOfBirth":"2004-12-10","courses":[{"id":3,"code":"CS101","title":"Intro","credits":4}]}
                        """, MediaType.APPLICATION_JSON));

        StudentResponse ada = client.getStudent(1);

        assertThat(ada.lastName()).isEqualTo("Lovelace");
        assertThat(ada.dateOfBirth()).hasToString("2004-12-10");
        assertThat(ada.courses()).extracting("code").containsExactly("CS101");
        server.verify();
    }

    @Test
    void searchTermIsUrlEncoded() {
        server.expect(requestTo("http://api.test/api/students?search=ada%20%26%20alan"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.searchStudents("ada & alan")).isEmpty();
        server.verify();
    }

    @Test
    void createStudentPostsJson() {
        server.expect(requestTo("http://api.test/api/students")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.edu","courseIds":[3]}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"id":7,"firstName":"Ada","lastName":"Lovelace","email":"ada@example.edu","courses":[]}
                        """));

        StudentResponse created = client.createStudent(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, Set.of(3L)));

        assertThat(created.id()).isEqualTo(7L);
        server.verify();
    }

    @Test
    void errorResponsesBecomeApiClientExceptionWithTheServerMessageAndFieldErrors() {
        server.expect(requestTo("http://api.test/api/students")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"status":409,"error":"Conflict","message":"A student with email ada@example.edu already exists",
                         "path":"/api/students","fieldErrors":{"email":"A student with email ada@example.edu already exists"}}
                        """));

        assertThatThrownBy(() -> client.createStudent(
                new StudentRequest("Ada", "Lovelace", "ada@example.edu", null, null)))
                .isInstanceOfSatisfying(ApiClientException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("A student with email ada@example.edu already exists");
                    assertThat(ex.getFieldErrors()).containsKey("email");
                });
    }

    @Test
    void errorWithoutAJsonBodyStillReportsTheStatus() {
        server.expect(requestTo("http://api.test/api/courses/5")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.deleteCourse(5))
                .isInstanceOfSatisfying(ApiClientException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getError()).isNull();
                    assertThat(ex.getFieldErrors()).isEmpty();
                    assertThat(ex.getMessage()).contains("502");
                });
    }
}
