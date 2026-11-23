-- The id of a reaction with a trigger starts with the id of its trigger, followed by "#" and the hash of its actions.
-- The id of an HTTP request trigger embeds its encoded FQN: "http_request:" followed by the FQN with %, :, # and &
-- percent-encoded. With FQNs of up to 512 characters a reaction id is up to 13 + 3 * 512 + 33 = 1582 characters long.
-- The column gets room for that and some reserve.
-- The length of the column is not the only limit: component and reaction_id together have to fit into one entry of
-- the unique index on (component, reaction_id), which PostgreSQL limits to about 2700 bytes. An id of 1582 bytes
-- leaves about 1100 bytes for the name of the component. Values that fit both columns can therefore still be
-- rejected by the index, e.g. an id of 2048 characters with a long component name or with multi-byte characters.
ALTER TABLE reaction ALTER COLUMN reaction_id SET DATA TYPE VARCHAR(2048);
