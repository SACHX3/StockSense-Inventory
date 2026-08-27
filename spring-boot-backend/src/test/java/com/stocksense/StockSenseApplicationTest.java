package com.stocksense;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies the Spring application context loads successfully.
 * Uses H2 in-memory database so no MySQL needed in CI.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class StockSenseApplicationTest {

    @Test
    void contextLoads() {
        // If this test passes, the Spring context loaded without errors.
        // That means all beans, configs, JPA entities, and repositories are valid.
    }
}
