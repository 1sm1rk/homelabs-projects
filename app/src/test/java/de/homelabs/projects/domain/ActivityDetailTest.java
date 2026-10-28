package de.homelabs.projects.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityDetailTest {

    @Test
    void composesTheUpdatedSummaryWithScalarValuesInline() {
        String detail = ActivityDetail.changed(List.of(
                new ActivityDetail.Change("title", "Neuer Titel"),
                new ActivityDetail.Change("due_date", "2026-11-01"),
                new ActivityDetail.Change("description", null)));

        assertThat(detail).isEqualTo("title='Neuer Titel', due_date='2026-11-01', description");
    }

    @Test
    void anEmptyChangeSetHasNoDetail() {
        assertThat(ActivityDetail.changed(List.of())).isNull();
        assertThat(ActivityDetail.changed(null)).isNull();
    }

    @Test
    void composesStatusTransitions() {
        assertThat(ActivityDetail.transition("active", "deactivated")).isEqualTo("active → deactivated");
    }

    @Test
    void joinsLabelNames() {
        assertThat(ActivityDetail.names(List.of("infra", "mcp"))).isEqualTo("infra, mcp");
        assertThat(ActivityDetail.names(List.of())).isNull();
    }
}
