-- Atomically add a player. Check-then-act in Java could let two players take the last seat;
-- inside one script nothing else runs between the capacity check and the write.
-- KEYS[1] room hash, KEYS[2] players hash
-- ARGV[1] userId, ARGV[2] player JSON, ARGV[3] ttl seconds
-- Returns JOINED | ALREADY_MEMBER | NOT_FOUND | NOT_JOINABLE | FULL
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 'NOT_FOUND'
end
if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 then
  return 'ALREADY_MEMBER' -- idempotent: rejoining (e.g. after a refresh) is not an error
end
if redis.call('HGET', KEYS[1], 'status') ~= 'LOBBY' then
  return 'NOT_JOINABLE'
end
if redis.call('HLEN', KEYS[2]) >= tonumber(redis.call('HGET', KEYS[1], 'maxPlayers')) then
  return 'FULL'
end
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
redis.call('HINCRBY', KEYS[1], 'seq', 1)
redis.call('EXPIRE', KEYS[1], ARGV[3])
redis.call('EXPIRE', KEYS[2], ARGV[3])
return 'JOINED'
