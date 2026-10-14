-- Atomically remove a player, migrating host or closing the room as needed.
-- KEYS[1] room hash, KEYS[2] players hash, KEYS[3..] the room's other keys (deleted if it closes)
-- ARGV[1] userId
-- Returns NOT_FOUND | NOT_MEMBER | LEFT | ROOM_CLOSED | HOST_CHANGED:<newHostId>
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 'NOT_FOUND'
end
if redis.call('HDEL', KEYS[2], ARGV[1]) == 0 then
  return 'NOT_MEMBER'
end

-- The earliest-joined remaining human becomes host. Bots never host.
local entries = redis.call('HGETALL', KEYS[2])
local nextHost, earliest = nil, nil
for i = 1, #entries, 2 do
  local player = cjson.decode(entries[i + 1])
  if not player.bot and (earliest == nil or player.joinedAt < earliest) then
    nextHost, earliest = entries[i], player.joinedAt
  end
end

if nextHost == nil then
  -- No humans left: a room of only bots is pointless, so close it.
  redis.call('DEL', unpack(KEYS))
  return 'ROOM_CLOSED'
end

if redis.call('HGET', KEYS[1], 'hostId') == ARGV[1] then
  redis.call('HSET', KEYS[1], 'hostId', nextHost)
  return 'HOST_CHANGED:' .. nextHost
end
return 'LEFT'
