
-- Sliding Window Log Rate Limiter
--
-- KEYS[1] = Redis sorted-set key for this client
--
-- ARGV[1] = Maximum requests in the window
-- ARGV[2] = Window duration in milliseconds
-- ARGV[3] = Unique request identifier
--
-- Return format:
-- allowed:remaining:retryAfterSeconds

local key = KEYS[1]

local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local requestId = ARGV[3]

-- Validate input.
if not limit or limit < 1 then
    return redis.error_reply("Invalid request limit")
end

if not windowMs or windowMs < 1 then
    return redis.error_reply("Invalid window duration")
end

if not requestId or requestId == "" then
    return redis.error_reply("Request ID required")
end

-- Redis server time provides a shared clock.
local time = redis.call('TIME')

local nowMs =
    tonumber(time[1]) * 1000
    + math.floor(tonumber(time[2]) / 1000)

-- A request remains relevant until its window expires.
local cutoff = nowMs - windowMs

-- Remove accepted requests outside the rolling window.
redis.call(
    'ZREMRANGEBYSCORE',
    key,
    '-inf',
    cutoff
)

-- Count accepted requests still inside the window.
local currentCount = redis.call('ZCARD', key)

-- Quota exhausted.
if currentCount >= limit then

    -- Find the oldest accepted event.
    local oldest = redis.call(
        'ZRANGE',
        key,
        0,
        0,
        'WITHSCORES'
    )

    local oldestMs = tonumber(oldest[2])

    -- Time until the oldest event leaves the window.
    local waitMs =
        math.max(1, oldestMs + windowMs - nowMs)

    -- Round up so the client doesn't retry too early.
    local retrySeconds =
        math.ceil(waitMs / 1000)

    return '0:0:' .. tostring(retrySeconds)
end

-- Store this accepted request.
-- The member is unique even when scores match.
redis.call(
    'ZADD',
    key,
    nowMs,
    requestId
)

-- Expire inactive client logs automatically.
redis.call('PEXPIRE', key, windowMs + 1000)

local remaining = limit - currentCount - 1

return '1:' .. tostring(remaining) .. ':0'
