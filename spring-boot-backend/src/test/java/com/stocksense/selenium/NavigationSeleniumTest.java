package com.stocksense.selenium;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-cutting navigation tests: every sidebar-linked route should load without
 * a 404/500, and logging out should actually end the session (protected pages
 * bounce back to /login afterwards).
 */
class NavigationSeleniumTest extends BaseSeleniumTest {

    @BeforeEach
    void signIn() {
        loginAsAdmin();
    }

    @ParameterizedTest(name = "{0} loads without an error page")
    @ValueSource(strings = {
            "/dashboard",
            "/products",
            "/categories",
            "/suppliers",
            "/inventory",
            "/inventory/adjust",
            "/products/low-stock",
            "/sales",
            "/sales/create",
            "/reports",
            "/forecasting",
            "/ocr",
            "/profile"
    })
    @DisplayName("Sidebar route renders successfully")
    void sidebarRouteLoads(String path) {
        driver.get(baseUrl() + path);

        String source = driver.getPageSource().toLowerCase();
        assertFalse(source.contains("whitelabel error page"),
                path + " returned a Spring Boot error page");
        assertFalse(source.contains("http status 500"),
                path + " returned a 500 error");
    }

    @Test
    @DisplayName("Logging out clears the session and protected pages redirect back to /login")
    void logoutEndsSession() {
        driver.get(baseUrl() + "/dashboard");
        driver.findElement(By.cssSelector("a.logout-btn")).click();

        waitFor().until(d -> d.getCurrentUrl().contains("/login"));
        assertTrue(driver.getCurrentUrl().contains("logout=true")
                || driver.getCurrentUrl().endsWith("/login"));

        // A protected page should now bounce back to /login instead of loading.
        driver.get(baseUrl() + "/dashboard");
        waitFor().until(d -> d.getCurrentUrl().contains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }
}
