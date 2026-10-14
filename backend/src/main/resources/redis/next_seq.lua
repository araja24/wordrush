-- Next event sequence number for a room, or -1 if the room no longer exists
-- (HINCRBY alone would resurrect a deleted room as a stray hash with no TTL).
-- KEYS[1] room hash
if redis.call('EXISTS', KEYS[1]) == 0 then
  return -1
end
return redis.call('HINCRBY', KEYS[1], 'seq', 1)
