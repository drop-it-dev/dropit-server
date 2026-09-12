-- Redis는 이 스크립트 전체를 하나의 원자적 연산으로 실행한다.
-- 따라서 idempotency, 재고, 사용자 예약량의 결과를 이 실행 안에서 함께 결정한다.
-- KEYS[1] = {drop:<dropId>}:stock
-- KEYS[2] = {drop:<dropId>}:purchase:user:<userId>
-- KEYS[3] = {drop:<dropId>}:idempotency:user:<userId>:<idempotencyKeyHash>
-- ARGV[1] = requestId, ARGV[2] = fingerprint, ARGV[3] = quantity,
-- ARGV[4] = purchaseLimit
local request_id = ARGV[1]
local fingerprint = ARGV[2]
local quantity = tonumber(ARGV[3])
local purchase_limit = tonumber(ARGV[4])

local existing_request_id = redis.call('HGET', KEYS[3], 'requestId')
if existing_request_id then
    -- replay/conflict는 기존 idempotency 기록만 읽으며 재고와 예약량을 변경하지 않는다.
    local existing_fingerprint = redis.call('HGET', KEYS[3], 'fingerprint')
    if existing_fingerprint == fingerprint then
        return 'REPLAY|' .. existing_request_id
    end
    return 'CONFLICT'
end

local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock < quantity then
    -- 실패 결과는 여기서 즉시 반환하므로 신규 idempotency 기록을 남기지 않는다.
    return 'OUT_OF_STOCK'
end

local purchased_quantity = tonumber(redis.call('GET', KEYS[2]) or '0')
if purchase_limit ~= 0 and purchased_quantity + quantity > purchase_limit then
    -- 실패 결과는 여기서 즉시 반환하므로 신규 idempotency 기록을 남기지 않는다.
    return 'PURCHASE_LIMIT_EXCEEDED'
end

-- 신규 성공 요청의 차감, 예약량 증가, idempotency 기록, 24시간 TTL 설정도
-- 위 검증과 같은 원자적 스크립트 실행 안에서 함께 완료한다.
redis.call('DECRBY', KEYS[1], quantity)
redis.call('INCRBY', KEYS[2], quantity)
redis.call(
        'HSET',
        KEYS[3],
        'requestId', request_id,
        'fingerprint', fingerprint,
        'status', 'RESERVED'
)
redis.call('EXPIRE', KEYS[3], 86400)

return 'NEW|' .. request_id
