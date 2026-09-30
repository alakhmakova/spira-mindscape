package com.spiramindscape.backend.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Field-level edits to a vacancy map's JSON document.
 *
 * <p><strong>Why this exists rather than a whole-document write.</strong> Two writers share one
 * map: the user, typing into the page, and the CV writer, filling in what it read from the advert.
 * When the unit of writing is the whole document, the later write wins and silently discards the
 * other's work — which is exactly what the requirement-map NOTE did (owner, 2026-09-17: the agent
 * "should fill each field separately, not overwrite the whole resource the way it did with the
 * note"). A patch names one field, so two writers touching different fields never collide, and an
 * agent physically cannot clobber an answer it did not address.
 *
 * <p>Paths are JSON Pointer (RFC 6901): {@code /facts/location}, {@code /requirements/2/important},
 * {@code /skills/0/comments/-}. A trailing {@code -} appends to an array — the one addition to the
 * pointer syntax, borrowed from JSON Patch, so adding a checklist item does not require the client
 * to know how many there already are (it may be racing another writer that just added one).
 *
 * <p>Missing containers along the way are created, taking their shape from the NEXT token: a
 * numeric token or {@code -} makes an array, anything else an object. So the page can write
 * {@code /company/comments/-} into a document that has no {@code company} yet, and a map created
 * empty needs no seeding.
 *
 * <p>Pure: no entity, no repository, no clock. {@link ResourceService} owns the size limit and the
 * ownership check.
 */
public final class VacancyMapPatch {

    /**
     * One edit.
     *
     * @param path  JSON Pointer to the field, e.g. {@code /facts/location}
     * @param value the new value as JSON ({@code "\"Stockholm\""}, {@code "true"}, an object or an
     *              array). {@code null} REMOVES the field — an object key is dropped, an array
     *              element spliced out. That is how the page deletes a skill or a requirement.
     */
    public record Patch(String path, String value) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Guards a pathological path from building a deep tree of empty containers. */
    private static final int MAX_DEPTH = 12;

    private VacancyMapPatch() {
    }

    /**
     * Applies every patch in order and returns the new document.
     *
     * @param document the stored JSON, or null/blank for a map that has never been written to
     * @throws IllegalArgumentException if the stored document or a patch value is not valid JSON,
     *                                  or a path is malformed
     */
    public static String apply(String document, List<Patch> patches) {
        JsonNode root = parseDocument(document);
        if (patches == null || patches.isEmpty()) {
            return write(root);
        }
        ObjectNode mutableRoot = root instanceof ObjectNode object ? object : MAPPER.createObjectNode();
        for (Patch patch : patches) {
            applyOne(mutableRoot, patch);
        }
        return write(mutableRoot);
    }

    /**
     * What is at {@code path} in the stored document, or {@code null} when nothing is.
     *
     * <p>How the CV coach's writes are checked before they are applied: a field the user has
     * filled in is not changed without her say-so, and that needs the field's current value.
     * An append path ({@code /skills/-}) names no existing value, so it reads as {@code null}.
     */
    public static JsonNode read(String document, String path) {
        JsonNode node = parseDocument(document);
        for (String token : tokens(path)) {
            if (node == null) return null;
            if (node.isObject()) {
                node = node.get(token);
            } else if (node.isArray()) {
                if (!isIndex(token)) return null;
                int index = Integer.parseInt(token);
                node = index < node.size() ? node.get(index) : null;
            } else {
                return null;
            }
        }
        return node == null || node.isNull() || node.isMissingNode() ? null : node;
    }

    /** The document as a tree, for callers that only read it. Blank reads as an empty object. */
    public static JsonNode tree(String document) {
        return parseDocument(document);
    }

    private static JsonNode parseDocument(String document) {
        if (document == null || document.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(document);
        } catch (JsonProcessingException e) {
            // The column holds only what this class wrote, so this means the row is corrupt.
            // Failing loudly beats silently starting a second, empty map over the top of it.
            throw new IllegalArgumentException("Vacancy map document is not valid JSON");
        }
    }

