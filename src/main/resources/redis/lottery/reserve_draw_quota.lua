local quotaKey = KEYS[1]
local reservationKey = KEYS[2]
local timeoutKey = KEYS[3]

local requestId = ARGV[1]
local activityId = ARGV[2]
local userId = ARGV[3]
local drawCount = tonumber(ARGV[4])
local dailyLimit = tonumber(ARGV[5])
local drawDate = ARGV[6]
local createdAt = ARGV[7]
local timeoutAt = ARGV[8]
local expiresAt = ARGV[9]

-- dailyLimit is a current server policy snapshot, not part of the idempotency identity.
if redis.call('EXISTS', reservationKey) == 1 then
    local existing = redis.call('HMGET', reservationKey,
            'activityId', 'userId', 'drawCount', 'drawDate', 'status')
    if existing[1] ~= activityId or existing[2] ~= userId
            or existing[3] ~= tostring(drawCount) then
        return {3, existing[5] or 'RESERVED', existing[4] or drawDate, existing[3] or tostring(drawCount)}
    end
    return {0, existing[5] or 'RESERVED', existing[4], existing[3]}
end

local used = tonumber(redis.call('GET', quotaKey) or '0')
if used + drawCount > dailyLimit then
    return {2, 'RESERVED', drawDate, tostring(drawCount)}
end

redis.call('INCRBY', quotaKey, drawCount)
redis.call('PEXPIREAT', quotaKey, expiresAt)
redis.call('HSET', reservationKey,
        'requestId', requestId,
        'activityId', activityId,
        'userId', userId,
        'drawCount', tostring(drawCount),
        'drawDate', drawDate,
        'status', 'RESERVED',
        'createdAt', createdAt)
redis.call('PEXPIREAT', reservationKey, expiresAt)
redis.call('ZADD', timeoutKey, timeoutAt, requestId)
return {1, 'RESERVED', drawDate, tostring(drawCount)}
