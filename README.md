# homelabs-projects

Project-management backend for the homelab: projects, work packages, tasks, labels,
comments, activity and attachments behind a REST API. Backend only — a web UI is a
separate project that will consume this API.

Java 25, Spring Boot 4.1, PostgreSQL 17, Flyway, OAuth 2.0 resource server (JWT with
issuer and audience validation). Apache-2.0.

## Domain in one minute

- **project** — `short_code` (`HP`), name, sanitized HTML content, lifecycle
  `active → deactivated | archived`. `archived` makes the whole subtree read-only.
- **work package** / **task** — always inside a project; a task may be grouped into
  one of that project's work packages. Both draw their reference number
  (`HP-12`) from ONE per-project counter, so references are unique per project.
  Statuses: `todo`, `in_progress`, `blocked`, `done`, `cancelled`.
- **label** — created on demand at the first assignment, names unique
  case-insensitively, attached to all three levels with full-replacement semantics.
- **comment** — plain text on all three levels.
- **activity** — append-only log of every mutation (`created`, `updated`,
  `status_changed`, `labeled`, `unlabeled`, `commented`, `attached`, `detached`).
- **attachment** — metadata in the database, bytes on a filesystem volume under a
  RELATIVE storage key.

Content is canonical HTML, always sanitized against a server-side allowlist; the plain
text for search and list excerpts is derived from it. Writers may submit either plain
text (`description`) or markup (`description_html`).

## API

`GET /health` is open (container liveness). Everything under `/api/**` requires a
Bearer token.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/projects` | `short_code` optional → derived from the name; 409 + `suggestions` on collision |
| GET | `/api/projects` | filters `status?`, `label?`, `q?`, `limit?`; default hides deactivated/archived |
| GET | `/api/projects/{idOrCode}` | full record |
| PATCH | `/api/projects/{idOrCode}` | `name`, `description`/`description_html`, `labels` |
| POST | `/api/projects/{idOrCode}/deactivate` \| `/activate` \| `/archive` | explicit transitions |
| POST | `/api/projects/{idOrCode}/work-packages` | create inside the project |
| GET | `/api/work-packages` | filters `project?`, `status?`, `assignee?`, `q?`, `include_inactive_projects?`, `limit?` |
| GET / PATCH | `/api/work-packages/{idOrRef}` | |
| POST | `/api/tasks` | body carries `project` and optional `work_package_id` |
| GET | `/api/tasks` | filters `project?`, `wp?`, `status?`, `assignee?`, `due_before?`, `q?`, `limit?` |
| GET / PATCH | `/api/tasks/{idOrRef}` | |
| POST | `/api/comments` | `parent` + `text` |
| GET | `/api/comments?parent=…` | newest first |
| GET | `/api/activities?parent=…&limit=…` | newest first |
| POST | `/api/attachments` | multipart: `parent` + `file` |
| GET | `/api/attachments?parent=…` | metadata only |
| GET | `/api/attachments/{id}` | metadata |
| GET | `/api/attachments/{id}/content` | bytes with content type and a SHA-256 `ETag` |
| DELETE | `/api/attachments/{id}` | the one hard delete of the domain |
| GET | `/api/labels` | known labels, for the label filters |

Identifiers accept the UUID, a project short code (`HP`) or a reference (`HP-12`)
interchangeably. Parents of comments, activity and attachments are written as
`{entity_type}:{id-or-reference}` (`task:HP-12`); a bare `HP-12` resolves tasks first
and then work packages.

Lists return LEAN summaries — reference, title, status, assignee with display name,
due date and ~200 characters of the derived text; full content comes from the single
resource read. JSON field names are snake_case (`short_code`, `due_date`,
`description_html`) on every layer.

Errors answer one shape, `{"error": "..."}`: 400 validation with the offending field,
401 missing/invalid token, 404 unknown (a foreign identifier answers like an absent
one), 409 archived project or short-code collision (the collision body also carries
`suggestions`), 413 upload above the attachment cap.

## Configuration

All knobs have localhost defaults in `app/src/main/resources/application.yml`.

| Env var | Meaning |
|---|---|
| `PROJECTS_ISSUER_URI` | JWT issuer (default `http://localhost:8091/realms/homelabs`) |
| `PROJECTS_RESOURCE` | accepted token audience — a comma-separated list maps to `projects.api.audiences` |
| `PROJECTS_DB_PASSWORD` | database password |
| `PROJECTS_ATTACHMENT_ROOT` | volume root for attachment bytes |

## Build and run

```bash
mvn verify                 # unit tests (surefire) + *IT integration tests (failsafe, needs Docker)
mvn package -DskipTests
java -jar app/target/homelabs-projects-0.0.1-SNAPSHOT.jar
```

`mvn spring-boot:run` works here as well (no native code in this service). The service
needs a PostgreSQL 17 database; Flyway migrates it on startup (`pg_trgm` is a trusted
extension, so the database owner can create it).
