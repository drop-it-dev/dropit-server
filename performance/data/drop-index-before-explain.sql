EXPLAIN ANALYZE
SELECT
    d.id,
    d.product_id,
    d.price,
    d.initial_quantity,
    d.remaining_quantity,
    d.discount_rate,
    d.purchase_limit,
    d.visible,
    d.open_at,
    d.close_at,
    d.created_at,
    d.updated_at
FROM drops d
INNER JOIN products p ON p.id = d.product_id
INNER JOIN users u ON u.id = p.seller_id
WHERE d.visible = b'1'
  AND d.open_at <= NOW()
  AND d.close_at > NOW()
  AND d.remaining_quantity > 0
ORDER BY d.close_at ASC, d.id DESC
LIMIT 20;

EXPLAIN ANALYZE
SELECT COUNT(d.id)
FROM drops d
INNER JOIN products p ON p.id = d.product_id
INNER JOIN users u ON u.id = p.seller_id
WHERE d.visible = b'1'
  AND d.open_at <= NOW()
  AND d.close_at > NOW()
  AND d.remaining_quantity > 0;
