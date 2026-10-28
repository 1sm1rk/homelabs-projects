-- Baseline schema of the project-management backend: projects, work packages,
-- tasks, labels (with their three join tables), comments, activity, attachments
-- and the local user directory. Status vocabularies are validated in the Java
-- layer only (no CHECK constraints): one vocabulary shared by schema, wire and
-- DB, changed in one place.

-- Lexical search: German FTS plus pg_trgm. pg_trgm has been a TRUSTED extension
-- since PostgreSQL 13, so the database owner creates it without a superuser.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Display-name directory: upserted from the JWT on every authenticated request,
-- so the service needs no Keycloak admin-API dependency.
CREATE TABLE app_user (
    sub           TEXT        PRIMARY KEY,
    username      TEXT        NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A project: the top of the tree. short_code is the human/agent handle that
-- prefixes every reference (HP, HP-12); next_seq is the single per-project
-- counter shared by work packages and tasks (cross-table uniqueness is enforced
-- in the service layer, per-table uniqueness by the indexes below).
CREATE TABLE project (
    id               UUID        PRIMARY KEY,
    short_code       TEXT        NOT NULL,
    name             TEXT        NOT NULL,
    description_html TEXT        NOT NULL DEFAULT '',
    description_text TEXT        NOT NULL DEFAULT '',
    status           TEXT        NOT NULL DEFAULT 'active',
    owner_sub        TEXT        NOT NULL,
    owner_name       TEXT        NOT NULL,
    next_seq         INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_text      TEXT        GENERATED ALWAYS AS (name || ' ' || description_text) STORED,
    search_tsv       TSVECTOR    GENERATED ALWAYS AS
        (to_tsvector('german', name || ' ' || description_text)) STORED
);

-- Case-insensitive short-code uniqueness (the service normalizes to upper case;
-- the functional index also protects rows written by hand).
CREATE UNIQUE INDEX uq_project_short_code ON project (upper(short_code));
CREATE INDEX idx_project_status ON project (status);
CREATE INDEX idx_project_created_at ON project (created_at DESC);
CREATE INDEX idx_project_search_tsv ON project USING GIN (search_tsv);
CREATE INDEX idx_project_search_trgm ON project USING GIN (search_text gin_trgm_ops);

-- A work package groups tasks; it always belongs to exactly one project.
CREATE TABLE work_package (
    id               UUID        PRIMARY KEY,
    project_id       UUID        NOT NULL REFERENCES project (id),
    title            TEXT        NOT NULL,
    description_html TEXT        NOT NULL DEFAULT '',
    description_text TEXT        NOT NULL DEFAULT '',
    status           TEXT        NOT NULL DEFAULT 'todo',
    assignee_sub     TEXT,
    due_date         DATE,
    seq              INT         NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_text      TEXT        GENERATED ALWAYS AS (title || ' ' || description_text) STORED,
    search_tsv       TSVECTOR    GENERATED ALWAYS AS
        (to_tsvector('german', title || ' ' || description_text)) STORED,
    UNIQUE (id, project_id)
);

CREATE UNIQUE INDEX uq_work_package_project_seq ON work_package (project_id, seq);
CREATE INDEX idx_work_package_project ON work_package (project_id);
CREATE INDEX idx_work_package_status ON work_package (status);
CREATE INDEX idx_work_package_assignee ON work_package (assignee_sub);
CREATE INDEX idx_work_package_due_date ON work_package (due_date);
CREATE INDEX idx_work_package_search_tsv ON work_package USING GIN (search_tsv);
CREATE INDEX idx_work_package_search_trgm ON work_package USING GIN (search_text gin_trgm_ops);

-- A task belongs to a project and may be regrouped into one of that project's
-- work packages; the composite foreign key keeps the pair consistent in the DB.
CREATE TABLE task (
    id               UUID        PRIMARY KEY,
    project_id       UUID        NOT NULL REFERENCES project (id),
    work_package_id  UUID,
    title            TEXT        NOT NULL,
    description_html TEXT        NOT NULL DEFAULT '',
    description_text TEXT        NOT NULL DEFAULT '',
    status           TEXT        NOT NULL DEFAULT 'todo',
    assignee_sub     TEXT,
    due_date         DATE,
    seq              INT         NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_text      TEXT        GENERATED ALWAYS AS (title || ' ' || description_text) STORED,
    search_tsv       TSVECTOR    GENERATED ALWAYS AS
        (to_tsvector('german', title || ' ' || description_text)) STORED,
    CONSTRAINT fk_task_work_package_same_project
        FOREIGN KEY (work_package_id, project_id) REFERENCES work_package (id, project_id)
);

CREATE UNIQUE INDEX uq_task_project_seq ON task (project_id, seq);
CREATE INDEX idx_task_project ON task (project_id);
CREATE INDEX idx_task_work_package ON task (work_package_id);
CREATE INDEX idx_task_status ON task (status);
CREATE INDEX idx_task_assignee ON task (assignee_sub);
CREATE INDEX idx_task_due_date ON task (due_date);
CREATE INDEX idx_task_search_tsv ON task USING GIN (search_tsv);
CREATE INDEX idx_task_search_trgm ON task USING GIN (search_text gin_trgm_ops);

-- One label entity for all three levels; FK integrity is preferred over a
-- polymorphic join table.
CREATE TABLE label (
    id         UUID        PRIMARY KEY,
    name       TEXT        NOT NULL,
    color      TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_label_name ON label (lower(name));

CREATE TABLE label_project (
    label_id   UUID NOT NULL REFERENCES label (id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    PRIMARY KEY (label_id, project_id)
);

CREATE INDEX idx_label_project_project ON label_project (project_id);

CREATE TABLE label_work_package (
    label_id       UUID NOT NULL REFERENCES label (id) ON DELETE CASCADE,
    work_package_id UUID NOT NULL REFERENCES work_package (id) ON DELETE CASCADE,
    PRIMARY KEY (label_id, work_package_id)
);

CREATE INDEX idx_label_work_package_item ON label_work_package (work_package_id);

CREATE TABLE label_task (
    label_id UUID NOT NULL REFERENCES label (id) ON DELETE CASCADE,
    task_id  UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    PRIMARY KEY (label_id, task_id)
);

CREATE INDEX idx_label_task_item ON label_task (task_id);

-- Comments: plain text, on all three levels. entity_id is polymorphic, so it
-- carries no foreign key (the service resolves the parent before inserting).
CREATE TABLE comment (
    id          UUID        PRIMARY KEY,
    entity_type TEXT        NOT NULL,
    entity_id   UUID        NOT NULL,
    author_sub  TEXT        NOT NULL,
    author_name TEXT        NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comment_entity ON comment (entity_type, entity_id, created_at DESC);

-- Activity log: append-only, one row per mutation. source is the harness client
-- identity forwarded by the MCP edge, null for direct API callers.
CREATE TABLE activity (
    id          UUID        PRIMARY KEY,
    entity_type TEXT        NOT NULL,
    entity_id   UUID        NOT NULL,
    actor_sub   TEXT        NOT NULL,
    actor_name  TEXT        NOT NULL,
    source      TEXT,
    event_type  TEXT        NOT NULL,
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_activity_entity ON activity (entity_type, entity_id, created_at DESC);

-- Attachments: metadata row here, bytes on the filesystem volume. storage_key is
-- RELATIVE to the configured root (never an absolute path in the DB).
CREATE TABLE attachment (
    id               UUID        PRIMARY KEY,
    entity_type      TEXT        NOT NULL,
    entity_id        UUID        NOT NULL,
    filename         TEXT        NOT NULL,
    mime_type        TEXT        NOT NULL,
    byte_size        BIGINT      NOT NULL,
    sha256           TEXT        NOT NULL,
    storage_key      TEXT        NOT NULL,
    uploaded_by_sub  TEXT        NOT NULL,
    uploaded_by_name TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachment_entity ON attachment (entity_type, entity_id, created_at DESC);
CREATE UNIQUE INDEX uq_attachment_storage_key ON attachment (storage_key);
