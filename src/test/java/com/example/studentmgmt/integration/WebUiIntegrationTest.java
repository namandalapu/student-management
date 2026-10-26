package com.example.studentmgmt.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks that the Angular UI built into classpath:/static is served by the same Spring Boot app,
 * and that client-side routes fall back to index.html while API and file 404s stay 404s.
 *
 * <p>Skipped when the UI was not built (for example {@code mvnw test -Dskip.npm -Dskip.installnodenpm}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class WebUiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @BeforeAll
    static void requireBuiltUi() {
        assumeTrue(new ClassPathResource("static/index.html").exists(),
                "Angular UI not built; skipping UI serving tests");
    }

    @Test
    void rootServesTheAngularApp() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody()).contains("<app-root");
    }

    @Test
    void deepLinksFallBackToIndexHtml() {
        for (String path : new String[] {"/students", "/students/42/edit", "/courses/new", "/no-such-page"}) {
            ResponseEntity<String> response = rest.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(path).contains("<app-root");
        }
    }

    @Test
    void unknownApiPathsStillReturnJson404() {
        ResponseEntity<String> response = rest.getForEntity("/api/does-not-exist", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).doesNotContain("<app-root").contains("\"status\":404");
    }

    @Test
    void missingStaticFilesReturn404() {
        ResponseEntity<String> response = rest.getForEntity("/missing-file.js", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
