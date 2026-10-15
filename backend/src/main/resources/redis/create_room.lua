-- Atomically reserve a room code and seat the host.
-- KEYS[1] room hash, KEYS[2] players hash, KEYS[3..] the room's other keys
-- ARGV[1] hostId, ARGV[2] host player JSON, ARGV[3] ttl seconds, ARGV[4..] room hash field/value pairs
-- Returns 1 if created, 0 if the code is already taken.
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
-- The code is free, but an expired room may have left keys behind: start clean.
redis.call('DEL', unpack(KEYS))
redis.call('HSET', KEYS[1], unpack(ARGV, 4))
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
redis.call('EXPIRE', KEYS[1], ARGV[3])
redis.call('EXPIRE', KEYS[2], ARGV[3])
return 1
