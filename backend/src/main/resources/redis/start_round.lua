-- COUNTDOWN -> IN_ROUND: install a fresh round. The answer stays in Redis; clients never see it.
-- KEYS: 1 room, 2 round, 3 guesses, 4 attempts, 5 used answers, 6 players, 7 leaderboard
-- ARGV: 1 expected (previous) roundNo, 2 new roundNo, 3 roundId, 4 answer, 5 startedAt, 6 endsAt, 7 ttl seconds
-- Returns 1 if started, 0 if the room was not counting down to this round.
if redis.call('HGET', KEYS[1], 'status') ~= 'COUNTDOWN' or redis.call('HGET', KEYS[1], 'roundNo') ~= ARGV[1] then
  return 0
end
redis.call('DEL', KEYS[2], KEYS[3], KEYS[4])
redis.call('HSET', KEYS[2], 'roundId', ARGV[3], 'roundNo', ARGV[2], 'answer', ARGV[4],
  'startedAt', ARGV[5], 'endsAt', ARGV[6])
redis.call('HSET', KEYS[1], 'status', 'IN_ROUND', 'roundNo', ARGV[2], 'phaseEndsAt', ARGV[6])
redis.call('SADD', KEYS[5], ARGV[4])
for i = 1, #KEYS do
  redis.call('EXPIRE', KEYS[i], ARGV[7]) -- activity keeps the whole room alive
end
return 1
