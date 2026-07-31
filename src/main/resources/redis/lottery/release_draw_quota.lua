local reservationKey = KEYS[1]
local timeoutKey = KEYS[2]
local quotaKey = KEYS[3]
local requestId = ARGV[1]
local expectedActivityId = ARGV[2]
local expectedUserId = ARGV[3]
local expectedDrawDate = ARGV[4]

if redis.call('EXISTS', reservationKey) == 0 then
    redis.call('ZREM', timeoutKey, requestId)
    return 0
end

local reservation = redis.call('HMGET', reservationKey,
        'requestId', 'activityId', 'userId', 'drawCount', 'drawDate', 'status', 'createdAt')
for index = 1, 7 do
    if not reservation[index] then
        return -1
    end
end
local drawCount = tonumber(reservation[4])
local createdAt = tonumber(reservation[7])
if reservation[1] ~= requestId or not drawCount or (drawCount ~= 1 and drawCount ~= 10)
        or not createdAt then
    return -1
end

local status = reservation[6]
if status == 'CONFIRMED' or status == 'RELEASED' then
    redis.call('ZREM', timeoutKey, requestId)
    return 0
end
if status ~= 'RESERVED' then
    return -1
end

if quotaKey == '' or reservation[2] ~= expectedActivityId
        or reservation[3] ~= expectedUserId or reservation[5] ~= expectedDrawDate then
    return -1
end

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
