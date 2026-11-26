
-- KEYS[1] = Client + window Redis key
-- ARGV[1] = Maximum requests
-- ARGV[2] = Key expiration in seconds
-- ARGV[3] = Seconds until window reset

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local expiration = tonumber(ARGV[2])
local resetSeconds = tonumber(ARGV[3])

-- Validate inputs.
if not limit or limit < 1 then
    return redis.error_reply("Invalid request limit")
end

if not expiration or expiration < 1 then
    return redis.error_reply("Invalid expiration")
end

if not resetSeconds or resetSeconds < 1 then
    return redis.error_reply("Invalid reset time")
end

-- Missing counter means zero requests so far.
local current = tonumber(
    redis.call('GET', key) or '0'
)

-- Reject if quota is exhausted.
if current >= limit then
    return "0:0:" .. tostring(resetSeconds)
end

-- INCR is atomic.
local newCount = redis.call('INCR', key)

-- Set TTL for a new key.
if newCount == 1 then
    redis.call('EXPIRE', key, expiration)
end

local remaining = limit - newCount

return "1:" .. tostring(remaining) .. ":0"
