-- ID 선조회: 조인 없이 정렬된 인덱스에서 페이지를 찾는다.
EXPLAIN ANALYZE
SELECT d.id FROM drops d
WHERE d.visible = b'1' AND d.open_at <= NOW()
  AND d.close_at > NOW() AND d.remaining_quantity > 0
ORDER BY d.close_at ASC, d.id DESC LIMIT 20;

-- count: 키워드가 없는 경우 필수 FK 조인을 생략한다.
EXPLAIN ANALYZE
SELECT COUNT(d.id) FROM drops d
WHERE d.visible = b'1' AND d.open_at <= NOW()
  AND d.close_at > NOW() AND d.remaining_quantity > 0;

-- 상세 fetch join은 위에서 선택된 ID 20개에만 수행된다.
