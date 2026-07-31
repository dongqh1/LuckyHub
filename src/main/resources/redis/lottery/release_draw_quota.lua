local reservationKey = KEYS[1]
local timeoutKey = KEYS[2]
local quotaKey = KEYS[3]
local requestId = ARGV[1]
local expectedActivityId = ARGV[2]
local expectedUserId = ARGV[3]
local expectedDrawDate = ARGV[4]

local status = redis.call('HGET', reservationKey, 'status')
if not status then
    redis.call('ZREM', timeoutKey, requestId)
    return 0
end
if status ~= 'RESERVED' then
    redis.call('ZREM', timeoutKey, requestId)
    return 2
end

local identity = redis.call('HMGET', reservationKey, 'activityId', 'userId', 'drawDate')
if quotaKey == '' or identity[1] ~= expectedActivityId
        or identity[2] ~= expectedUserId or identity[3] ~= expectedDrawDate then
    return -1
end

local drawCount = tonumber(redis.call('HGET', reservationKey, 'drawCount') or '0')
if redis.call('EXISTS', quotaKey) == 1 then
    local used = tonumber(redis.call('GET', quotaKey) or '0')
    local remaining = used - drawCount
    if remaining < 0 then
        remaining = 0
    end
    redis.call('SET', quotaKey, remaining, 'KEEPTTL')
end
redis.call('HSET', reservationKey, 'status', 'RELEASED')
redis.call('ZREM', timeoutKey, requestId)
return 1
