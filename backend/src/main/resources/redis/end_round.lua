-- IN_ROUND -> ROUND_RESULTS when the round timer fires, unless a guess already ended it.
-- KEYS: 1 room, 2 round
-- ARGV: 1 roundId the timer was set for, 2 when the results pause ends (epoch ms)
-- Returns 1 if this call ended the round, 0 otherwise.
if redis.call('HGET', KEYS[1], 'status') ~= 'IN_ROUND' or redis.call('HGET', KEYS[2], 'roundId') ~= ARGV[1] then
  return 0
end
redis.call('HSET', KEYS[2], 'endReason', 'TIMEOUT')
redis.call('HSET', KEYS[1], 'status', 'ROUND_RESULTS', 'phaseEndsAt', ARGV[2])
return 1
