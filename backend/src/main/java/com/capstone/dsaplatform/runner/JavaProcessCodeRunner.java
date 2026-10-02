package com.capstone.dsaplatform.runner;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.capstone.dsaplatform.entity.Problem;
import com.capstone.dsaplatform.entity.TestCase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Runs learner code locally: compile once in-process, then one fresh `java` process per test.
 * A fresh process per test means static state or a crash in one test cannot affect another.
 * Demo only: there is no sandbox, so production would use Docker or Judge0 instead.
 */
@Service
@Primary
public class JavaProcessCodeRunner implements CodeRunner {

    // Caps the whole submission so a slow solution with many tests cannot hold a request for minutes.
    static final long TOTAL_BUDGET_MS = 10_000;

    // Enough for any expected DSA output; stops a print-in-a-loop bug from filling the server's memory.
    static final int OUTPUT_CAP_BYTES = 64 * 1024;

    // The next phase still needs to know the process failed, so a crash with no class name is never null.
    static final String NON_ZERO_EXIT = "NonZeroExit";

    // How the JVM reports an uncaught exception: Exception in thread "main" java.lang.Foo: message
    private static final Pattern UNCAUGHT =
            Pattern.compile("Exception in thread \"[^\"]*\" ([\\w$.]+)");

    // Fallback for code that calls printStackTrace() and then System.exit(1) itself.
    private static final Pattern QUALIFIED_THROWABLE =
            Pattern.compile("\\b((?:[A-Za-z_$][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error))\\b");

    private final RunnerTestCaseRepository testCaseRepository;
    private final JavaCompiler compiler;
    private final String javaExecutable;

