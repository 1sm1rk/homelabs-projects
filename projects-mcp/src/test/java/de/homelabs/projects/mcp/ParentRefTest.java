package de.homelabs.projects.mcp;

import org.junit.jupiter.api.Test;

import de.homelabs.projects.mcp.dto.EntityType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The parent grammar of the API: {@code {entity_type}:{id-or-reference}}. The tool surface
 * keeps the two apart; only the client collapses them into the one string.
 */
class ParentRefTest {

    @Test
    void composesTheTypedFormForEveryLevel() {
        assertThat(ParentRef.compose(EntityType.task, "HP-12")).isEqualTo("task:HP-12");
        assertThat(ParentRef.compose(EntityType.work_package, "HP-12")).isEqualTo("work_package:HP-12");
        assertThat(ParentRef.compose(EntityType.project, "HP")).isEqualTo("project:HP");
        assertThat(ParentRef.compose(EntityType.task, "44444444-4444-4444-4444-444444444444"))
                .isEqualTo("task:44444444-4444-4444-4444-444444444444");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(ParentRef.compose(EntityType.project, "  HP  ")).isEqualTo("project:HP");
    }

    @Test
    void rejectsAMissingIdentifierBeforeAnyCall() {
        assertThatThrownBy(() -> ParentRef.compose(EntityType.task, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity_id is required");
        assertThatThrownBy(() -> ParentRef.compose(EntityType.task, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity_id is required");
        assertThatThrownBy(() -> ParentRef.compose(EntityType.task, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity_id is required");
    }

    @Test
    void rejectsAMissingLevel() {
        assertThatThrownBy(() -> ParentRef.compose(null, "HP-12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity_type is required");
    }
}
