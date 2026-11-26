
-- Token Bucket Rate Limiter
--
-- KEYS[1] = Client bucket key
--
-- ARGV[1] = Maximum bucket capacity
-- ARGV[2] = Refill tokens per second
-- ARGV[3] = TTL in milliseconds
-- ARGV[4] = Optional controlled time (tests only)
--
-- Return format:
-- allowed:remainingWholeTokens:retryAfterSeconds

local key = KEYS[1]

local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local ttlMs = tonumber(ARGV[3])

-- Validate configuration.
if not capacity
        or capacity < 1
        or capacity % 1 ~= 0 then
    return redis.error_reply("Invalid bucket capacity")
end

if not refillRate or refillRate <= 0 then
    return redis.error_reply("Invalid refill rate")
end

if not ttlMs or ttlMs < 1
        or ttlMs % 1 ~= 0 then
    return redis.error_reply("Invalid bucket TTL")
end

-- Use controlled time only for direct internal tests.
-- Normal application requests use Redis server time.
local nowMs

if ARGV[4] and ARGV[4] ~= "" then
    nowMs = tonumber(ARGV[4])

    if not nowMs or nowMs < 0
            or nowMs % 1 ~= 0 then
        return redis.error_reply("Invalid test timestamp")
    end
else
    local redisTime = redis.call('TIME')

    nowMs =
        tonumber(redisTime[1]) * 1000
        + math.floor(tonumber(redisTime[2]) / 1000)
end

-- Read existing state.
local previousTokens =
    redis.call('HGET', key, 'tokens')

local previousTime =
    redis.call('HGET', key, 'lastRefillMs')

local tokens
local lastRefillMs

if not previousTokens and not previousTime then

    -- First request: start with a full bucket.
    tokens = capacity
    lastRefillMs = nowMs

else
    -- Both fields must exist for a valid bucket.
    if not previousTokens or not previousTime then
        return redis.error_reply("Incomplete bucket state")
    end

    tokens = tonumber(previousTokens)
    lastRefillMs = tonumber(previousTime)

    if not tokens or not lastRefillMs then
        return redis.error_reply("Invalid bucket state")
    end

    -- Never generate negative elapsed time.
    local elapsedMs = math.max(
        0,
        nowMs - lastRefillMs
    )

    -- Convert elapsed milliseconds to seconds.
    local elapsedSeconds = elapsedMs / 1000

    -- Refill, without exceeding bucket capacity.
    tokens = math.min(
        capacity,
        tokens + elapsedSeconds * refillRate
    )

    -- Prevent moving the stored timestamp backwards.
    lastRefillMs = math.max(lastRefillMs, nowMs)
end

local allowed = 0
local retryAfterSeconds = 0

-- A request costs one complete token.
if tokens >= 1 then

    allowed = 1
    tokens = tokens - 1

else

    -- Calculate how long until one token is available.
    local missingTokens = 1 - tokens

    retryAfterSeconds = math.max(
        1,
        math.ceil(missingTokens / refillRate)
    )
end

-- Store tokens with fractional precision.
redis.call(
    'HSET',
    key,
    'tokens',
    string.format('%.12f', tokens),
    'lastRefillMs',
    string.format('%.0f', lastRefillMs)
)

-- Remove inactive buckets automatically.
redis.call('PEXPIRE', key, ttlMs)

local remaining = math.floor(tokens)

return tostring(allowed)
    .. ':'
    .. tostring(remaining)
    .. ':'
    .. tostring(retryAfterSeconds)
