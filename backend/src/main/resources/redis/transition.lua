-- Compare-and-set on the room's status: only one caller can move a room out of a given state,
-- so duplicate triggers (a timer firing twice, a double-clicked button) are harmless.
-- KEYS[1] room hash
-- ARGV[1] expected status, ARGV[2] expected roundNo ('' = any), ARGV[3..] field/value pairs to set
-- Returns 1 if applied, 0 if the room was not in the expected state.
if redis.call('HGET', KEYS[1], 'status') ~= ARGV[1] then
  return 0
end
if ARGV[2] ~= '' and redis.call('HGET', KEYS[1], 'roundNo') ~= ARGV[2] then
  return 0
end
redis.call('HSET', KEYS[1], unpack(ARGV, 3))
return 1
