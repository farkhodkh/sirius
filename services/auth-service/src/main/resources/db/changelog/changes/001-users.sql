--liquibase formatted sql

--changeset farhod:001-users
CREATE TABLE app.users (
    id             uuid         PRIMARY KEY,
    email          varchar(320) NOT NULL,
    password_hash  varchar(100),
    email_verified boolean      NOT NULL DEFAULT false,
    roles          varchar(100) NOT NULL DEFAULT 'USER',
    created_at     timestamptz  NOT NULL DEFAULT now()
);
-- уникальность e-mail без учёта регистра
CREATE UNIQUE INDEX uq_users_email ON app.users (lower(email));

--changeset farhod:002-email-tokens
CREATE TABLE app.email_tokens (
    token_hash char(64)    PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    purpose    varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz
);
CREATE INDEX ix_email_tokens_user ON app.email_tokens (user_id);
