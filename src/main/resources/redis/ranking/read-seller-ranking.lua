local version = redis.call('HGET', KEYS[1], 'version')
local status = redis.call('HGET', KEYS[1], 'status')
if not version or status ~= 'SUCCESS' then
    return {'NOT_READY'}
end

local month = redis.call('HGET', KEYS[1], 'month')
local asOf = redis.call('HGET', KEYS[1], 'asOf')
local publishedAt = redis.call('HGET', KEYS[1], 'publishedAt')
if not month or not asOf or not publishedAt then
    return {'UNAVAILABLE'}
end
if ARGV[1] == 'MONTHLY' and month ~= ARGV[2] then
    return {'NOT_READY'}
end

local expectedField = ARGV[1] == 'TOTAL' and 'totalSellerCount' or 'monthlySellerCount'
local expectedCount = tonumber(redis.call('HGET', KEYS[1], expectedField))
if not expectedCount or redis.call('ZCARD', KEYS[2]) ~= expectedCount then
    return {'UNAVAILABLE'}
end

local rows = redis.call('ZREVRANGE', KEYS[2], 0, tonumber(ARGV[3]) - 1, 'WITHSCORES')
local response = {'OK', version, asOf, publishedAt, month, tostring(#rows / 2)}
for _, value in ipairs(rows) do
    table.insert(response, value)
end

if ARGV[4] ~= '' then
    local rank = redis.call('ZREVRANK', KEYS[2], ARGV[4])
    local score = redis.call('ZSCORE', KEYS[2], ARGV[4])
    table.insert(response, rank and tostring(rank) or '')
    table.insert(response, score or '')
else
    table.insert(response, '')
    table.insert(response, '')
end
return response
