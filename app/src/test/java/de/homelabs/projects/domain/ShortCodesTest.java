package de.homelabs.projects.domain;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodesTest {

    @Test
    void derivesFromTheNameUppercasedAndStripped() {
        assertThat(ShortCodes.derive("Homelabs Projects")).isEqualTo("HOMELA");
        assertThat(ShortCodes.derive("mcp")).isEqualTo("MCP");
        assertThat(ShortCodes.derive("  pro jekt -- 2026 ")).isEqualTo("PROJEK");
    }

    @Test
    void derivedCandidateMayBeTooShortAndIsThenRejectedByTheCaller() {
        // nothing usable survives: the service demands an explicit code
        assertThat(ShortCodes.derive("ü ö")).isEmpty();
        assertThat(ShortCodes.derive("A")).isEqualTo("A");
    }

    @Test
    void normalizesToUpperCaseAndValidatesTheFormat() {
        assertThat(ShortCodes.normalize("hp")).isEqualTo("HP");
        assertThat(ShortCodes.normalize("  home2  ")).isEqualTo("HOME2");

        assertThatThrownBy(() -> ShortCodes.normalize("H"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2-6 characters");
        assertThatThrownBy(() -> ShortCodes.normalize("TOOLONG7"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShortCodes.normalize("H-P"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suggestsTheFirstFreeSuffixesOnCollision() {
        assertThat(ShortCodes.suggestions("HP", code -> false)).containsExactly("HP2", "HP3", "HP4");
        assertThat(ShortCodes.suggestions("hp", Set.of("HP2")::contains)).containsExactly("HP3", "HP4", "HP5");
    }

    @Test
    void shortensASixCharacterCodeBeforeSuffixing() {
        List<String> suggestions = ShortCodes.suggestions("HOMELA", code -> false);

        assertThat(suggestions).containsExactly("HOMEL2", "HOMEL3", "HOMEL4");
        assertThat(suggestions).allMatch(code -> code.length() <= 6);
    }
}
