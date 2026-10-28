package de.homelabs.projects.mcp;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns an error response of the projects API into the text an agent sees.
 *
 * <p>The API answers every domain error as {@code {"error": "<message>"}} with 400, 404,
 * 409 or 413. A short-code collision (409) additionally carries {@code suggestions} —
 * alternative codes the API already knows to be free. Those must survive into the tool
 * error: an agent that cannot see the suggestions starts guessing codes and collides
 * again.
 */
final class ApiErrors {

    /** The API's message, with the suggestions appended when the body carries them. */
    static String message(ObjectMapper objectMapper, int status, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.get("error");
            if (error != null && !error.asString().isBlank()) {
                return withSuggestions(error.asString(), root.get("suggestions"));
            }
        } catch (RuntimeException unparseable) {
            // an unparseable error body is reported as-is below
        }
        return "projects API answered " + status + ": " + body;
    }

    /** Renders {@code suggestions: [A, B, C]} after the message; never invents one. */
    private static String withSuggestions(String message, JsonNode suggestions) {
        if (suggestions == null || !suggestions.isArray()) {
            return message;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode suggestion : suggestions) {
            String value = suggestion.asString();
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? message : message + "\nsuggestions: [" + String.join(", ", values) + "]";
    }

    private ApiErrors() {
    }
}
