CREATE SEQUENCE reaction_sequence START WITH 1 INCREMENT 10 CYCLE;
CREATE TABLE reaction
(
    id            BIGINT                   NOT NULL
        CONSTRAINT reaction_pkey PRIMARY KEY,
    component     VARCHAR(1024)            NOT NULL,
    reaction_id   VARCHAR(1024)            NOT NULL,
    trigger_type  VARCHAR(64),
    trigger_fqn   VARCHAR(1024),
    action_type   VARCHAR(64),
    action_fqn    VARCHAR(1024),
    identified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (component, reaction_id)
);
CREATE INDEX reaction_component_reaction_id ON reaction (component, reaction_id);

CREATE SEQUENCE observation_property_sequence START WITH 1 INCREMENT 10 CYCLE;
CREATE TABLE observation_property
(
    id                  BIGINT        NOT NULL
        CONSTRAINT observation_property_pkey PRIMARY KEY,
    reaction_trigger_fk BIGINT
        CONSTRAINT observation_property_trigger_fkey REFERENCES reaction (id) ON DELETE CASCADE,
    reaction_action_fk  BIGINT
        CONSTRAINT observation_property_action_fkey REFERENCES reaction (id) ON DELETE CASCADE,
    property_key        VARCHAR(1024) NOT NULL,
    property_value      VARCHAR(1024) NOT NULL
);
CREATE INDEX observation_property_trigger_fk ON observation_property (reaction_trigger_fk);
CREATE INDEX observation_property_action_fk ON observation_property (reaction_action_fk);
