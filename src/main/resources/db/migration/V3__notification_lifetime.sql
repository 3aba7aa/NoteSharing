-- Notifications outlive the things they refer to.
--
-- user_relationship_id is dropped. A friend-request notification pointed at the request row,
-- but accepting or rejecting a request deletes that row -- so the notification either broke
-- the delete with a foreign key violation, or, with a nullable foreign key, became a dead
-- link. The notifier id and the message already say who and what; the request itself lives
-- on the friend-requests endpoints.
--
-- note_id stays, but with ON DELETE SET NULL. Deleting a note must not fail because someone
-- was once told about it, and a notification about a deleted note is still a true statement
-- about the past. The column goes null and the client renders it as history.

ALTER TABLE notification
    DROP COLUMN user_relationship_id;

-- The baseline declared this foreign key inline (REFERENCES note (id)), so PostgreSQL named
-- it itself. Both spellings are dropped: if the auto-generated one survived, the column would
-- end up with two constraints and the strict one would still block note deletion.
ALTER TABLE notification
    DROP CONSTRAINT IF EXISTS fk_notification_note;

ALTER TABLE notification
    DROP CONSTRAINT IF EXISTS notification_note_id_fkey;

ALTER TABLE notification
    ADD CONSTRAINT fk_notification_note
    FOREIGN KEY (note_id) REFERENCES note (id) ON DELETE SET NULL;

-- The inbox is always "my notifications, newest first", so that ordering is the hot path, and
-- the unread filter is almost always applied on top of it.
--
-- One composite index rather than a separate partial index on unread: H2 does not accept
-- `CREATE INDEX ... WHERE`, and a second index to serve one flag would not be worth a
-- per-database migration branch for the row counts this table sees.
CREATE INDEX idx_notification_notified_issued
    ON notification (notified_id, issue_date DESC);
