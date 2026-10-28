package de.homelabs.projects.mcp;

/**
 * Snake_case response bodies as the REST API produces them, shared by the ITs. They are
 * the full records the endpoints return — parsing them component for component is what
 * proves the adapter's DTO field names match the wire.
 */
final class WireFixtures {

    static final String PROJECT = """
            {"id":"11111111-1111-1111-1111-111111111111","short_code":"HP","name":"Homelabs Platform",
             "description_html":"<p>Infrastructure work</p>","description_text":"Infrastructure work",
             "status":"active","owner_sub":"user-1","owner_name":"owner",
             "created_at":"2026-10-28T09:00:00Z","updated_at":"2026-10-28T09:30:00Z",
             "labels":[{"id":"22222222-2222-2222-2222-222222222222","name":"infra","color":null}]}""";

    static final String WORK_PACKAGE = """
            {"id":"33333333-3333-3333-3333-333333333333","reference":"HP-12",
             "project_id":"11111111-1111-1111-1111-111111111111","project_short_code":"HP",
             "title":"Adapter module","description_html":"<p>twenty tools</p>","description_text":"twenty tools",
             "status":"todo","assignee_sub":null,"assignee_name":null,"due_date":"2026-11-15","seq":12,
             "created_at":"2026-10-28T09:00:00Z","updated_at":"2026-10-28T09:30:00Z","labels":[]}""";

    static final String TASK = """
            {"id":"44444444-4444-4444-4444-444444444444","reference":"HP-13",
             "project_id":"11111111-1111-1111-1111-111111111111","project_short_code":"HP",
             "work_package_id":"33333333-3333-3333-3333-333333333333","work_package_reference":"HP-12",
             "title":"Write the client","description_html":"<p>body</p>","description_text":"body",
             "status":"in_progress","assignee_sub":"user-1","assignee_name":"owner","due_date":"2026-11-01",
             "seq":13,"created_at":"2026-10-28T09:00:00Z","updated_at":"2026-10-28T09:40:00Z",
             "labels":[{"id":"22222222-2222-2222-2222-222222222222","name":"infra","color":"blue"}]}""";

    private WireFixtures() {
    }
}
