-- Redis는 이 스크립트 전체를 하나의 원자적 연산으로 실행한다.
-- 따라서 idempotency, 재고, 사용자 예약량의 결과를 이 실행 안에서 함께 결정한다.
-- KEYS[1] = {drop:<dropId>}:sale
-- KEYS[2] = {drop:<dropId>}:stock
-- KEYS[3] = {drop:<dropId>}:purchases
-- KEYS[4] = {drop:<dropId>}:idempotency:user:<userId>:<idempotencyKeyHash>
-- ARGV[1..7] = requestId, fingerprint, quantity, acceptedAt micros, userId, dropId, key hash
local request_id = ARGV[1]
local fingerprint = ARGV[2]
local quantity = tonumber(ARGV[3])
local accepted_at = ARGV[4]
local user_id = ARGV[5]
local drop_id = ARGV[6]
local key_hash = ARGV[7]

local existing_request_id = redis.call('HGET', KEYS[4], 'requestId')
if existing_request_id then
    -- replay/conflict는 기존 idempotency 기록만 읽으며 재고와 예약량을 변경하지 않는다.
    local existing_fingerprint = redis.call('HGET', KEYS[4], 'fingerprint')
    if existing_fingerprint == fingerprint then
        return 'REPLAY|' .. existing_request_id
    end
    return 'CONFLICT'
end

if redis.call('TYPE', KEYS[1]).ok ~= 'hash'
        or redis.call('TYPE', KEYS[2]).ok ~= 'string'
        or (redis.call('TYPE', KEYS[3]).ok ~= 'none' and redis.call('TYPE', KEYS[3]).ok ~= 'hash') then
    return 'NOT_READY'
end

local purchase_limit = tonumber(redis.call('HGET', KEYS[1], 'purchaseLimit'))
local open_at = tonumber(redis.call('HGET', KEYS[1], 'openAt'))
local close_at = tonumber(redis.call('HGET', KEYS[1], 'closeAt'))
local accepted_at_millis = math.floor(tonumber(accepted_at) / 1000)
local expires_at = close_at + 86400000
if not quantity or quantity < 1 or quantity % 1 ~= 0
        or not purchase_limit or not open_at or not close_at or not accepted_at_millis then
    return 'NOT_READY'
end
if accepted_at_millis < open_at or accepted_at_millis >= close_at then
    return 'NOT_OPEN'
end

local stock = tonumber(redis.call('GET', KEYS[2]))
if not stock then
    return 'NOT_READY'
end
if stock < quantity then
    -- 실패 결과는 여기서 즉시 반환하므로 신규 idempotency 기록을 남기지 않는다.
    return 'OUT_OF_STOCK'
end

local purchased_quantity = tonumber(redis.call('HGET', KEYS[3], user_id) or '0')
if purchase_limit ~= 0 and purchased_quantity + quantity > purchase_limit then
    -- 실패 결과는 여기서 즉시 반환하므로 신규 idempotency 기록을 남기지 않는다.
    return 'PURCHASE_LIMIT_EXCEEDED'
end

-- 신규 성공 요청의 차감, 예약량 증가, idempotency 기록, 24시간 TTL 설정도
-- 위 검증과 같은 원자적 스크립트 실행 안에서 함께 완료한다.
redis.call('DECRBY', KEYS[2], quantity)
redis.call('HINCRBY', KEYS[3], user_id, quantity)
redis.call(
        'HSET',
        KEYS[4],
        'requestId', request_id,
        'userId', user_id,
        'dropId', drop_id,
        'idempotencyKeyHash', key_hash,
        'fingerprint', fingerprint,
        'quantity', quantity,
        'acceptedAtEpochMicros', accepted_at,
        'status', 'RESERVED',
        'publicationState', 'UNCONFIRMED',
        'productName', redis.call('HGET', KEYS[1], 'productName'),
        'unitPrice', redis.call('HGET', KEYS[1], 'unitPrice'),
        'discountRate', redis.call('HGET', KEYS[1], 'discountRate'),
        'expiresAtEpochMillis', expires_at
)
redis.call('PEXPIREAT', KEYS[2], expires_at)
redis.call('PEXPIREAT', KEYS[3], expires_at)
redis.call('PEXPIREAT', KEYS[4], expires_at)

return 'NEW|' .. request_id
