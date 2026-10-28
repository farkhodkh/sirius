--liquibase formatted sql

--changeset farhod:001-processed-events
CREATE TABLE app.processed_events (
    event_id     uuid        PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

