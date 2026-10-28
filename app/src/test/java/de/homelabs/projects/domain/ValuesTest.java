package de.homelabs.projects.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuesTest {

    @Test
    void detectsUuids() {
        String uuid = "3f2c1b8e-1111-2222-3333-444455556666";

        assertThat(Values.parseUuid(uuid)).contains(UUID.fromString(uuid));
        assertThat(Values.parseUuid("HP-12")).isEmpty();
        assertThat(Values.parseUuid("HP")).isEmpty();
        assertThat(Values.parseUuid(null)).isEmpty();
    }

    @Test
    void parsesIsoDatesAndTreatsBlankAsAbsent() {
        assertThat(Values.parseDate("2026-11-01", "due_date")).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(Values.parseDate("  2026-11-01 ", "due_date")).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(Values.parseDate("", "due_date")).isNull();
        assertThat(Values.parseDate(null, "due_date")).isNull();

        assertThatThrownBy(() -> Values.parseDate("01.11.2026", "due_date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO date");
    }

    @Test
    void trimsBlankInputToNull() {
        assertThat(Values.trimToNull("  ")).isNull();
        assertThat(Values.trimToNull(null)).isNull();
        assertThat(Values.trimToNull(" sub-1 ")).isEqualTo("sub-1");
    }
}
