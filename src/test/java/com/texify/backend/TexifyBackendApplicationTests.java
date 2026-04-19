package com.texify.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies that the Spring application context loads successfully.
 * <p>
 * Uses the {@code test} profile to replace MySQL with an in-memory H2 database.
 * If any bean fails to initialise, this test reports a descriptive startup error.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TexifyBackendApplicationTests {

    /**
     * A successful context start is the only assertion needed here.
     * Bean initialisation failures will surface as test errors automatically.
     */
    @Test
    void contextLoads() {
    }
}
