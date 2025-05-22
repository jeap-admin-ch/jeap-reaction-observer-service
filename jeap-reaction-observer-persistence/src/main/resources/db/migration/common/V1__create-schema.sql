-- identified reactions
CREATE SEQUENCE reaction_sequence START WITH 1 INCREMENT 10 CYCLE;
CREATE TABLE reaction
(
    id            BIGINT                   NOT NULL
        CONSTRAINT reaction_pkey PRIMARY KEY,
    component     VARCHAR(1024)            NOT NULL,
    reaction_id   VARCHAR(1024)            NOT NULL,
    trigger_id    VARCHAR(1024),
    trigger_type  VARCHAR(64),
    trigger_fqn   VARCHAR(1024),
    action_id     VARCHAR(1024),
    action_type   VARCHAR(64),
    action_fqn    VARCHAR(1024),
    identified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (component, reaction_id)
);
CREATE INDEX reaction_component_reaction_id ON reaction (component, reaction_id);

-- observation properties
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

-- observed reaction counts within a certain timeframe (not aggregated)
CREATE SEQUENCE observed_reaction_sequence START WITH 1 INCREMENT 10 CYCLE;
CREATE TABLE observed_reaction
(
    id               BIGINT                   NOT NULL
        CONSTRAINT observed_reaction_pkey PRIMARY KEY,
    reaction_fk      BIGINT                   NOT NULL
        CONSTRAINT observed_reaction_reaction_fk_fkey REFERENCES reaction (id) ON DELETE CASCADE,
    idempotence_id   VARCHAR(1024)            NOT NULL,
    timeframe_start  TIMESTAMP WITH TIME ZONE NOT NULL,
    timeframe_end    TIMESTAMP WITH TIME ZONE NOT NULL,
    observation_date DATE                     NOT NULL,
    count            INT                      NOT NULL
);

-- observed reactions aggregated counts within a certain day
CREATE SEQUENCE observed_reactions_aggregated_sequence START WITH 1 INCREMENT 10 CYCLE;
CREATE TABLE observed_reactions_aggregated
(
    id              BIGINT DEFAULT nextval('observed_reactions_aggregated_sequence') NOT NULL
        CONSTRAINT observed_reactions_aggregated_pkey PRIMARY KEY,
    reaction_fk     BIGINT                   NOT NULL
        CONSTRAINT observed_reactions_aggregated_reaction_fk_fkey REFERENCES reaction (id) ON DELETE CASCADE,
    component       VARCHAR(1024)            NOT NULL,
    trigger_id      VARCHAR(1024),
    trigger_type    VARCHAR(64),
    trigger_fqn     VARCHAR(1024),
    action_id       VARCHAR(1024),
    action_type     VARCHAR(64),
    action_fqn      VARCHAR(1024),
    date            DATE                     NOT NULL,
    count           INT                      NOT NULL
);
