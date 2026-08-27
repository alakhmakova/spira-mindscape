package com.spiramindscape.backend.ai.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **Every prompt file must survive the Docker build context.**
 *
 * <p>{@link PromptResources} loads {@code prompts/grow/coach-method.md} at construction and fails
 * startup when it is missing — deliberately, because a coach running silently without its method
 * is worse than a boot failure. That rule only holds if the file is actually in the jar.
 *
 * <p>It was not. `.dockerignore` carried a blanket {@code **}{@code /*.md} to keep documentation
 * out of the image, and the prompt is Markdown: `COPY backend/src src` produced an <b>empty</b>
 * {@code prompts/grow/} directory, the jar shipped without the file, and Cloud Run rejected three
 * revisions in a row with "The user-provided container failed to start and listen on the port" —
 * a message that points nowhere near a Markdown rule. Everything passed on the way there: the
 * unit tests, the integration tests, the E2E suite, because all of them run from
 * {@code src/main/resources} where the file plainly exists (2026-08-27).
 *
 * <p>So this reads the ignore file itself. It is the only layer that can see the difference
 * between "the resource exists in the repository" and "the resource reaches the image", and it
 * fails for exactly the edit that caused the outage.
 */
class PromptResourcePackagingTest {

    /** Repo root, from the backend module's working directory. */
    private static final Path ROOT = Path.of("..");

    private static final Path PROMPTS =
            Path.of("backend", "src", "main", "resources", "prompts");

    @Test
    @DisplayName("the prompts directory is exempt from .dockerignore's Markdown exclusion")
    void promptsAreNotStrippedFromTheImage() throws IOException {
        Path dockerignore = ROOT.resolve(".dockerignore");
        assertThat(dockerignore)
                .as(".dockerignore has moved — move this check with it")
                .exists();
        List<String> lines = Files.readAllLines(dockerignore).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

        int markdownExclusion = lastIndexMatching(lines, "**/*.md", "*.md");
        if (markdownExclusion < 0) return; // nothing strips Markdown; nothing to exempt

        int negation = lastIndexMatching(
                lines,
                "!backend/src/main/resources/prompts/**",
                "!backend/src/main/resources/prompts");

        assertThat(negation)
                .as("""
                        .dockerignore excludes Markdown (line %d) and nothing brings the prompts \
                        back. `PromptResources` loads prompts/grow/coach-method.md at startup and \
                        throws when it is missing, so the container will build, deploy, and then \
                        fail to listen on its port. Add:

                          !backend/src/main/resources/prompts/**
                        """.formatted(markdownExclusion + 1))
                .isGreaterThanOrEqualTo(0);

        assertThat(negation)
                .as("the negation must come AFTER the exclusion — in .dockerignore the last "
                        + "matching pattern wins, so above it the prompts are stripped anyway")
                .isGreaterThan(markdownExclusion);
    }

    @Test
    @DisplayName("every prompt the app loads is a real, non-empty file in the repository")
    void promptsExistOnDisk() throws IOException {
        Path coachMethod = ROOT.resolve(PROMPTS).resolve("grow").resolve("coach-method.md");

        assertThat(coachMethod).exists();
        assertThat(Files.readString(coachMethod).strip())
                .as("a blank prompt file fails startup just as a missing one does")
                .isNotEmpty();
    }

    private static int lastIndexMatching(List<String> lines, String... wanted) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            for (String w : wanted) {
                if (lines.get(i).equals(w)) found = i;
            }
        }
        return found;
    }
}
