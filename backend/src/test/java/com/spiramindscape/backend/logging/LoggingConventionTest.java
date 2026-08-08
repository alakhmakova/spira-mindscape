package com.spiramindscape.backend.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scans the source for logging that would leak secrets or user content.
 *
 * <p>Why a source scan rather than a runtime test: a leak like the one this codebase
 * actually shipped — a raw Gemini tool-call chunk logged at WARN — throws nothing, fails
 * nothing, and is only visible if a human happens to read that line. No runtime assertion
 * can catch a log statement that was never exercised. Reading the source catches the
 * <em>next</em> one, written months from now.
 *
 * <p>The rules mirror the never-log list in {@code docs/logging.md}. When a match is a
 * genuine false positive, prefer sharpening the heuristic over adding an exception —
 * a test people learn to suppress protects nothing.
 */
class LoggingConventionTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /** A logging call: log.info(…), logger.error(…), … Group 2 is the level. */
    private static final Pattern LOG_CALL =
            Pattern.compile("\\b(log|logger|LOG)\\s*\\.\\s*(trace|debug|info|warn|error)\\s*\\(");

    /** A log statement can wrap over several lines; don't run away on a malformed file. */
    private static final int MAX_STATEMENT_LINES = 8;

    /**
     * Identifiers whose VALUES are credentials. One of these as an argument means a secret
     * is about to be interpolated into a log line.
     */
    private static final List<String> SECRET_TOKENS = List.of(
            "apikey", "idtoken", "refreshtoken", "accesstoken", "tokenvalue",
            "password", "secret", "credential", "plaintext", "encryptionkey");

    /**
     * Identifiers that typically carry the user's own text — goals, notes, AI answers,
     * uploaded files. Allowed at DEBUG (off in production) but never at INFO and above,
     * which is what actually reaches Cloud Logging.
     */
    private static final List<String> CONTENT_TOKENS = List.of(
            "chunk", "payload", "body", "content", "prompt", "completion", "transcript");

    /** {@code chunks.size()}, {@code body.length()}, {@code payload.length} — a measurement. */
    private static final Pattern MEASUREMENT =
            Pattern.compile("\\w+\\s*\\.\\s*(size\\s*\\(\\s*\\)|length\\s*\\(\\s*\\)|length\\b)");

    /**
     * Statements reviewed and deliberately kept, keyed by a distinctive fragment.
     *
     * <p>This list exists so that an accepted trade-off can be written down instead of
     * being hidden by loosening a pattern — a test that cannot say "reviewed, and here is
     * why" is one people eventually delete. Keep it short, and justify every entry.
     */
    private static final Map<String, String> ACCEPTED = Map.of(
            "Mistral OCR failed: HTTP",
            "Logs Mistral's ERROR envelope on a non-200, capped at 300 chars by abbreviate(). "
                    + "Provider error text is the only clue why OCR was refused, and a provider "
                    + "echoing the request image back inside an error is not a shape Mistral uses. "
                    + "Revisit if that changes."
    );

    private static boolean isAccepted(String source) {
        return ACCEPTED.keySet().stream().anyMatch(source::contains);
    }

    /** A whole log statement, joined across continuation lines. */
    private record LogStatement(Path file, int line, String level, String source) {

        boolean isAboveDebug() {
            return level.equals("info") || level.equals("warn") || level.equals("error");
        }

        /**
         * The statement's ARGUMENTS, lower-cased: message literals are blanked out (a
         * message that merely mentions "token" is fine) and measurements are reduced to a
         * neutral word — logging {@code chunks.size()} is exactly the right thing to do,
         * so flagging it would train people to disable this test.
         */
        String arguments() {
            String lower = source.toLowerCase(Locale.ROOT);
            String withoutLiterals = lower.replaceAll("\"[^\"]*\"", "\"\"");
            return MEASUREMENT.matcher(withoutLiterals).replaceAll("size");
        }

        String mentions(List<String> tokens, String rule) {
            String args = arguments();
            for (String token : tokens) {
                if (args.contains(token)) {
                    return rule + ":" + token;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return "%s:%d%n    %s".formatted(file, line, source);
        }
    }

    private record Violation(LogStatement statement, String rule) {
        @Override
        public String toString() {
            return "[%s] %s".formatted(rule, statement);
        }
    }

    private static List<Path> sourceFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /**
     * Collects every log statement in the tree, joining continuation lines. Line-by-line
     * scanning would miss the very shape this codebase writes — the message on one line
     * and the leaking argument on the next.
     */
    private static List<LogStatement> logStatements() throws IOException {
        List<LogStatement> statements = new ArrayList<>();
        for (Path file : sourceFiles()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = LOG_CALL.matcher(lines.get(i));
                if (!matcher.find()) {
                    continue;
                }
                StringBuilder source = new StringBuilder();
                int end = Math.min(lines.size(), i + MAX_STATEMENT_LINES);
                for (int j = i; j < end; j++) {
                    source.append(j > i ? " " : "").append(lines.get(j).trim());
                    if (lines.get(j).stripTrailing().endsWith(";")) {
                        break;
                    }
                }
                statements.add(new LogStatement(file, i + 1, matcher.group(2), source.toString()));
            }
        }
        return statements;
    }

    private static List<Violation> violations(List<LogStatement> statements,
                                              Function<LogStatement, String> rule) {
        List<Violation> found = new ArrayList<>();
        for (LogStatement statement : statements) {
            if (isAccepted(statement.source())) {
                continue;
            }
            String broken = rule.apply(statement);
            if (broken != null) {
                found.add(new Violation(statement, broken));
            }
        }
        return found;
    }

    @Test
    @DisplayName("the scanner actually finds the log statements it is meant to police")
    void scannerFindsLogStatements() throws IOException {
        List<LogStatement> statements = logStatements();
        // A self-check: if a refactor ever breaks the regex, the two rules below would
        // silently pass on an empty list and this test would be pure decoration.
        assertThat(statements).hasSizeGreaterThan(30);
        assertThat(statements).anyMatch(s -> s.source().contains("auth_signin_success"));
        // Continuation lines must be joined, or arguments on the next line escape the scan.
        assertThat(statements).anyMatch(s -> s.source().contains("rate_limit_block")
                && s.source().contains("getRequestURI"));
    }

    @Test
    @DisplayName("no log statement interpolates a secret (API key, token, password)")
    void logsNoSecrets() throws IOException {
        List<Violation> found = violations(logStatements(),
                statement -> statement.mentions(SECRET_TOKENS, "secret"));
        assertThat(found)
                .as("Secrets must never be logged (docs/logging.md). Offending statements:%n%s", found)
                .isEmpty();
    }

    @Test
    @DisplayName("user content is not logged at INFO or above — only DEBUG, which is off in prod")
    void logsNoUserContentAboveDebug() throws IOException {
        // This is the exact defect class that shipped: GeminiProvider logged a raw model
        // chunk at WARN, so real users' goal text went to Cloud Logging in production.
        List<Violation> found = violations(logStatements(), statement ->
                statement.isAboveDebug() ? statement.mentions(CONTENT_TOKENS, "content") : null);
        assertThat(found)
                .as("User content must not reach production logs (docs/logging.md). "
                        + "Log a length or a shape instead. Offending statements:%n%s", found)
                .isEmpty();
    }

    @Test
    @DisplayName("nothing prints to stdout/stderr or swallows a trace with printStackTrace")
    void usesTheLoggerNotTheConsole() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sourceFiles()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("*") || line.startsWith("//")) {
                    continue; // javadoc / comment
                }
                // printStackTrace writes to stderr with no severity and no trace id, so on
                // Cloud Run it becomes an unattributed DEFAULT entry.
                if (line.contains("System.out.print") || line.contains("System.err.print")
                        || line.contains("printStackTrace()")) {
                    offenders.add("%s:%d%n    %s".formatted(file, i + 1, line));
                }
            }
        }
        assertThat(offenders)
                .as("Use the SLF4J logger, not the console. Offending lines:%n%s", offenders)
                .isEmpty();
    }
}
