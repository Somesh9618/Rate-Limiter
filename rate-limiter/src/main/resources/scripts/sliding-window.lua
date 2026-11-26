
-- Sliding Window Log Rate Limiter
--
-- KEYS[1] = Redis key for the client
--
-- ARGV[1] = Capacity
-- ARGV[2] = Window duration in milliseconds
-- ARGV[3] = Unique request ID
-- ARGV[4] = Optional test timestamp in ms
--
-- Returns: allowed:remaining:retryAfterSeconds

local key = KEYS[1]

local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local requestId = ARGV[3]

if not limit or limit < 1 or limit % 1 ~= 0 then
    return redis.error_reply("Invalid capacity")
end

if not windowMs or windowMs < 1
        or windowMs % 1 ~= 0 then
    return redis.error_reply("Invalid window duration")
end

if not requestId or requestId == "" then
    return redis.error_reply("Request ID required")
end

local nowMs

-- Controlled time used only by internal tests.
if ARGV[4] and ARGV[4] ~= "" then

    nowMs = tonumber(ARGV[4])

    if not nowMs or nowMs < 0
            or nowMs % 1 ~= 0 then
        return redis.error_reply("Invalid test time")
    end

else

    -- Production: use the Redis server's clock.
    local redisTime = redis.call('TIME')

    nowMs =
        tonumber(redisTime[1]) * 1000
        + math.floor(tonumber(redisTime[2]) / 1000)
end

local cutoff = nowMs - windowMs

-- Remove events at or before the cutoff.
-- An event exactly windowMs old is expired.
redis.call(
    'ZREMRANGEBYSCORE',
    key,
    '-inf',
    cutoff
)

local count = redis.call('ZCARD', key)

if count >= limit then

    local oldest = redis.call(
        'ZRANGE',
        key,
        0,
        0,
        'WITHSCORES'
    )

    local oldestMs = tonumber(oldest[2])

    local waitMs = math.max(
        1,
        oldestMs + windowMs - nowMs
    )

    local retrySeconds = math.ceil(
        waitMs / 1000
    )

    return '0:0:' .. tostring(retrySeconds)
end

-- Add the current accepted request.
redis.call('ZADD', key, nowMs, requestId)

-- Clean up inactive client logs.
redis.call('PEXPIRE', key, windowMs + 1000)

local remaining = limit - count - 1

return '1:' .. tostring(remaining) .. ':0'
