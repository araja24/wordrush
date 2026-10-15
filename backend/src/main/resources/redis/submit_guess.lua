-- Records one guess atomically. This script is the linearization point for "who solved first":
-- Redis runs one script at a time, so of two correct guesses arriving in the same millisecond
-- (even from different app instances) exactly one sees status IN_ROUND and wins.
--
-- KEYS: 1 room, 2 round, 3 players, 4 guesses, 5 attempts, 6 leaderboard,
--       7 idempotency key (scoped to this user and guessId)
-- ARGV: 1 userId, 2 roundId, 3 word, 4 feedback, 5 correct ('1'|'0'), 6 now (epoch ms),
--       7 resultsEndsAt, 8 idempotency ttl seconds, 9 max attempts, 10..15 points for winning on attempt 1..6
-- Returns "replay|OUTCOME|attempt|points|roundEnded|msBehind|word|feedback"
--   word/feedback are the COMMITTED values, empty when the guess was not counted.
--   On a replay they come from the cache, never from this request: otherwise resending an old
--   guessId with new words would reveal their colours without spending attempts (an answer oracle).
local cached = redis.call('GET', KEYS[7])
if cached then
  return '1|' .. cached
end

local status = redis.call('HGET', KEYS[1], 'status')
local roundId = redis.call('HGET', KEYS[2], 'roundId')
if status ~= 'IN_ROUND' or roundId ~= ARGV[2] then
  if roundId == ARGV[2] and ARGV[5] == '1' and redis.call('HGET', KEYS[2], 'endReason') == 'SOLVED' then
    -- Correct, but someone else's correct guess committed first. Report how close it was.
    local behind = tonumber(ARGV[6]) - tonumber(redis.call('HGET', KEYS[2], 'winnerAt'))
    return '0|CORRECT_LATE|0|0|0|' .. math.max(behind, 0) .. '||'
  end
  return '0|ROUND_OVER|0|0|0|0||'
end
if tonumber(ARGV[6]) > tonumber(redis.call('HGET', KEYS[2], 'endsAt')) then
  return '0|ROUND_OVER|0|0|0|0||' -- past the deadline; the round timer is about to end it
end
if redis.call('HEXISTS', KEYS[3], ARGV[1]) == 0 then
  return '0|NOT_A_MEMBER|0|0|0|0||'
end
local max = tonumber(ARGV[9])
local used = tonumber(redis.call('HGET', KEYS[5], ARGV[1]) or '0')
if used >= max then
  return '0|OUT_OF_GUESSES|' .. used .. '|0|0|0||'
end

local attempt = redis.call('HINCRBY', KEYS[5], ARGV[1], 1)
local entry = ARGV[3] .. ':' .. ARGV[4]
local previous = redis.call('HGET', KEYS[4], ARGV[1])
redis.call('HSET', KEYS[4], ARGV[1], previous and (previous .. ',' .. entry) or entry)
-- These two hashes are first created here, so they need the room's TTL or they'd never expire.
local ttl = redis.call('PTTL', KEYS[1])
if ttl > 0 then
  redis.call('PEXPIRE', KEYS[4], ttl)
  redis.call('PEXPIRE', KEYS[5], ttl)
end

local result
if ARGV[5] == '1' then
  local points = tonumber(ARGV[9 + attempt])
  redis.call('HSET', KEYS[2], 'winnerId', ARGV[1], 'winnerAttempts', attempt, 'winnerAt', ARGV[6],
    'endReason', 'SOLVED')
  redis.call('HSET', KEYS[1], 'status', 'ROUND_RESULTS', 'phaseEndsAt', ARGV[7])
  redis.call('ZINCRBY', KEYS[6], points, ARGV[1])
  result = 'WON|' .. attempt .. '|' .. points .. '|1|0'
else
  local roundEnded = 0
  if attempt >= max then
    -- This player is out. If every current player is out, nobody can win: end the round.
    local everyoneOut = true
    for _, playerId in ipairs(redis.call('HKEYS', KEYS[3])) do
      if tonumber(redis.call('HGET', KEYS[5], playerId) or '0') < max then
        everyoneOut = false
        break
      end
    end
    if everyoneOut then
      redis.call('HSET', KEYS[2], 'endReason', 'ALL_FAILED')
      redis.call('HSET', KEYS[1], 'status', 'ROUND_RESULTS', 'phaseEndsAt', ARGV[7])
      roundEnded = 1
    end
  end
  result = 'WRONG|' .. attempt .. '|0|' .. roundEnded .. '|0'
end
result = result .. '|' .. ARGV[3] .. '|' .. ARGV[4]
redis.call('SET', KEYS[7], result, 'EX', ARGV[8])
return '0|' .. result
