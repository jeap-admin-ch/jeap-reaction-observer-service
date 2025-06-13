CREATE SEQUENCE action_sequence START WITH 1 INCREMENT 10 CYCLE;
CREATE TABLE action
(
    id            BIGINT NOT NULL CONSTRAINT action_pkey PRIMARY KEY,
    reaction_id   BIGINT NOT NULL CONSTRAINT action_reaction_id_fkey REFERENCES reaction (id),
    action_id     VARCHAR(1024),
    action_type   VARCHAR(64),
    action_fqn    VARCHAR(1024)
);

ALTER TABLE observation_property
    ADD COLUMN action_fk BIGINT CONSTRAINT observation_property_action_fkey2 REFERENCES action (id) ON DELETE CASCADE;

CREATE INDEX action_reaction_id ON action (reaction_id);
CREATE INDEX observation_property_action_fk2 ON observation_property (action_fk);
