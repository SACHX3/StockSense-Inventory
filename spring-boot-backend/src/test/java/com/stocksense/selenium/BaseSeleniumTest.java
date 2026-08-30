package com.stocksense.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Locale;

/**
 * Base class for all Selenium browser (end-to-end / cross-browser compatibility) tests.
 *
 * Boots the full Spring application on a random port (SpringBootTest.WebEnvironment.RANDOM_PORT)
 * and drives it with a real browser via Selenium WebDriver.
 *
 * Subclasses get:
 *  - a shared WebDriver instance (`driver`)
 *  - a `baseUrl()` helper that points at the running app
 *  - a `login(username, password)` helper for the common "sign in first" step
 *  - a `waitVisible(By)` helper for explicit waits
 *
 * CROSS-BROWSER COMPATIBILITY
 * ----------------------------
 * The browser is chosen at runtime from the `selenium.browser` system property
 * (case-insensitive), defaulting to "chrome". Same test code runs unmodified
 * against whichever browser you pick - that's the actual compatibility check:
 * if a test that passes on Chrome fails on Firefox or Edge, that's a real
 * cross-browser bug (CSS rendering difference, a JS API Chrome tolerates but
 * Firefox doesn't, etc.), not a flaky test.
 *
 * Requires the corresponding real browser installed on the machine running the
 * tests (Chrome, Firefox, and/or Edge). WebDriverManager downloads and wires up
 * the matching driver binary automatically for whichever one you select - no
 * manual chromedriver/geckodriver/msedgedriver setup needed.
 *
 * Run against a single browser:
 *   mvn test -Dtest=*SeleniumTest -Dselenium.browser=chrome
 *   mvn test -Dtest=*SeleniumTest -Dselenium.browser=firefox
 *   mvn test -Dtest=*SeleniumTest -Dselenium.browser=edge
 *
 * Run against all three in one go (see README-SELENIUM.md for the exact command
 * and how to fold it into CI as a matrix):
 *   for b in chrome firefox edge; do mvn test -Dtest=*SeleniumTest -Dselenium.browser=$b; done
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseSeleniumTest {

    @LocalServerPort
    protected int port;

    protected static WebDriver driver;
    protected WebDriverWait wait;

    @BeforeAll
    static void setUpClass() {
        String browser = System.getProperty("selenium.browser", "chrome").toLowerCase(Locale.ROOT);
        boolean headless = !"false".equalsIgnoreCase(System.getProperty("selenium.headless", "true"));

        switch (browser) {
            case "firefox" -> {
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions options = new FirefoxOptions();
                if (headless) options.addArguments("-headless");
                options.addArguments("--width=1440", "--height=900");
                driver = new FirefoxDriver(options);
            }
            case "edge" -> {
                WebDriverManager.edgedriver().setup();
                EdgeOptions options = new EdgeOptions();
                if (headless) options.addArguments("--headless=new");
                options.addArguments("--window-size=1440,900");
                options.addArguments("--disable-gpu");
                driver = new EdgeDriver(options);
            }
            case "chrome" -> {
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                if (headless) options.addArguments("--headless=new");
                options.addArguments("--window-size=1440,900");
                options.addArguments("--disable-gpu");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                driver = new ChromeDriver(options);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown -Dselenium.browser value: '" + browser
                            + "'. Expected one of: chrome, firefox, edge.");
        }

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
    }

    @AfterEach
    void resetSession() {
        // Clear cookies between tests so each test starts logged out,
        // without paying the cost of restarting the browser every time.
        driver.manage().deleteAllCookies();
    }

    @AfterAll
    static void tearDownClass() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected WebDriverWait waitFor() {
        if (wait == null) {
            wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        }
        return wait;
    }

    protected WebElement waitVisible(By locator) {
        return waitFor().until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement waitClickable(By locator) {
        return waitFor().until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Logs in through the real /login form (username/password fields + submit button,
     * matching SecurityConfig's formLogin field names) and waits for the redirect to
     * /dashboard to complete. Seeded users (see DataInitializer): admin/admin123,
     * manager/admin123, staff1/admin123.
     */
    protected void login(String username, String password) {
        driver.get(baseUrl() + "/login");
        waitVisible(By.id("username")).sendKeys(username);
        driver.findElement(By.id("passwordField")).sendKeys(password);
        driver.findElement(By.cssSelector("button.login-btn-submit")).click();
        waitFor().until(ExpectedConditions.urlContains("/dashboard"));
    }

    protected void loginAsAdmin() {
        login("admin", "admin123");
    }
}
