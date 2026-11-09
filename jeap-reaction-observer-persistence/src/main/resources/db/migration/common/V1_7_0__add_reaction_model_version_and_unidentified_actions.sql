-- The reaction model version of the last identification of a reaction.
-- The default describes emitters that predate the reaction model version.
ALTER TABLE reaction ADD COLUMN last_identified_by_model_version INT NOT NULL DEFAULT 1;

-- Per count: the reaction model version of the emitter and the unidentified actions of the counted reactions.
-- The defaults describe emitters that predate these fields and do not detect unidentified actions.
ALTER TABLE observed_reaction ADD COLUMN reaction_model_version INT NOT NULL DEFAULT 1;
ALTER TABLE observed_reaction ADD COLUMN min_unidentified_actions INT NOT NULL DEFAULT 0;
ALTER TABLE observed_reaction ADD COLUMN observations_with_unidentified_actions INT NOT NULL DEFAULT 0;
ALTER TABLE observed_reaction ADD COLUMN unidentified_actions INT NOT NULL DEFAULT 0;
