local reservationKey = KEYS[1]
local timeoutKey = KEYS[2]
local requestId = ARGV[1]

local status = redis.call('HGET', reservationKey, 'status')
if not status then
    redis.call('ZREM', timeoutKey, requestId)
    return 0
end
if status == 'RESERVED' then
    redis.call('HSET', reservationKey, 'status', 'CONFIRMED')
    redis.call('ZREM', timeoutKey, requestId)
    return 1
end
redis.call('ZREM', timeoutKey, requestId)
return 2
