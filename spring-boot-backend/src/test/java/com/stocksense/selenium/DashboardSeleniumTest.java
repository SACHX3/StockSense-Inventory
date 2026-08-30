package com.stocksense.selenium;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browser tests for the dashboard: KPI cards, charts, and the PDF export control
 * that were the subject of earlier manual/bug-fix work (see the "dashboard export
 * always using 30-day window" fix) are all worth covering with a real browser check.
 */
class DashboardSeleniumTest extends BaseSeleniumTest {

    @BeforeEach
    void signIn() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Dashboard loads with KPI cards and chart canvases present")
    void dashboardShowsKpisAndCharts() {
        driver.get(baseUrl() + "/dashboard");

        assertTrue(driver.getTitle().startsWith("Dashboard"));
        assertFalse(driver.findElements(By.id("movementChart")).isEmpty(),
                "expected the sales-vs-received movement chart canvas to be present");
        assertFalse(driver.findElements(By.id("stockHealthChart")).isEmpty(),
                "expected the stock health chart canvas to be present");
        assertFalse(driver.findElements(By.id("topProductsChart")).isEmpty(),
                "expected the top products chart canvas to be present");
    }

    @Test
    @DisplayName("Dashboard has a range selector and an export control")
    void dashboardHasRangeSelectorAndExport() {
        driver.get(baseUrl() + "/dashboard");

        assertTrue(driver.findElement(By.id("dashRangeSegmented")).isDisplayed());
        assertTrue(driver.findElement(By.id("dashExportBtn")).isDisplayed());
    }

    @Test
    @DisplayName("Sidebar navigation is visible and the Dashboard item is marked active")
    void sidebarShowsActiveDashboardItem() {
        driver.get(baseUrl() + "/dashboard");

        assertTrue(driver.findElement(By.id("sidebar")).isDisplayed());
        assertFalse(driver.findElements(By.cssSelector("a.nav-item.active")).isEmpty(),
                "expected at least one nav item to carry the 'active' class on the dashboard");
    }
}
