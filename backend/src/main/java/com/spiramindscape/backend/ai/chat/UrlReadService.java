package com.spiramindscape.backend.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a web page and returns its readable text, for the AI's {@code read_url}
 * tool. No API key needed — a plain HTTP GET plus HTML-to-text stripping.
 *
 * <p>Best-effort: pages behind login or rendered by JavaScript yield little
 * text, in which case the caller (the model) is told to ask the user to paste
 * the content. Includes a basic SSRF guard against internal/loopback hosts.
 *
 * <p>Two shapes, one implementation. {@link #read} answers a MODEL, so a failure is
 * prose it can act on — that is why it never throws and never returns empty.
 * {@link #fetch} answers CODE, which needs to know whether there is a page at all:
 * saving a job application from a link has to fail at the start card, where the user
 * can paste the text instead, rather than storing an apology as the advert. Telling
 * the two apart by string-matching the messages would be a guess; {@link Fetched}
 * makes it a fact.
 */
@Service
public class UrlReadService {

    private static final Logger log = LoggerFactory.getLogger(UrlReadService.class);

    /**
     * The outcome of one fetch: the page's text, or why there isn't any.
     *
     * @param text  the readable text when {@code ok}, otherwise the explanation
     * @param ok    whether {@code text} is a page rather than a message about one
     */
    public record Fetched(String text, boolean ok) {
        static Fetched ok(String text) {
            return new Fetched(text, true);
        }

        static Fetched failed(String why) {
            return new Fetched(why, false);
        }
    }

    private static final int MAX_CHARS = 12_000;
    private static final int MAX_BODY_CHARS = 2_000_000;

    /**
     * Below this, an HTML page's text is treated as no page at all.
     *
     * <p>A page rendered in the browser still answers with a shell — a nav bar, a cookie
     * notice, a footer — and the model has no way to tell that from a very short advert,
     * so it reads the chrome as the job. Two hundred characters is under any real posting
     * and over any shell. Plain text is exempt: a small file fetched from
     * raw.githubusercontent is short on purpose.
     */
    private static final int MIN_USEFUL_HTML_CHARS = 200;

    private static final int MAX_REDIRECT_HOPS = 3;

    // SSRF: do NOT let the HTTP client auto-follow redirects — a public URL can
    // 302 to an internal/metadata address, bypassing the pre-fetch host check.
    // We follow manually, re-validating the host on every hop.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Returns the page's readable text, or a short explanatory message (never throws).
     * For the model's {@code read_url} tool, where an explanation is a usable answer.
     */
    public String read(String url) {
        return fetch(url).text();
    }

    /** The same fetch, with success or failure told apart. For callers that are code. */
    public Fetched fetch(String url) {
        if (url == null || url.isBlank()) return Fetched.failed("No URL was provided.");
        String trimmed = url.trim();

        // Some job boards ship an empty page and draw the advert in the browser, so there
        // is nothing here to strip tags out of. Each of those serves the same advert as
        // JSON. A failure here falls through to the page itself rather than giving up:
        // if a board changes its API, reading the page badly beats not reading it at all.
        var board = JobBoard.match(trimmed);
        if (board.isPresent()) {
            Fetched viaApi = fetchFromApi(board.get());
            if (viaApi.ok()) return viaApi;
            log.debug("job_board_api_miss board={}", board.get().board());
        }

        Raw raw = get(trimmed, PAGE_ACCEPT);
        if (!raw.ok()) return Fetched.failed(raw.failure());

        // **A page that carries its advert as structured data is read from that.** Job
        // boards publish a schema.org JobPosting for search engines; the page text around it
        // is the same advert wrapped in a cookie banner, navigation, the employer's culture
        // pages and their other vacancies. Read as a page, all of that became "the advert":
        // the CV writer quoted a Teamtailor footer ("Proaktiv Arbetsplats") back as a
        // requirement (2026-09-15). The structured copy of that page is 5,000 clean
        // characters against 9,600 of mixed ones.
        var posting = jobPostingText(raw.body());
        if (posting.isPresent()) return Fetched.ok(clip(posting.get()));

        boolean isHtml = raw.contentType().contains("html")
                || raw.body().contains("<html") || raw.body().contains("<body");
        String text = isHtml ? htmlToText(raw.body()) : raw.body().strip();

        // A page drawn by JavaScript answers with its chrome and nothing else, and the
        // model then reads a cookie banner as the job advert. The floor applies to HTML
        // only: a short plain-text file (a small source file on raw.githubusercontent) is
        // exactly what was asked for and must not be rejected for being short.
        if (text.isBlank() || (isHtml && text.length() < MIN_USEFUL_HTML_CHARS)) {
            // **Worded for a PERSON, because it reaches one.** `CvApplicationService.create`
            // puts this string into a 400 that the start card shows as a toast, so the
            // model-facing version ("Do not treat what came back as the content. Ask the
            // user to paste the text") was read by the user as an instruction to themselves
            // (found in review, 2026-09-10). It still tells a model everything it needs.
            return Fetched.failed("That page rendered almost no text (" + text.length()
                    + " characters) — it is drawn in the browser, or behind a login. Paste "
                    + "the advert's text instead, or attach the page as a file.");
        }
        return Fetched.ok(clip(text));
    }

    /** One HTTP GET with the redirect and SSRF handling: either a body, or why not. */
    private record Raw(String body, String contentType, String failure) {
        boolean ok() {
            return failure == null;
        }

        static Raw failed(String why) {
            return new Raw("", "", why);
        }
    }

    private static final String PAGE_ACCEPT =
            "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5";

    private Raw get(String url, String accept) {
        String current = url;
        try {
            HttpResponse<String> res = null;
            for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
                String rejection = validateFetchUrl(current);
                if (rejection != null) return Raw.failed(rejection);
                HttpRequest req = HttpRequest.newBuilder(URI.create(current))
                        .timeout(Duration.ofSeconds(20))
                        .header("user-agent", "Mozilla/5.0 (compatible; SpiraBot/1.0)")
                        .header("accept", accept)
                        .GET()
                        .build();
                res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int sc = res.statusCode();
                if (sc >= 300 && sc < 400) {
                    String location = res.headers().firstValue("location").orElse(null);
                    if (location == null) break;
                    // Resolve relative redirects against the current URL, then re-check.
                    current = URI.create(current).resolve(location).toString();
                    if (hop == MAX_REDIRECT_HOPS) {
                        return Raw.failed("That page redirected too many times — ask the user to paste the text.");
                    }
                    continue;
                }
                break;
            }
            if (res == null) return Raw.failed("That doesn't look like a valid URL.");
            if (res.statusCode() >= 400) {
                return Raw.failed("The page returned HTTP " + res.statusCode()
                        + " — it may require login or be unavailable. Ask the user to paste the text.");
            }
            String body = res.body();
            if (body == null || body.isBlank()) return Raw.failed("(the page returned no content)");
            if (body.length() > MAX_BODY_CHARS) body = body.substring(0, MAX_BODY_CHARS);
            return new Raw(body,
                    res.headers().firstValue("content-type").orElse("").toLowerCase(), null);
        } catch (Exception e) {
            log.warn("read_url_failed host={}", hostOf(url), e);
            return Raw.failed("Couldn't fetch the page. Ask the user to paste the text.");
        }
    }

    /** The host alone — a pasted URL can carry a query string, which is the user's data. */
    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "unparseable";
        }
    }

    private static String clip(String text) {
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "…[truncated]" : text;
    }

    // ── Job boards whose posting page is empty ───────────────────────────────

    /**
     * A job board that draws its advert in the browser, and where the same advert is
     * served as JSON from a derivable address.
     *
     * <p>This is not a nicety. The owner's own test vacancy — a Workday posting — returns
     * <b>zero</b> readable text as HTML, so the CV writer was handed a link it could not
     * read, and every "paste the text instead" was work the app should have done. Its CXS
     * endpoint returns the whole 3,700-character description.
     *
     * <p>Adding a board is a pattern, an address built from it, and a renderer. The host
     * is always rebuilt from the matched groups (which cannot contain a dot or a slash),
     * never taken from the URL as given, so this cannot be talked into fetching somewhere
     * else — and the derived address still goes through the SSRF check like any other.
     */
    enum JobBoard {
        /**
         * {@code https://{tenant}.{wdN}.myworkdayjobs.com/[{locale}/]{site}/details/{slug}}
         * → {@code …/wday/cxs/{tenant}/{site}/job/{slug}}. The locale is optional: both
         * shapes are in the wild, and the API does not take it.
         */
        // The tenant excludes `:@?#` as well as dots and slashes, so the derived string's
        // AUTHORITY cannot be anything but a myworkdayjobs.com host. Without that,
        // `https://redis#x.wd1.myworkdayjobs.com/...` derived a URL whose host was `redis`
        // — the SSRF check refused it, but the claim in this enum's doc was untrue.
        WORKDAY(Pattern.compile(
                "^https://([^./:@?#]+)\\.(wd\\d+)\\.myworkdayjobs\\.com/(?:[^/]+/)?([^/]+)/details/([^/?#]+)"),
                m -> "https://" + m.group(1) + "." + m.group(2) + ".myworkdayjobs.com/wday/cxs/"
                        + m.group(1) + "/" + m.group(3) + "/job/" + m.group(4),
                UrlReadService::renderWorkday),

        /** Greenhouse's public board API, same board and job ids as the page. */
        GREENHOUSE(Pattern.compile(
                "^https://(?:boards|job-boards)\\.greenhouse\\.io/([^/]+)/jobs/(\\d+)"),
                m -> "https://boards-api.greenhouse.io/v1/boards/" + m.group(1)
                        + "/jobs/" + m.group(2),
                UrlReadService::renderGreenhouse),

        /** Lever's public postings API. */
        LEVER(Pattern.compile("^https://jobs\\.lever\\.co/([^/]+)/([0-9a-fA-F-]{8,})"),
                m -> "https://api.lever.co/v0/postings/" + m.group(1) + "/" + m.group(2),
                UrlReadService::renderLever);

        private final Pattern pattern;
        private final Function<Matcher, String> endpoint;
        private final Function<JsonNode, String> renderer;

        JobBoard(Pattern pattern, Function<Matcher, String> endpoint,
                 Function<JsonNode, String> renderer) {
            this.pattern = pattern;
            this.endpoint = endpoint;
            this.renderer = renderer;
        }

        /** A matched board and the address its advert can be read from. */
        record Api(JobBoard board, String endpoint, Function<JsonNode, String> renderer) {}

        static java.util.Optional<Api> match(String url) {
            for (JobBoard b : values()) {
                Matcher m = b.pattern.matcher(url);
                if (m.find()) {
                    return java.util.Optional.of(
                            new Api(b, b.endpoint.apply(m), b.renderer));
                }
            }
            return java.util.Optional.empty();
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Fetched fetchFromApi(JobBoard.Api api) {
        Raw raw = get(api.endpoint(), "application/json");
        if (!raw.ok()) return Fetched.failed(raw.failure());
        try {
            String text = api.renderer().apply(MAPPER.readTree(raw.body())).strip();
            return text.isBlank()
                    ? Fetched.failed("(the job board's API returned no description)")
                    : Fetched.ok(clip(text));
        } catch (Exception e) {
            // Shape changed, or it is not JSON at all. Caller falls back to the page.
            log.debug("job_board_parse_failed board={}", api.board());
            return Fetched.failed("(the job board's API could not be read)");
        }
    }

    static String renderWorkday(JsonNode json) {
        JsonNode info = json.path("jobPostingInfo");
        StringBuilder sb = new StringBuilder();
        title(sb, info.path("title").asText(null));
        line(sb, "Employer", json.path("hiringOrganization").path("name").asText(null));
        line(sb, "Location", info.path("location").asText(null));
        line(sb, "Remote", info.path("remoteType").asText(null));
        line(sb, "Employment", info.path("timeType").asText(null));
        line(sb, "Reference", info.path("jobReqId").asText(null));
        sb.append('\n').append(htmlToText(info.path("jobDescription").asText("")));
        return sb.toString();
    }

    static String renderGreenhouse(JsonNode json) {
        StringBuilder sb = new StringBuilder();
        title(sb, json.path("title").asText(null));
        line(sb, "Location", json.path("location").path("name").asText(null));
        // Greenhouse double-encodes: the HTML arrives entity-escaped inside the JSON
        // string, so unescaping has to happen before the tags can be stripped.
        sb.append('\n').append(htmlToText(unescapeEntities(json.path("content").asText(""))));
        return sb.toString();
    }

    static String renderLever(JsonNode json) {
        StringBuilder sb = new StringBuilder();
        title(sb, json.path("text").asText(null));
        line(sb, "Location", json.path("categories").path("location").asText(null));
        line(sb, "Team", json.path("categories").path("team").asText(null));
        sb.append('\n').append(htmlToText(json.path("description").asText("")));
        // Lever keeps the requirements in named lists rather than in the description, so
        // dropping these would drop exactly the part the writer deconstructs.
        for (JsonNode list : json.path("lists")) {
            sb.append("\n\n").append(list.path("text").asText("")).append('\n')
                    .append(htmlToText(list.path("content").asText("")));
        }
        sb.append('\n').append(htmlToText(json.path("additional").asText("")));
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) sb.append(label).append(": ").append(value).append('\n');
    }

    /**
     * The job title, on the first line and UNLABELLED.
     *
     * <p>It carried a "Title: " label like the rest, and the application's name is derived
     * from the advert's first line ({@code CvApplicationService.titleFor}) — so the
     * Continue list read "Title: Software Engineer, Java" (found in live testing against
     * the owner's own vacancy, 2026-09-10). A real advert opens with its own title anyway,
     * so this is the more faithful rendering as well as the working one.
     */
    private static void title(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) sb.append(value).append('\n');
    }

    /**
     * Validates a single URL to fetch (called on every redirect hop). Returns a
     * user-facing rejection message, or {@code null} if the URL is safe to fetch.
     *
     * <p>SSRF guard: scheme must be http(s); port must be 80/443 (or default);
     * the host must not be loopback/private/link-local or the cloud metadata
     * endpoint; resolution is checked here, immediately before the request, to
     * shrink (not eliminate) the DNS-rebinding window.
     */
    private static String validateFetchUrl(String u) {
        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
            return "Only http(s) URLs can be read.";
        }
        URI uri;
        try {
            uri = URI.create(u);
        } catch (Exception e) {
            return "That doesn't look like a valid URL.";
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return "That address can't be fetched (only standard web ports are allowed).";
        }
        if (isBlockedHost(uri.getHost())) {
            return "That address can't be fetched (internal/local addresses are not allowed).";
        }
        return null;
    }

    /** Cloud metadata hostnames that must never be fetched (credential theft). */
    private static final java.util.Set<String> BLOCKED_HOSTNAMES = java.util.Set.of(
            "localhost", "metadata.google.internal", "metadata");

    /** Blocks loopback / private / link-local / metadata hosts to avoid SSRF. */
    private static boolean isBlockedHost(String host) {
        if (host == null || host.isBlank()) return true;
        String h = host.toLowerCase();
        if (BLOCKED_HOSTNAMES.contains(h) || h.endsWith(".local") || h.endsWith(".internal")) return true;
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isMulticastAddress()) {
                    return true;
                }
                // GCP/AWS metadata IPs (link-local 169.254/16 is caught above,
                // but block the canonical addresses explicitly for clarity).
                String ip = addr.getHostAddress();
                if (ip.equals("169.254.169.254") || ip.startsWith("fd00:ec2:")) return true;
            }
        } catch (Exception e) {
            return true; // can't resolve → don't fetch
        }
        return false;
    }

    /**
     * The raw body of a URL, for callers that parse it themselves (a repository tree as JSON, a
     * build file). Same SSRF and redirect handling as {@link #fetch}; clipped to {@code maxChars}
     * instead of the page limit, and never reduced to text.
     */
    public Fetched fetchRaw(String url, String accept, int maxChars) {
        if (url == null || url.isBlank()) return Fetched.failed("No URL was provided.");
        Raw raw = get(url.trim(), accept == null ? PAGE_ACCEPT : accept);
        if (!raw.ok()) return Fetched.failed(raw.failure());
        String body = raw.body();
        return Fetched.ok(body.length() > maxChars ? body.substring(0, maxChars) : body);
    }

    // ── schema.org JobPosting ────────────────────────────────────────────────

    private static final Pattern JSON_LD = Pattern.compile(
            "(?is)<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>");
    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    /** The advert from a page's JobPosting structured data, when it has one worth reading. */
    static java.util.Optional<String> jobPostingText(String html) {
        if (html == null || !html.contains("ld+json")) return java.util.Optional.empty();
        Matcher m = JSON_LD.matcher(html);
        while (m.find()) {
            try {
                JsonNode posting = findJobPosting(JSON_LD_MAPPER.readTree(m.group(1).trim()));
                if (posting == null) continue;
                String text = renderJobPosting(posting);
                if (text.length() >= MIN_USEFUL_HTML_CHARS) return java.util.Optional.of(text);
            } catch (Exception e) {
                // A malformed block is common and harmless: try the next one, then the page.
            }
        }
        return java.util.Optional.empty();
    }

    private static JsonNode findJobPosting(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode n : node) {
                JsonNode found = findJobPosting(n);
                if (found != null) return found;
            }
            return null;
        }
        if (!node.isObject()) return null;
        JsonNode type = node.path("@type");
        boolean posting = type.isTextual()
                ? "JobPosting".equalsIgnoreCase(type.asText())
                : type.isArray() && java.util.stream.StreamSupport.stream(type.spliterator(), false)
                        .anyMatch(t -> "JobPosting".equalsIgnoreCase(t.asText()));
        if (posting) return node;
        return findJobPosting(node.get("@graph"));
    }

    /** The posting as plain text, its title on the first line (the application is named from it). */
    static String renderJobPosting(JsonNode p) {
        StringBuilder sb = new StringBuilder(plain(p.path("title").asText("")));
        labelled(sb, "Employer", plain(p.path("hiringOrganization").path("name").asText("")));
        JsonNode locations = p.path("jobLocation");
        java.util.List<String> places = new java.util.ArrayList<>();
        for (JsonNode loc : locations.isArray() ? locations : java.util.List.of(locations)) {
            String place = loc.path("address").path("addressLocality").asText("");
            if (!place.isBlank() && !places.contains(place)) places.add(plain(place));
        }
        labelled(sb, "Location", String.join(", ", places));
        JsonNode type = p.path("employmentType");
        labelled(sb, "Employment type", type.isArray()
                ? String.join(", ", java.util.stream.StreamSupport.stream(type.spliterator(), false)
                        .map(JsonNode::asText).toList())
                : type.asText(""));
        labelled(sb, "Apply by", p.path("validThrough").asText(""));
        for (String field : java.util.List.of("description", "responsibilities", "qualifications", "skills")) {
            String html = p.path(field).asText("");
            if (html.isBlank()) continue;
            // Often entity-escaped HTML inside the JSON string, so unescape before stripping tags.
            String text = htmlToText(html.contains("&lt;") ? unescapeEntities(html) : html);
            if (!text.isBlank()) sb.append("\n\n").append(text);
        }
        return sb.toString().strip();
    }

    private static void labelled(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) sb.append('\n').append(label).append(": ").append(value.strip());
    }

    private static String plain(String s) {
        return htmlToText(unescapeEntities(s == null ? "" : s));
    }

    /** Crude but dependency-free HTML → plain text. */
    private static String htmlToText(String html) {
        String s = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<noscript.*?</noscript>", " ")
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?i)<(br|/p|/div|/h[1-6]|/li|/tr|/section|/article)\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]+>", " ");
        s = unescapeEntities(s);
        s = s.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        return s;
    }

    /**
     * The handful of entities that actually turn up in a job advert.
     *
     * <p>Shared with the Greenhouse renderer, whose HTML arrives entity-escaped inside a
     * JSON string and so has to be unescaped BEFORE its tags can be stripped.
     */
    private static String unescapeEntities(String s) {
        return s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&rsquo;", "'").replace("&lsquo;", "'")
                .replace("&rdquo;", "\"").replace("&ldquo;", "\"")
                .replace("&ndash;", "–").replace("&mdash;", "—").replace("&hellip;", "…")
                .transform(UrlReadService::unescapeNumeric);
    }

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");

    /** {@code &#228;} and {@code &#xE4;} — how Swedish letters often arrive in a posting's JSON. */
    private static String unescapeNumeric(String s) {
        if (s.indexOf("&#") < 0) return s;
        Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String code = m.group(1);
            int cp;
            try {
                cp = code.startsWith("x") || code.startsWith("X")
                        ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
            } catch (NumberFormatException e) {
                cp = -1;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(
                    Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
