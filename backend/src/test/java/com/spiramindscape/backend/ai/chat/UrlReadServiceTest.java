package com.spiramindscape.backend.ai.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSRF guard tests for {@link UrlReadService}. These assert the request is
 * REJECTED before any network call — no live fetching happens here.
 */
class UrlReadServiceTest {

    private final UrlReadService service = new UrlReadService();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/admin",
            "http://127.0.0.1:8080/health",
            "http://169.254.169.254/latest/meta-data/",      // AWS metadata
            "http://metadata.google.internal/computeMetadata/v1/",  // GCP metadata
            "http://10.0.0.5/internal",                      // private range
            "http://192.168.1.1/router",                     // private range
            "http://[::1]/",                                 // IPv6 loopback
            "http://service.internal/secret",
    })
    @DisplayName("internal, loopback, private, and metadata addresses are blocked")
    void blocksInternalAddresses(String url) {
        assertThat(service.read(url)).contains("can't be fetched");
    }

    @Test
    @DisplayName("non-standard ports are blocked (SSRF to internal services)")
    void blocksNonStandardPorts() {
        assertThat(service.read("http://example.com:22/")).contains("standard web ports");
        assertThat(service.read("http://example.com:6379/")).contains("standard web ports");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "file:///etc/passwd",
            "gopher://example.com/",
            "not-a-url",
    })
    @DisplayName("non-http(s) schemes are rejected")
    void rejectsNonHttpSchemes(String url) {
        assertThat(service.read(url)).containsAnyOf("Only http", "valid URL");
    }

    @Test
    @DisplayName("blank/null input is handled without throwing")
    void handlesBlank() {
        assertThat(service.read(null)).isNotBlank();
        assertThat(service.read("   ")).isNotBlank();
    }

    // ── Job boards that draw the advert in the browser ──────────────────────
    //
    // The address and the field mapping are what can break; the fetch itself cannot be
    // exercised here, because the SSRF guard blocks loopback and so a local test server is
    // unreachable by design. So these test the two pure halves: the endpoint derived from a
    // posting URL, and the text derived from the API's JSON. The live fetch was verified by
    // hand against the owner's own Workday vacancy (2026-09-10).

    private static String endpointFor(String url) {
        return UrlReadService.JobBoard.match(url).orElseThrow().endpoint();
    }

    @Test
    @DisplayName("a Workday posting resolves to its CXS endpoint, with or without a locale")
    void workdayEndpoint() {
        assertThat(endpointFor("https://groupefdj.wd103.myworkdayjobs.com/en-US/FDJ-UNITED"
                + "/details/Software-Engineer--Java_JR103493"))
                .isEqualTo("https://groupefdj.wd103.myworkdayjobs.com/wday/cxs/groupefdj"
                        + "/FDJ-UNITED/job/Software-Engineer--Java_JR103493");

        assertThat(endpointFor("https://groupefdj.wd103.myworkdayjobs.com/FDJ-UNITED"
                + "/details/Software-Engineer--Java_JR103493"))
                .isEqualTo("https://groupefdj.wd103.myworkdayjobs.com/wday/cxs/groupefdj"
                        + "/FDJ-UNITED/job/Software-Engineer--Java_JR103493");
    }

    @Test
    @DisplayName("Greenhouse and Lever postings resolve to their public APIs")
    void otherBoardEndpoints() {
        assertThat(endpointFor("https://boards.greenhouse.io/acme/jobs/4567890"))
                .isEqualTo("https://boards-api.greenhouse.io/v1/boards/acme/jobs/4567890");
        assertThat(endpointFor("https://job-boards.greenhouse.io/acme/jobs/4567890"))
                .isEqualTo("https://boards-api.greenhouse.io/v1/boards/acme/jobs/4567890");
        assertThat(endpointFor("https://jobs.lever.co/acme/1234abcd-5678-90ef-1234-567890abcdef"))
                .isEqualTo("https://api.lever.co/v0/postings/acme"
                        + "/1234abcd-5678-90ef-1234-567890abcdef");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/jobs/123",
            "https://www.linkedin.com/jobs/view/4123456789",
            // The host is rebuilt from the matched groups, never taken as given, so a
            // look-alike host cannot borrow a board's shape.
            "https://evil.example.com/groupefdj.wd103.myworkdayjobs.com/x/y/details/z",
            // And the tenant group cannot smuggle an authority terminator into the host it
            // is interpolated into: `#`, `?`, `@` and `:` are all excluded, so these do not
            // match at all rather than deriving a URL whose host is "redis" (the SSRF guard
            // refused those, but the pattern should not produce them).
            "https://redis#x.wd1.myworkdayjobs.com/site/details/job",
            "https://2130706433#x.wd1.myworkdayjobs.com/site/details/job",
            "https://evil.com@groupefdj.wd103.myworkdayjobs.com/s/details/j",
    })
    @DisplayName("an ordinary page is read as a page — no board is matched")
    void nonBoardUrls(String url) {
        assertThat(UrlReadService.JobBoard.match(url)).isEmpty();
    }

    @Test
    @DisplayName("the Workday JSON becomes the advert, its title on the first line")
    void workdayRender() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"jobPostingInfo": {
                   "title": "Software Engineer, Java",
                   "location": "Stockholm",
                   "remoteType": "Hybrid",
                   "timeType": "Full time",
                   "jobReqId": "JR103493",
                   "jobDescription": "<p><b>What you will do</b></p><ul><li>Build Java services</li><li>Work with Kafka &amp; Postgres</li></ul>"},
                 "hiringOrganization": {"name": "Kindred People AB"}}
                """);

        String text = UrlReadService.renderWorkday(json);

        // **The title is the first line and carries no label.** It had one, and the
        // application's name is derived from the advert's first line, so the Continue list
        // read "Title: Software Engineer, Java" (found in live testing, 2026-09-10).
        assertThat(text.lines().findFirst()).contains("Software Engineer, Java");
        assertThat(text)
                .contains("Employer: Kindred People AB")
                .contains("Location: Stockholm")
                .contains("Reference: JR103493")
                .contains("What you will do")
                .contains("Build Java services")
                // Entities are decoded, and no tag survives for the model to read as markup.
                .contains("Kafka & Postgres")
                .doesNotContain("<li>");
    }

    @Test
    @DisplayName("Greenhouse's double-encoded HTML is unescaped before its tags are stripped")
    void greenhouseRender() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"title": "QA Engineer",
                 "location": {"name": "Malmö"},
                 "content": "&lt;p&gt;You will test &lt;b&gt;everything&lt;/b&gt;&lt;/p&gt;"}
                """);

        String text = UrlReadService.renderGreenhouse(json);

        assertThat(text.lines().findFirst()).contains("QA Engineer");
        assertThat(text)
                .contains("Location: Malmö")
                .contains("You will test everything")
                .doesNotContain("&lt;")
                .doesNotContain("<b>");
    }

    // ── schema.org JobPosting ────────────────────────────────────────────────

    private static final String ADVERT_HTML = "&lt;p&gt;Har du arbetat med Java och testning?&lt;/p&gt;"
            + "&lt;p&gt;Det inneb&amp;auml;r att du arbetar med att:&lt;/p&gt;"
            + "&lt;ul&gt;&lt;li&gt;Utveckla och vidareutveckla l&#246;sningar i Java&lt;/li&gt;"
            + "&lt;li&gt;Arbeta med API:er och integrationer&lt;/li&gt;&lt;/ul&gt;"
            + "&lt;p&gt;Vi s&#xF6;ker dig som har praktisk erfarenhet av testning, kunskap om Linux och "
            + "goda kunskaper i svenska och engelska. Det &#228;r meriterande med Spring Boot och Git.&lt;/p&gt;";

    private static String page(String jsonLd) {
        return "<html><head><script type=\"application/ld+json\">" + jsonLd + "</script></head>"
                + "<body><div>Acceptera alla cookies</div><footer>Proaktiv Arbetsplats</footer></body></html>";
    }

    private static String posting(String extra) {
        return "{\"@context\":\"http://schema.org/\",\"@type\":\"JobPosting\","
                + "\"title\":\"System Developer/Tester p&#229; Advania\","
                + "\"hiringOrganization\":{\"@type\":\"Organization\",\"name\":\"Advania\"},"
                + "\"jobLocation\":[{\"address\":{\"addressLocality\":\"Malmö\"}}],"
                + "\"employmentType\":\"FULL_TIME\","
                + "\"description\":\"" + ADVERT_HTML + "\"" + extra + "}";
    }

    @Test
    @DisplayName("a JobPosting is read instead of the page around it: title first, no cookie banner or footer")
    void jobPostingReplacesThePage() {
        String text = UrlReadService.jobPostingText(page(posting(""))).orElseThrow();

        assertThat(text.lines().findFirst()).contains("System Developer/Tester på Advania");
        assertThat(text)
                .contains("Employer: Advania")
                .contains("Location: Malmö")
                .contains("Utveckla och vidareutveckla lösningar i Java")
                .contains("Vi söker dig")
                .contains("Det är meriterande")
                .doesNotContain("cookies")
                .doesNotContain("Proaktiv")
                .doesNotContain("<li>")
                .doesNotContain("&lt;");
    }

    @Test
    @DisplayName("a JobPosting inside @graph or an array is found; a page without one falls back")
    void jobPostingShapes() {
        String graph = "{\"@context\":\"https://schema.org\",\"@graph\":[{\"@type\":\"WebPage\"},"
                + posting("") + "]}";
        assertThat(UrlReadService.jobPostingText(page(graph))).isPresent();
        assertThat(UrlReadService.jobPostingText(page("[{\"@type\":\"Organization\"}," + posting("") + "]")))
                .isPresent();
        assertThat(UrlReadService.jobPostingText(page("{\"@type\":\"Organization\",\"name\":\"Advania\"}")))
                .isEmpty();
        assertThat(UrlReadService.jobPostingText(page("{ not json"))).isEmpty();
        assertThat(UrlReadService.jobPostingText("<html><body>No structured data</body></html>")).isEmpty();
    }

    @Test
    @DisplayName("a JobPosting too short to be an advert does not replace the page")
    void shortJobPostingIgnored() {
        String tiny = "{\"@type\":\"JobPosting\",\"title\":\"Developer\",\"description\":\"Apply now\"}";
        assertThat(UrlReadService.jobPostingText(page(tiny))).isEmpty();
    }

    @Test
    @DisplayName("Lever's named lists are kept — they hold the requirements")
    void leverRender() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"text": "Backend Developer",
                 "categories": {"location": "Remote", "team": "Platform"},
                 "description": "<p>About the role</p>",
                 "lists": [{"text": "Requirements",
                            "content": "<li>5 years of Java</li><li>Fluent Swedish</li>"}],
                 "additional": "<p>We offer pension</p>"}
                """);

        String text = UrlReadService.renderLever(json);

        assertThat(text.lines().findFirst()).contains("Backend Developer");
        assertThat(text)
                .contains("Team: Platform")
                .contains("About the role")
                .contains("Requirements")
                .contains("5 years of Java")
                .contains("Fluent Swedish")
                .contains("We offer pension");
    }
}
