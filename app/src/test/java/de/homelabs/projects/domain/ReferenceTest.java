package de.homelabs.projects.domain;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceTest {

    @Test
    void parsesCodeAndSequence() {
        assertThat(Reference.parse("HP-12")).isEqualTo(Optional.of(new Reference("HP", 12)));
        assertThat(Reference.parse(" hp-3 ")).isEqualTo(Optional.of(new Reference("HP", 3)));
        assertThat(Reference.parse("2026-1")).isEqualTo(Optional.of(new Reference("2026", 1)));
    }

    @Test
    void rejectsEverythingThatIsNotAReference() {
        assertThat(Reference.parse("HP")).isEmpty();
        assertThat(Reference.parse("H-1")).isEmpty();
        assertThat(Reference.parse("TOOLONG7-1")).isEmpty();
        assertThat(Reference.parse("HP-")).isEmpty();
        assertThat(Reference.parse("HP-x")).isEmpty();
        // a UUID must never be mistaken for a reference
        assertThat(Reference.parse("3f2c1b8e-1111-2222-3333-444455556666")).isEmpty();
        assertThat(Reference.parse(null)).isEmpty();
    }

    @Test
    void formatsCanonically() {
        assertThat(new Reference("HP", 12).format()).isEqualTo("HP-12");
    }
}
