package com.stocksense.selenium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browser tests for the /login page: valid sign-in, invalid credentials,
 * and the "already authenticated" redirect.
 */
class LoginPageSeleniumTest extends BaseSeleniumTest {

    @Test
    @DisplayName("Login page renders the username/password form")
    void loginPageRenders() {
        driver.get(baseUrl() + "/login");

        assertTrue(driver.findElement(By.id("username")).isDisplayed());
        assertTrue(driver.findElement(By.id("passwordField")).isDisplayed());
        assertTrue(driver.findElement(By.cssSelector("button.login-btn-submit")).isDisplayed());
    }

    @Test
    @DisplayName("Valid admin credentials sign in and land on the dashboard")
    void validLoginRedirectsToDashboard() {
        loginAsAdmin();

        assertTrue(driver.getCurrentUrl().contains("/dashboard"));
        assertTrue(driver.getTitle().startsWith("Dashboard"));
    }

    @Test
    @DisplayName("Invalid credentials show the login failure alert and stay on /login")
    void invalidLoginShowsError() {
        driver.get(baseUrl() + "/login");
        driver.findElement(By.id("username")).sendKeys("admin");
        driver.findElement(By.id("passwordField")).sendKeys("wrong-password");
        driver.findElement(By.cssSelector("button.login-btn-submit")).click();

        waitFor().until(ExpectedConditions.urlContains("error=true"));
        WebElement alert = waitVisible(By.cssSelector(".login-alert.danger"));
        assertTrue(alert.getText().toLowerCase().contains("invalid"));
    }

    @Test
    @DisplayName("An already-authenticated user hitting /login is redirected away, not shown the login form")
    void alreadyLoggedInRedirectsAwayFromLogin() {
        loginAsAdmin();

        driver.get(baseUrl() + "/login");
        waitFor().until(ExpectedConditions.urlContains("/dashboard"));

        // The app appends its own query params (e.g. ?currentUri=... for sidebar active-state)
        // on this redirect, so assert on the path rather than requiring an exact URL match.
        assertTrue(driver.getCurrentUrl().contains("/dashboard"),
                "expected an already-authenticated user hitting /login to land on /dashboard, was: "
                        + driver.getCurrentUrl());
    }

    @Test
    @DisplayName("Manager and staff demo accounts can also sign in")
    void otherSeededRolesCanLogIn() {
        login("manager", "admin123");
        assertTrue(driver.getCurrentUrl().contains("/dashboard"));
    }
}
