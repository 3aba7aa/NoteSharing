-- Baseline schema. Hibernate runs with ddl-auto=validate in the test and prod profiles,
-- so every column and constraint an entity declares must exist here.
-- Regenerate deliberately: changing an entity without a matching migration fails the build.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(255)     NOT NULL,
    hashed_password VARCHAR(255)     NOT NULL,
    email           VARCHAR(255)     NOT NULL UNIQUE,
    bod             DATE,
    created_at      TIMESTAMP        NOT NULL,
    gender          VARCHAR(255),
    image_data      BYTEA
);

CREATE TABLE user_settings (
    user_id               BIGINT PRIMARY KEY REFERENCES users (id),
    theme                 VARCHAR(255),
    two_factor_enabled    BOOLEAN,
    notification_enabled  BOOLEAN
);

CREATE TABLE note (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255),
    content          TEXT,
    created_at       TIMESTAMP NOT NULL,
    modified_at      TIMESTAMP,
    start_date       TIMESTAMP,
    end_date         TIMESTAMP,
    type             VARCHAR(255),
    visibility       VARCHAR(255),
    notify_threshold INTEGER,
    user_id          BIGINT NOT NULL REFERENCES users (id)
);

CREATE TABLE note_sharing (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    note_id     BIGINT      NOT NULL REFERENCES note (id),
    permission  VARCHAR(255) NOT NULL,
    CONSTRAINT uk_note_sharing_user_note UNIQUE (user_id, note_id)
);

CREATE TABLE user_relationship (
    id              BIGSERIAL PRIMARY KEY,
    first_user_id   BIGINT       NOT NULL REFERENCES users (id),
    second_user_id  BIGINT       NOT NULL REFERENCES users (id),
    type            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP,
    CONSTRAINT uk_user_relationship_pair UNIQUE (first_user_id, second_user_id)
);

CREATE TABLE two_factor (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    secret      VARCHAR(255) NOT NULL,
    enabled_at  TIMESTAMP   NOT NULL
);

CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    token_id    VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP   NOT NULL,
    revoked     BOOLEAN     NOT NULL
);

CREATE TABLE notification (
    id                    BIGSERIAL PRIMARY KEY,
    message               VARCHAR(255),
    type                  VARCHAR(255),
    issue_date            TIMESTAMP NOT NULL,
    read                  BOOLEAN    NOT NULL,
    notifier_id           BIGINT REFERENCES users (id),
    notified_id           BIGINT REFERENCES users (id),
    note_id               BIGINT REFERENCES note (id),
    user_relationship_id  BIGINT REFERENCES user_relationship (id)
);

CREATE INDEX idx_note_user_id ON note (user_id);
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
