-- Ordering of work items inside their project: sort_order is a first-class,
-- mutable integer. Existing rows are backfilled in creation order, per project
-- and per TABLE independently — a project's tasks get 1..n, its work packages
-- get 1..m. The tiebreak is the id: two rows created in the same instant would
-- otherwise get a nondeterministic number.
--
-- No CHECK and no UNIQUE constraint (house rule: value rules live in the Java
-- layer). Ties break by created_at on read, and the lower bound is validated in
-- the service. Creates continue at max + 1 inside the project.

ALTER TABLE work_package ADD COLUMN sort_order INT;
ALTER TABLE task ADD COLUMN sort_order INT;

WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at ASC, id ASC) AS position
    FROM work_package
)
UPDATE work_package w
SET sort_order = ordered.position
FROM ordered
WHERE ordered.id = w.id;

WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at ASC, id ASC) AS position
    FROM task
)
UPDATE task t
SET sort_order = ordered.position
FROM ordered
WHERE ordered.id = t.id;

ALTER TABLE work_package ALTER COLUMN sort_order SET NOT NULL;
ALTER TABLE task ALTER COLUMN sort_order SET NOT NULL;
