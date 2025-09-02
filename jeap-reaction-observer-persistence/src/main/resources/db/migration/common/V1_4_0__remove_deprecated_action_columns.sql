ALTER TABLE reaction DROP COLUMN action_id;
ALTER TABLE reaction DROP COLUMN action_type;
ALTER TABLE reaction DROP COLUMN action_fqn;

DROP INDEX observed_reactions_component_trigger_id;
ALTER TABLE observed_reactions_aggregated DROP COLUMN trigger_id;
ALTER TABLE observed_reactions_aggregated DROP COLUMN trigger_type;
ALTER TABLE observed_reactions_aggregated DROP COLUMN trigger_fqn;
ALTER TABLE observed_reactions_aggregated DROP COLUMN action_id;
ALTER TABLE observed_reactions_aggregated DROP COLUMN action_type;
ALTER TABLE observed_reactions_aggregated DROP COLUMN action_fqn;
