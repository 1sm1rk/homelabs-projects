package de.homelabs.projects.mcp;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error text an agent sees: the API's message verbatim, with the short-code
 * suggestions appended when the body carries them — an agent that cannot see the
 * suggestions starts guessing codes and collides again.
 */
class ApiErrorsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void keepsTheApiMessageForEveryPlainErrorShape() {
        assertThat(ApiErrors.message(mapper, 404, "{\"error\":\"no project UNKNOWN\"}"))
                .isEqualTo("no project UNKNOWN");
        assertThat(ApiErrors.message(mapper, 409,
                "{\"error\":\"project HP is archived: the project and everything under it is read-only\"}"))
                .isEqualTo("project HP is archived: the project and everything under it is read-only");
        assertThat(ApiErrors.message(mapper, 400, "{\"error\":\"title: must not be blank\"}"))
                .isEqualTo("title: must not be blank");
        assertThat(ApiErrors.message(mapper, 413, "{\"error\":\"upload exceeds the maximum request size\"}"))
                .isEqualTo("upload exceeds the maximum request size");
    }

    @Test
    void appendsTheSuggestionsOfAShortCodeCollision() {
        assertThat(ApiErrors.message(mapper, 409,
                "{\"error\":\"short code 'HP' is already taken\",\"suggestions\":[\"HPX\",\"HPM\",\"HP2\"]}"))
                .isEqualTo("short code 'HP' is already taken\nsuggestions: [HPX, HPM, HP2]");
    }

    @Test
    void ignoresAnEmptyOrForeignSuggestionsValue() {
        assertThat(ApiErrors.message(mapper, 409, "{\"error\":\"collision\",\"suggestions\":[]}"))
                .isEqualTo("collision");
        assertThat(ApiErrors.message(mapper, 409, "{\"error\":\"collision\",\"suggestions\":\"HPX\"}"))
                .isEqualTo("collision");
        assertThat(ApiErrors.message(mapper, 409, "{\"error\":\"collision\",\"suggestions\":[null,\" \"]}"))
                .isEqualTo("collision");
    }

    @Test
    void fallsBackToStatusAndBodyWhenTheBodyIsNotTheApiShape() {
        assertThat(ApiErrors.message(mapper, 500, "upstream exploded"))
                .isEqualTo("projects API answered 500: upstream exploded");
        assertThat(ApiErrors.message(mapper, 502, "{\"message\":\"gateway\"}"))
                .isEqualTo("projects API answered 502: {\"message\":\"gateway\"}");
        assertThat(ApiErrors.message(mapper, 500, "{\"error\":\"   \"}"))
                .isEqualTo("projects API answered 500: {\"error\":\"   \"}");
        assertThat(ApiErrors.message(mapper, 502, ""))
                .isEqualTo("projects API answered 502: ");
    }
}
