package com.stocksense.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Starts and stops the FastAPI AI service (OCR + demand forecasting) automatically
 * whenever this Spring Boot application starts/stops, so nobody has to remember to
 * run start.bat / start.sh / RUN.bat by hand.
 *
 * Works on both Windows and macOS/Linux:
 *  - Picks "python"/"py" on Windows and "python3"/"python" elsewhere.
 *  - Prefers a project-local virtual environment (fastapi-service/venv) if one exists.
 *  - Locates the fastapi-service folder relative to wherever the JVM was started from.
 *  - Skips startup entirely if something is already answering on the AI service URL
 *    (e.g. you started it manually), so it never launches a duplicate.
 *
 * Disable with: app.ai-service.auto-start=false
 */
@Slf4j
@Component
public class AiServiceProcessManager {

    @Value("${app.ai-service.base-url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${app.ai-service.auto-start:true}")
    private boolean autoStart;

    @Value("${app.ai-service.startup-timeout-seconds:180}")
    private int startupTimeoutSeconds;

    @Value("${app.ai-service.working-dir:}")
    private String configuredWorkingDir;

    @Value("${app.ai-service.python-executable:}")
    private String configuredPythonExecutable;

    private Process process;
    private volatile String lastError;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoStart) {
            log.info("AI service auto-start disabled (app.ai-service.auto-start=false)");
            return;
        }
        // Do the (potentially slow) launch + health-poll off the main thread so it
        // never delays the web app from serving requests.
        Thread launcher = new Thread(this::startAiServiceIfNeeded, "ai-service-launcher");
        launcher.setDaemon(true);
        launcher.start();
    }

    // Public entry point for the manual "Start" button (and for auto-start on boot).
    // Runs synchronously in the caller's thread - the controller wraps the call in a
    // background thread so the HTTP request returns immediately.
    public synchronized boolean startAiServiceIfNeeded() {
        lastError = null;
        if (isServiceHealthy()) {
            log.info("AI service already running at {} - skipping start", aiServiceUrl);
            return true;
        }

        Path fastApiDir = resolveFastApiDir();
        if (fastApiDir == null || !Files.exists(fastApiDir.resolve("main.py"))) {
            lastError = "Could not locate the fastapi-service folder. Start it manually: cd fastapi-service && (start.bat | ./start.sh)";
            log.warn("{} (looked near {})", lastError, Paths.get("").toAbsolutePath());
            return false;
        }

        String pythonExe = resolvePythonExecutable(fastApiDir);
        if (pythonExe == null) {
            lastError = "No Python interpreter found on PATH. Install Python 3.10+, or start the AI service manually.";
            log.warn("{} ({})", lastError, fastApiDir);
            return false;
        }

        try {
            List<String> command = new ArrayList<>();
            Path unixStartupScript = fastApiDir.resolve("start.sh");
            if (!isWindows() && Files.exists(unixStartupScript)) {
                // start.sh creates/reuses the venv and installs dependencies before
                // starting Uvicorn. This is essential on a newly extracted macOS copy.
                command.add("/bin/bash");
                command.add(unixStartupScript.toAbsolutePath().toString());
            } else {
                command.add(pythonExe);
                command.add("-m");
                command.add("uvicorn");
                command.add("main:app");
                command.add("--host");
                command.add("127.0.0.1");
                command.add("--port");
                command.add(extractPort(aiServiceUrl));
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(fastApiDir.toFile());
            pb.redirectErrorStream(true);

            Path logFile = fastApiDir.resolve("ai-service.log");
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

            log.info("Starting AI service: {} (cwd={}, log={})",
                    String.join(" ", command), fastApiDir, logFile);
            process = pb.start();

            boolean ready = waitUntilHealthy(startupTimeoutSeconds);
            if (ready) {
                log.info("AI service is up at {} (OCR + forecasting)", aiServiceUrl);
                return true;
            } else {
                lastError = "AI service did not respond within " + startupTimeoutSeconds + "s. Check " + logFile + " for details.";
                log.warn("{} The app will keep working with fallback forecasts until it's up.", lastError);
                return false;
            }
        } catch (IOException e) {
            lastError = "Failed to start AI service: " + e.getMessage();
            log.warn("{} You can still start it manually from {}: {}", lastError, fastApiDir,
                    isWindows() ? "start.bat" : "./start.sh");
            return false;
        }
    }

    // Stops the AI service on demand (topbar "Stop" button). Separate from the
    // @PreDestroy hook below, which stops it when the whole Spring Boot app shuts down.
    public synchronized boolean stopAiServiceManually() {
        lastError = null;
        if (process == null || !process.isAlive()) {
            lastError = "AI service isn\'t running (or wasn\'t started by this app - it may have been started manually).";
            return false;
        }
        log.info("Stopping AI service (pid {})", process.pid());
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        return true;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isManagedProcessAlive() {
        return process != null && process.isAlive();
    }

    public boolean checkHealthy() {
        return isServiceHealthy();
    }

    @PreDestroy
    public void stop() {
        if (process != null && process.isAlive()) {
            log.info("Stopping AI service (pid {})", process.pid());
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Path resolveFastApiDir() {
        if (configuredWorkingDir != null && !configuredWorkingDir.isBlank()) {
            return Paths.get(configuredWorkingDir).toAbsolutePath().normalize();
        }
        Path cwd = Paths.get("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("fastapi-service"),
                cwd.resolve("../fastapi-service"),
                cwd.resolve("../../fastapi-service")
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.exists(normalized.resolve("main.py"))) {
                return normalized;
            }
        }
        return null;
    }

    // Common install locations to check directly (by file existence) in addition to
    // whatever the JVM's own PATH resolves. This matters most on macOS: an IDE launched
    // from Finder/Dock (Eclipse.app, IntelliJ IDEA.app) does NOT inherit your shell's
    // full PATH (~/.zshrc, ~/.bash_profile), so a python3 that works fine in Terminal
    // can be completely invisible to the JVM's child-process PATH lookup. Checking these
    // well-known paths directly sidesteps that problem entirely.
    private static final String[] MAC_LINUX_PYTHON_PATHS = {
            "/opt/homebrew/bin/python3",                                   // Homebrew on Apple Silicon
            "/usr/local/bin/python3",                                      // Homebrew on Intel Mac / python.org
            "/usr/local/opt/python3/bin/python3",
            "/Library/Frameworks/Python.framework/Versions/Current/bin/python3", // python.org installer
            "/usr/bin/python3",                                            // Apple-bundled / Linux system python
            "/opt/homebrew/bin/python",
            "/usr/local/bin/python",
    };

    private String resolvePythonExecutable(Path fastApiDir) {
        if (configuredPythonExecutable != null && !configuredPythonExecutable.isBlank()) {
            return configuredPythonExecutable;
        }
        boolean windows = isWindows();

        // 1) Prefer a project-local virtual environment if one has already been created
        //    (e.g. by fastapi-service/start.bat or start.sh).
        Path venvPython = windows
                ? fastApiDir.resolve("venv").resolve("Scripts").resolve("python.exe")
                : fastApiDir.resolve("venv").resolve("bin").resolve("python");
        if (Files.exists(venvPython) && venvHasUvicorn(fastApiDir, windows)) {
            return venvPython.toAbsolutePath().toString();
        }
        if (Files.exists(venvPython)) {
            log.warn("Ignoring virtual environment at {} - it exists but has no uvicorn installed "
                    + "(half-finished 'pip install'?). Falling back to a Python on PATH. "
                    + "To repair it, delete the venv folder and re-run start.bat / start.sh.", venvPython);
        }

        // 2) Whatever Python is on the JVM's own PATH.
        String[] candidates = windows ? new String[]{"python", "py"} : new String[]{"python3", "python"};
        for (String candidate : candidates) {
            if (isOnPath(candidate)) {
                return candidate;
            }
        }

        // 3) On macOS/Linux, also check well-known absolute install locations directly -
        //    these don't depend on PATH being inherited at all.
        if (!windows) {
            for (String candidate : MAC_LINUX_PYTHON_PATHS) {
                if (Files.isExecutable(Paths.get(candidate))) {
                    return candidate;
                }
            }
        }

        log.warn("Python not found. Tried PATH ({}) and, on macOS/Linux, these paths: {}",
                String.join(", ", candidates), String.join(", ", MAC_LINUX_PYTHON_PATHS));
        return null;
    }

    /** A venv folder that exists but has no uvicorn in it is worse than no venv at all:
     *  launching it gives "No module named uvicorn" and the AI service dies instantly.
     *  This happens when a 'pip install -r requirements.txt' was interrupted. */
    private boolean venvHasUvicorn(Path fastApiDir, boolean windows) {
        Path venv = fastApiDir.resolve("venv");
        Path sitePackages = windows
                ? venv.resolve("Lib").resolve("site-packages")
                : venv.resolve("lib");
        if (Files.isDirectory(sitePackages.resolve("uvicorn"))) {
            return true;
        }
        Path scripts = windows ? venv.resolve("Scripts") : venv.resolve("bin");
        if (Files.exists(scripts.resolve(windows ? "uvicorn.exe" : "uvicorn"))) {
            return true;
        }
        if (!windows) {
            // lib/pythonX.Y/site-packages/uvicorn
            try (java.util.stream.Stream<Path> versions = Files.list(venv.resolve("lib"))) {
                return versions.anyMatch(v -> Files.isDirectory(v.resolve("site-packages").resolve("uvicorn")));
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    private boolean isOnPath(String command) {
        try {
            Process p = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean waitUntilHealthy(int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isServiceHealthy()) {
                return true;
            }
            if (process != null && !process.isAlive()) {
                log.warn("AI service process exited early (exit code {}). Check ai-service.log", process.exitValue());
                return false;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isServiceHealthy() {
        try {
            URL url = URI.create(aiServiceUrl + "/health").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (IOException e) {
            return false;
        }
    }

    private String extractPort(String url) {
        try {
            int port = URI.create(url).getPort();
            return String.valueOf(port > 0 ? port : 8000);
        } catch (Exception e) {
            return "8000";
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
