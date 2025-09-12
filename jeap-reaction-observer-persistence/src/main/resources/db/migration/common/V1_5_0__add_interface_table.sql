CREATE SEQUENCE interface_sequence START WITH 1 INCREMENT BY 10 CYCLE;
CREATE TABLE interface
(
    id   BIGINT       NOT NULL
        CONSTRAINT interface_pkey PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    fqn  VARCHAR(255) NOT NULL,
    CONSTRAINT unique_type_fqn UNIQUE (type, fqn)
);

CREATE INDEX idx_interface_type_fqn ON interface (type, fqn);

-- New column interface_id in reaction und action
ALTER TABLE reaction
    ADD COLUMN interface_id BIGINT;
ALTER TABLE action
    ADD COLUMN interface_id BIGINT;

ALTER TABLE reaction
    ADD CONSTRAINT fk_reaction_interface FOREIGN KEY (interface_id) REFERENCES interface (id);
ALTER TABLE action
    ADD CONSTRAINT fk_action_interface FOREIGN KEY (interface_id) REFERENCES interface (id);


-- Data migration: Insert unique trigger interfaces from reaction
INSERT INTO interface (id, type, fqn)
SELECT nextval('interface_sequence'), r.trigger_type, r.trigger_fqn
FROM (SELECT DISTINCT trigger_type, trigger_fqn
      FROM reaction
      WHERE trigger_type IS NOT NULL
        AND trigger_fqn IS NOT NULL) r;

-- Data migration: Insert unique action interfaces from action
INSERT INTO interface (id, type, fqn)
SELECT nextval('interface_sequence'), a.action_type, a.action_fqn
FROM (SELECT DISTINCT action_type, action_fqn
      FROM action
      WHERE action_type IS NOT NULL
        AND action_fqn IS NOT NULL) a
WHERE NOT EXISTS (SELECT 1
                  FROM interface i
                  WHERE i.type = a.action_type
                    AND i.fqn = a.action_fqn);


-- Data migration: Update reaction.interface_id
UPDATE reaction
SET interface_id = i.id FROM interface i
WHERE reaction.trigger_type = i.type AND reaction.trigger_fqn = i.fqn;

-- Data migration: Update action.interface_id
UPDATE action
SET interface_id = i.id FROM interface i
WHERE action.action_type = i.type AND action.action_fqn = i.fqn;

-- Remove old columns
ALTER TABLE reaction DROP COLUMN trigger_type;
ALTER TABLE reaction DROP COLUMN trigger_fqn;

ALTER TABLE action DROP COLUMN action_type;
ALTER TABLE action DROP COLUMN action_fqn;
