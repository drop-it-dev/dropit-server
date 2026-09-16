local currentAsOf = redis.call('HGET', KEYS[5], 'asOfEpochMillis')
local currentStartedAt = redis.call('HGET', KEYS[5], 'startedAtEpochMillis')
if currentAsOf then
    if tonumber(currentAsOf) > tonumber(ARGV[1])
        or (tonumber(currentAsOf) == tonumber(ARGV[1])
            and tonumber(currentStartedAt) >= tonumber(ARGV[2])) then
        redis.call('DEL', KEYS[1], KEYS[2])
        redis.call('HSET', KEYS[6],
            'completedAt', ARGV[4], 'status', 'SKIPPED',
            'reason', '더 최신 스냅샷이 이미 공개되었습니다.')
        redis.call('EXPIRE', KEYS[6], 604800)
        return 0
    end
end

local totalCount = tonumber(ARGV[7])
local monthlyCount = tonumber(ARGV[8])
if redis.call('ZCARD', KEYS[1]) ~= totalCount
    or redis.call('ZCARD', KEYS[2]) ~= monthlyCount then
    return redis.error_reply('Incomplete seller ranking snapshot')
end

if totalCount > 0 then
    redis.call('RENAME', KEYS[1], KEYS[3])
    redis.call('PERSIST', KEYS[3])
else
    redis.call('DEL', KEYS[3])
end
if monthlyCount > 0 then
    redis.call('RENAME', KEYS[2], KEYS[4])
    redis.call('PERSIST', KEYS[4])
else
    redis.call('DEL', KEYS[4])
end

redis.call('HSET', KEYS[5],
    'asOfEpochMillis', ARGV[1], 'startedAtEpochMillis', ARGV[2],
    'asOf', ARGV[3], 'publishedAt', ARGV[4], 'month', ARGV[5],
    'version', ARGV[6], 'status', 'SUCCESS', 'ranges', 'TOTAL,MONTHLY',
    'totalSellerCount', ARGV[7], 'monthlySellerCount', ARGV[8])
redis.call('HSET', KEYS[6],
    'completedAt', ARGV[4], 'status', 'SUCCESS',
    'totalSellerCount', ARGV[7], 'monthlySellerCount', ARGV[8])
redis.call('EXPIRE', KEYS[6], 604800)
return 1
