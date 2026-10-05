-- Relationships become explicitly directional.
--
-- The baseline constrained only (first_user_id, second_user_id). Two problems followed from
-- that. A pair could not hold more than one kind of relationship in the same direction, so a
-- block and a pending request between the same two people could not both exist; and because
-- one row had to mean several things, direction was encoded in the enum names themselves
-- (FRIENDREQ12, FRIENDREQ21, BLOCKED12, BLOCKED21). The columns already say which way a row
-- points, so the suffixes were redundant.
--
-- Adding `type` to the constraint lets one direction carry more than one kind of
-- relationship, and the Java enum drops the numeric suffixes.
--
-- Friendship stays symmetric by writing a row in each direction rather than canonicalising
-- the pair, because then the uniqueness constraint is doing the deduplication instead of
-- application code, and two concurrent accepts cannot produce two friendships.

ALTER TABLE user_relationship
    DROP CONSTRAINT uk_user_relationship_pair;

ALTER TABLE user_relationship
    ADD CONSTRAINT uk_user_relationship_pair UNIQUE (first_user_id, second_user_id, type);

-- Friend lookups and block checks are all "rows pointing away from me of one kind", which is
-- a first_user_id + type scan. Without these the friends list reads the whole table.
CREATE INDEX idx_user_relationship_first_type
    ON user_relationship (first_user_id, type);

-- Inbox and outgoing-request listings filter on the other side of the pair.
CREATE INDEX idx_user_relationship_second_type
    ON user_relationship (second_user_id, type);
