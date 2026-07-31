local reservationKey = KEYS[1]
local timeoutKey = KEYS[2]
local requestId = ARGV[1]

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
if status == 'RESERVED' then
    redis.call('HSET', reservationKey, 'status', 'CONFIRMED')
    redis.call('ZREM', timeoutKey, requestId)
    return 1
end
if status == 'CONFIRMED' or status == 'RELEASED' then
    redis.call('ZREM', timeoutKey, requestId)
    return 0
end
return -1