    public JavaProcessCodeRunner(RunnerTestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
        this.compiler = ToolProvider.getSystemJavaCompiler();
        // Fail at startup, not on the first submission: a JRE has no compiler, so nothing could ever be graded.
        if (compiler == null) {
            throw new IllegalStateException(
                    "A full JDK is required to run submissions: ToolProvider.getSystemJavaCompiler() "
                            + "returned null, which means the backend is running on a JRE. "
                            + "Point JAVA_HOME at a JDK 17+ and restart.");
        }
        // Same installation as the compiler, so compiled class files always match the JVM that runs them.
        this.javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Override
    public RunResult run(Problem problem, String code) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("dsa-run-");
            // javac requires a public class Main to live in Main.java; the starter code uses that name.
            Path source = workDir.resolve("Main.java");
            Files.writeString(source, code, UTF_8);

            String compileError = compile(source, workDir);
            if (compileError != null) {
                return new RunResult(false, compileError, List.of());
            }
            return new RunResult(true, null, runAllTests(problem, workDir));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the submission workspace", e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    /** Returns null on success, otherwise javac-style messages such as "Main.java:7: error: ';' expected". */
    private String compile(Path source, Path outputDir) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // Closing the file manager releases handles on Main.java, so Windows can delete the temp dir.
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, UTF_8)) {
            // Without an explicit classpath, javac would see this Spring app's classpath and accept
            // code that imports Spring or Hibernate, which would then fail at runtime.
            List<String> options = List.of(
                    "-d", outputDir.toString(),
                    "-classpath", outputDir.toString(),
                    "-encoding", "UTF-8",
                    "-proc:none");
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(source.toFile());
            // The StringWriter swallows stray compiler chatter; the diagnostics carry everything we report.
            boolean ok = compiler.getTask(new StringWriter(), fileManager, diagnostics, options, null, units).call();
            if (ok) {
                return null;
            }
        }
        String errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(JavaProcessCodeRunner::formatDiagnostic)
                .collect(Collectors.joining("\n"));
        return errors.isEmpty() ? "Compilation failed." : errors;
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> d) {
        String message = d.getMessage(Locale.ENGLISH);
        // NOPOS means javac could not tie the error to a line (rare, e.g. a bad option).
        return d.getLineNumber() == Diagnostic.NOPOS
                ? "Main.java: error: " + message
                : "Main.java:" + d.getLineNumber() + ": error: " + message;
    }

    private List<TestOutcome> runAllTests(Problem problem, Path classDir) {
        List<TestCase> tests = new ArrayList<>(testCaseRepository.findByProblemId(problem.getId()));
        // TestTag declares SAMPLE, EDGE, BOUNDARY, LARGE in that order, so the enum's natural order is
        // the first-failure order from CONTRACT.md section 1; ordinal breaks ties within a tag.
        tests.sort(Comparator.comparing(TestCase::getTag).thenComparing(TestCase::getOrdinal));

        long deadline = System.nanoTime() + MILLISECONDS.toNanos(TOTAL_BUDGET_MS);
        List<TestOutcome> outcomes = new ArrayList<>();
        // Every test gets an outcome, even after a failure, because the UI shows the full test table.
        for (TestCase test : tests) {
            long remainingMs = NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMs <= 0) {
                // Budget used up: report as timed out without starting a process, so the list stays complete.
                outcomes.add(new TestOutcome(test.getId(), test.getTag(), false, true, null, null, 0));
                continue;
            }
            // A test that starts near the end of the budget only gets what is left of it.
            long limitMs = Math.min(problem.getTimeLimitMs(), remainingMs);
            outcomes.add(runOneTest(test, classDir, limitMs));
        }
        return outcomes;
    }

    private TestOutcome runOneTest(TestCase test, Path classDir, long limitMs) {
        // -Xmx256m stops one submission from taking the server's memory (e.g. new int[Integer.MAX_VALUE]).
        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable, "-Xmx256m", "-cp", classDir.toString(), "Main");
        builder.directory(classDir.toFile());

        long start = System.nanoTime();
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the java process", e);
        }

        // Stdin and both outputs each get their own thread. Pipes have a small OS buffer: if we wrote
        // all of stdin before reading stdout, a program that prints while reading could block forever.
        StreamCollector stdout = new StreamCollector(process.getInputStream());
        StreamCollector stderr = new StreamCollector(process.getErrorStream());
        Thread stdinFeeder = feedStdin(process, test.getInput());

        try {
            boolean finished = process.waitFor(limitMs, MILLISECONDS);
            long runtimeMs = NANOSECONDS.toMillis(System.nanoTime() - start);
            if (!finished) {
                process.destroyForcibly();
                // Wait until it is really dead: Windows will not delete files a live process is using.
                process.waitFor();
            }
            // The process is dead now, so its pipes are closed and these threads end promptly.
            stdinFeeder.join(1000);
            String out = stdout.text();
            String err = stderr.text();

            if (!finished) {
                return new TestOutcome(test.getId(), test.getTag(), false, true, null, out, runtimeMs);
            }
            if (process.exitValue() != 0) {
                return new TestOutcome(test.getId(), test.getTag(), false, false,
                        exceptionType(err), out, runtimeMs);
            }
            boolean passed = normalize(out).equals(normalize(test.getExpectedOutput()));
            return new TestOutcome(test.getId(), test.getTag(), passed, false, null, out, runtimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running a test", e);
        } finally {
            // Covers every exit path, including exceptions, so no child JVM is ever left running.
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Thread feedStdin(Process process, String input) {
        Thread feeder = new Thread(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(UTF_8));
            } catch (IOException ignored) {
                // The program exited or was killed before reading all its input. That is reflected in
                // its own outcome; it is not a runner failure.
            }
        }, "runner-stdin");
        // Daemon so a stuck thread can never keep the backend from shutting down.
        feeder.setDaemon(true);
        feeder.start();
        return feeder;
    }

    /** Simple class name of the thrown exception, or "NonZeroExit" if stderr names none. */
    static String exceptionType(String stderr) {
        Matcher m = UNCAUGHT.matcher(stderr);
        if (!m.find()) {
            m = QUALIFIED_THROWABLE.matcher(stderr);
            if (!m.find()) {
                return NON_ZERO_EXIT;
            }
        }
        String className = m.group(1);
        // Simple name, because CONTRACT.md section 4.2 matches on "ArrayIndexOutOfBoundsException".
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * CONTRACT.md section 4.1, step 3: strip trailing whitespace from each line, drop trailing
     * empty lines, then compare exactly. A missing final newline or a stray space never fails a learner.
     */
    static String normalize(String text) {
        List<String> lines = new ArrayList<>();
        // stripTrailing also removes '\r', so Windows line endings compare equal to Unix ones.
        for (String line : text.split("\n", -1)) {
            lines.add(line.stripTrailing());
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return String.join("\n", lines);
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        // Children first (reverse order), because a directory must be empty before it can be deleted.
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // A cleanup failure must not hide the learner's result; the OS clears its temp dir eventually.
        }
    }

    /** Reads a process stream on its own thread, keeping at most OUTPUT_CAP_BYTES. */
    private static final class StreamCollector {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Thread thread;

        StreamCollector(InputStream in) {
            thread = new Thread(() -> drain(in), "runner-drain");
            thread.setDaemon(true);
            thread.start();
        }

        private void drain(InputStream in) {
            byte[] chunk = new byte[8192];
            try (in) {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    int room = OUTPUT_CAP_BYTES - buffer.size();
                    // Keep reading past the cap and discard the rest. If we stopped reading, a program that
                    // prints a lot would block on a full pipe and be wrongly reported as timed out.
                    if (room > 0) {
                        buffer.write(chunk, 0, Math.min(n, room));
                    }
                }
            } catch (IOException ignored) {
                // The stream closes when the process is killed; whatever was read so far is kept.
            }
        }

        String text() throws InterruptedException {
            thread.join(1000);
            return buffer.toString(UTF_8);
        }
    }
}
