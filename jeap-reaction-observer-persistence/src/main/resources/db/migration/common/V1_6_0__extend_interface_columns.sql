-- REST FQNs can be longer than 255 characters; the reaction observer library cuts FQNs at 512 characters
ALTER TABLE interface
    ALTER COLUMN fqn TYPE VARCHAR(1024),
    ALTER COLUMN type TYPE VARCHAR(1024);

-- The id of an observation embeds its (encoded) FQN, e.g. "http_request:" followed by the FQN with %, :, # and &
-- percent-encoded, so it can be longer than the FQN itself
ALTER TABLE reaction
    ALTER COLUMN trigger_id TYPE VARCHAR(4096);
ALTER TABLE action
    ALTER COLUMN action_id TYPE VARCHAR(4096);