    private static void applyOne(ObjectNode root, Patch patch) {
        List<String> tokens = tokens(patch.path());
        JsonNode parent = root;
        for (int i = 0; i < tokens.size() - 1; i++) {
            parent = descend(parent, tokens.get(i), tokens.get(i + 1));
        }
        String last = tokens.get(tokens.size() - 1);
        JsonNode value = patch.value() == null ? null : parseValue(patch.value());
        if (parent instanceof ObjectNode object) {
            setOnObject(object, last, value);
        } else if (parent instanceof ArrayNode array) {
            setOnArray(array, last, value);
        } else {
            throw new IllegalArgumentException(
                    "Vacancy map path '" + patch.path() + "' runs through a value, not a container");
        }
    }

    private static void setOnObject(ObjectNode object, String field, JsonNode value) {
        if (value == null) {
            object.remove(field);
        } else {
            object.set(field, value);
        }
    }

    private static void setOnArray(ArrayNode array, String token, JsonNode value) {
        if ("-".equals(token)) {
            if (value == null) {
                throw new IllegalArgumentException("Cannot remove at '-': it names the end of the array");
            }
            array.add(value);
            return;
        }
        int index = index(token);
        if (index >= array.size()) {
            // Only an append is meaningful past the end; a gap would have to be filled with nulls,
            // and every caller that lands here meant "add this one".
            if (value == null) return;
            array.add(value);
            return;
        }
        if (value == null) {
            array.remove(index);
        } else {
            array.set(index, value);
        }
    }

    /** Steps into {@code token}, creating the container it names when it is missing. */
    private static JsonNode descend(JsonNode parent, String token, String next) {
        if (parent instanceof ObjectNode object) {
            JsonNode child = object.get(token);
            if (child == null || child.isNull()) {
                child = emptyFor(next);
                object.set(token, child);
            }
            return child;
        }
        if (parent instanceof ArrayNode array) {
            if ("-".equals(token)) {
                JsonNode child = emptyFor(next);
                array.add(child);
                return child;
            }
            int index = index(token);
            if (index >= array.size()) {
                JsonNode child = emptyFor(next);
                array.add(child);
                return child;
            }
            JsonNode child = array.get(index);
            if (child == null || child.isNull()) {
                child = emptyFor(next);
                array.set(index, child);
            }
            return child;
        }
        throw new IllegalArgumentException("Vacancy map path runs through a value, not a container");
    }

    /** An array when the next step indexes one, an object otherwise. */
    private static JsonNode emptyFor(String next) {
        return "-".equals(next) || isIndex(next) ? MAPPER.createArrayNode() : MAPPER.createObjectNode();
    }

    private static List<String> tokens(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("Vacancy map path must start with '/'");
        }
        // -1: keep trailing empty tokens so "/a/" is rejected below rather than silently becoming "/a".
        String[] raw = path.substring(1).split("/", -1);
        if (raw.length > MAX_DEPTH) {
            throw new IllegalArgumentException("Vacancy map path is too deep");
        }
        for (String token : raw) {
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Vacancy map path has an empty segment: " + path);
            }
        }
        // RFC 6901 escaping, in this order: ~1 is a slash, ~0 a tilde. Unescaping ~0 first would
        // turn "~01" into "~1" and then into "/".
        return List.of(raw).stream()
                .map(token -> token.replace("~1", "/").replace("~0", "~"))
                .toList();
    }

    private static boolean isIndex(String token) {
        if (token.isEmpty() || token.length() > 9) return false;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    private static int index(String token) {
        if (!isIndex(token)) {
            throw new IllegalArgumentException("Vacancy map array index must be a number or '-': " + token);
        }
        return Integer.parseInt(token);
    }

    private static JsonNode parseValue(String value) {
        try {
            return MAPPER.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Vacancy map patch value is not valid JSON");
        }
    }

    private static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Vacancy map document could not be written");
        }
    }
}
