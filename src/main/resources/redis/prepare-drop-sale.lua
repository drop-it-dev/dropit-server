local saleKey = KEYS[1]
local stockKey = KEYS[2]
local controlKey = KEYS[3]

local operationVersion = tonumber(ARGV[1])
local saleVersion = tonumber(ARGV[2])
local currentOperationVersion = tonumber(redis.call('HGET', controlKey, 'operationVersion') or '-1')
local currentSaleVersion = tonumber(redis.call('HGET', controlKey, 'saleVersion') or '-1')

if currentOperationVersion > operationVersion then
    return 0
end

redis.call('HSET', controlKey, 'operationVersion', ARGV[1], 'saleVersion', ARGV[2])
redis.call('PEXPIREAT', controlKey, ARGV[11])

if ARGV[3] ~= 'true' then
    redis.call('DEL', saleKey)
    return 2
end

if currentSaleVersion < saleVersion then
    redis.call('SET', stockKey, ARGV[10])
end

redis.call('HSET', saleKey,
        'operationVersion', ARGV[1],
        'saleVersion', ARGV[2],
        'openAt', ARGV[4],
        'closeAt', ARGV[5],
        'unitPrice', ARGV[6],
        'discountRate', ARGV[7],
        'purchaseLimit', ARGV[8],
        'productName', ARGV[9])
redis.call('PEXPIREAT', saleKey, ARGV[5])
redis.call('PEXPIREAT', stockKey, ARGV[11])
return 1
