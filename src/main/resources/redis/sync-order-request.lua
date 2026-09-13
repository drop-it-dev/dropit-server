-- Redis는 이 스크립트 전체를 하나의 원자적 연산으로 실행한다.
-- DB 주문 처리가 끝난 뒤, Redis에 남아 있는 예약을 최종 상태로 바꾸는 후처리 스크립트
-- SUCCEEDED: (확정) 이미 차감된 예약 수량은 그대로 두고 상태만 확정한다.
-- FAILED: (복원) 예약 당시 차감했던 재고와 구매량을 한 번 되돌린 뒤 상태를 확정한다.
-- KEYS[1] = {drop:<dropId>}:idempotency:user:<userId>:<idempotencyKeyHash>
-- KEYS[2] = {drop:<dropId>}:stock
-- KEYS[3] = {drop:<dropId>}:purchase:user:<userId>
-- ARGV[1] = 최종 상태(SUCCEEDED/FAILED), ARGV[2] = quantity,
-- ARGV[3] = orderId, ARGV[4] = failureCode, ARGV[5] = DB 동기화 버전
local reservationKey = KEYS[1]
local stockKey = KEYS[2]
local purchaseKey = KEYS[3]

local targetStatus = ARGV[1]
local quantity = tonumber(ARGV[2])
local orderId = ARGV[3]
local failureCode = ARGV[4]
local desiredVersion = ARGV[5]

if redis.call('TYPE', reservationKey).ok ~= 'hash' then
    -- 어떤 예약을 확정해야 하는지 모르므로 재고·구매량을 임의로 바꾸지 않는다.
    return 'MISSING'
end

local currentStatus = redis.call('HGET', reservationKey, 'status')
if currentStatus == targetStatus then
    -- 같은 SQS 메시지가 다시 와도 이미 끝난 작업이므로 아무것도 하지 않는다.
    return 'ALREADY_APPLIED'
end
if currentStatus ~= 'RESERVED' then
    -- 이미 다른 최종 상태가 기록됐다면 그 결정을 바꾸지 않는다.
    return 'INVALID'
end
if tonumber(redis.call('HGET', reservationKey, 'quantity')) ~= quantity then
    -- DB 요청과 Redis 예약의 수량이 다르면 잘못된 수량을 복원할 수 있으므로 중단한다.
    return 'INVALID'
end

if targetStatus == 'FAILED' then
    -- DB 주문 생성이 실패했으므로 Redis에 미리 잡아 둔 자리를 다시 돌려준다.
    if redis.call('TYPE', stockKey).ok ~= 'string'
            or redis.call('TYPE', purchaseKey).ok ~= 'string' then
        return 'INVALID'
    end
    local purchased = tonumber(redis.call('GET', purchaseKey))
    if not purchased or purchased < quantity then
        return 'INVALID'
    end
    redis.call('INCRBY', stockKey, quantity)
    redis.call('DECRBY', purchaseKey, quantity)
elseif targetStatus ~= 'SUCCEEDED' then
    -- 예약은 성공 확정 또는 실패 반환만 가능하다.
    return 'INVALID'
end

-- 상태를 RESERVED에서 최종 상태로 바꾸면, 다음 재시도는 위의 ALREADY_APPLIED로 끝난다.
redis.call('HSET', reservationKey,
        'status', targetStatus,
        'orderId', orderId,
        'failureCode', failureCode,
        'appliedVersion', desiredVersion)

return 'APPLIED'
