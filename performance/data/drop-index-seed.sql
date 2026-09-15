SET SESSION cte_max_recursion_depth = 1000001;

SET @performance_marker = 'DROP_INDEX_PERFORMANCE_PRODUCT';
SET @product_count = 100000;
SET @drop_count = 1000000;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'drops'
      AND index_name = 'idx_drops_public_closing'
);

SET @drop_index_sql = IF(
    @index_exists > 0,
    'DROP INDEX idx_drops_public_closing ON drops',
    'SELECT 1'
);

PREPARE drop_index_statement FROM @drop_index_sql;
EXECUTE drop_index_statement;
DEALLOCATE PREPARE drop_index_statement;

INSERT INTO users (
    email,
    password,
    username,
    role,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    'drop-index-seller@dropit.test',
    'local-performance-password',
    'drop-index-seller',
    'SELLER',
    NULL,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'drop-index-seller@dropit.test'
);

SET @seller_id = (
    SELECT id
    FROM users
    WHERE email = 'drop-index-seller@dropit.test'
    LIMIT 1
);

DELETE FROM drops
WHERE product_id IN (
    SELECT id
    FROM products
    WHERE description = @performance_marker
);

DELETE FROM products
WHERE description = @performance_marker;

INSERT INTO products (
    seller_id,
    name,
    description,
    image_url,
    created_at,
    updated_at
)
WITH RECURSIVE product_numbers AS (
    SELECT 1 AS number

    UNION ALL

    SELECT number + 1
    FROM product_numbers
    WHERE number < @product_count
)
SELECT
    @seller_id,
    CONCAT('Drop Index Performance Product ', number),
    @performance_marker,
    NULL,
    DATE_SUB(NOW(), INTERVAL MOD(number, 365) DAY),
    NOW()
FROM product_numbers;

INSERT INTO drops (
    product_id,
    price,
    initial_quantity,
    remaining_quantity,
    discount_rate,
    purchase_limit,
    visible,
    open_at,
    close_at,
    created_at,
    updated_at
)
WITH RECURSIVE numbers AS (
    SELECT 1 AS number

    UNION ALL

    SELECT number + 1
    FROM numbers
    WHERE number < @drop_count
),
performance_products AS (
    SELECT
        id,
        ROW_NUMBER() OVER (ORDER BY id) AS product_number
    FROM products
    WHERE description = @performance_marker
)
SELECT
    performance_products.id,
    30000 + MOD(numbers.number, 70000),
    100,
    CASE MOD(numbers.number, 10)
        WHEN 7 THEN 0
        ELSE 100
    END,
    MOD(numbers.number, 51),
    2,
    CASE MOD(numbers.number, 10)
        WHEN 9 THEN b'0'
        ELSE b'1'
    END,
    CASE
        WHEN MOD(numbers.number, 10) BETWEEN 0 AND 4
            THEN DATE_SUB(NOW(), INTERVAL 7 DAY)
        WHEN MOD(numbers.number, 10) BETWEEN 5 AND 6
            THEN DATE_ADD(NOW(), INTERVAL 7 DAY)
        WHEN MOD(numbers.number, 10) = 7
            THEN DATE_SUB(NOW(), INTERVAL 7 DAY)
        WHEN MOD(numbers.number, 10) = 8
            THEN DATE_SUB(NOW(), INTERVAL 14 DAY)
        ELSE DATE_SUB(NOW(), INTERVAL 7 DAY)
    END,
    CASE
        WHEN MOD(numbers.number, 10) BETWEEN 0 AND 4
            THEN DATE_ADD(NOW(), INTERVAL (7 + MOD(numbers.number, 30)) DAY)
        WHEN MOD(numbers.number, 10) BETWEEN 5 AND 6
            THEN DATE_ADD(NOW(), INTERVAL (37 + MOD(numbers.number, 30)) DAY)
        WHEN MOD(numbers.number, 10) = 7
            THEN DATE_ADD(NOW(), INTERVAL (7 + MOD(numbers.number, 30)) DAY)
        WHEN MOD(numbers.number, 10) = 8
            THEN DATE_SUB(NOW(), INTERVAL 7 DAY)
        ELSE DATE_ADD(NOW(), INTERVAL (7 + MOD(numbers.number, 30)) DAY)
    END,
    DATE_SUB(NOW(), INTERVAL MOD(numbers.number, 365) DAY),
    NOW()
FROM numbers
INNER JOIN performance_products
    ON performance_products.product_number = MOD(numbers.number - 1, @product_count) + 1;

SELECT
    COUNT(DISTINCT products.id) AS performance_product_count,
    COUNT(drops.id) AS total_drop_count,
    SUM(
        drops.visible = b'1'
        AND drops.open_at <= NOW()
        AND drops.close_at > NOW()
        AND drops.remaining_quantity > 0
    ) AS open_public_drop_count
FROM products
INNER JOIN drops ON drops.product_id = products.id
WHERE products.description = @performance_marker;
