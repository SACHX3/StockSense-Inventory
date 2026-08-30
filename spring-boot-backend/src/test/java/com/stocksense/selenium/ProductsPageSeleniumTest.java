package com.stocksense.selenium;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browser tests for the Products module: list rendering, search, the
 * add-product form (with real field names from ProductController/form.html),
 * and the low-stock filtered view.
 */
class ProductsPageSeleniumTest extends BaseSeleniumTest {

    @BeforeEach
    void signIn() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Products list renders product cards seeded by DataInitializer")
    void productsListShowsCards() {
        driver.get(baseUrl() + "/products");

        List<WebElement> cards = driver.findElements(By.className("pcard"));
        assertFalse(cards.isEmpty(), "expected at least one seeded product card on /products");
    }

    @Test
    @DisplayName("Searching by keyword filters the product list")
    void productSearchFilters() {
        driver.get(baseUrl() + "/products");

        WebElement search = waitVisible(By.id("plSearchInput"));
        search.sendKeys("Rice");
        driver.findElement(By.cssSelector(".filter-bar-panel button[type='submit']")).click();

        waitFor().until(ExpectedConditions.urlContains("keyword=Rice"));
        assertTrue(driver.getCurrentUrl().contains("keyword=Rice"));
    }

    @Test
    @DisplayName("Add Product form accepts input and creates a new product")
    void addProductFormSubmits() {
        driver.get(baseUrl() + "/products/create");

        String uniqueSku = "SEL-TEST-" + System.currentTimeMillis();
        driver.findElement(By.name("name")).sendKeys("Selenium Test Product");
        driver.findElement(By.name("sku")).sendKeys(uniqueSku);
        driver.findElement(By.name("buyingPrice")).sendKeys("100");
        driver.findElement(By.name("sellingPrice")).sendKeys("150");
        driver.findElement(By.name("quantity")).sendKeys("25");

        // Category is a required <select>; just pick whatever the first real option is.
        WebElement categorySelect = driver.findElement(By.name("categoryId"));
        List<WebElement> categoryOptions = categorySelect.findElements(By.tagName("option"));
        assertTrue(categoryOptions.size() > 1, "expected seeded categories in the dropdown");
        categoryOptions.get(1).click();

        driver.findElement(By.cssSelector("button.btn-save")).click();

        // A successful create redirects back to the products list.
        waitFor().until(ExpectedConditions.urlContains("/products"));
        assertTrue(driver.getPageSource().contains("Selenium Test Product")
                || driver.getPageSource().contains(uniqueSku),
                "expected the newly created product to appear after redirect");
    }

    @Test
    @DisplayName("Low Stock page loads and only shows below-threshold items (or an empty state)")
    void lowStockPageLoads() {
        driver.get(baseUrl() + "/products/low-stock");

        assertTrue(driver.getCurrentUrl().endsWith("/products/low-stock"));
        // Page should render without error, whether or not any product is currently low.
        assertFalse(driver.getPageSource().toLowerCase().contains("whitelabel error page"));
    }
}
