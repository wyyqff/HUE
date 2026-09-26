-- Run against the intended application schema before deploying the new backend.
-- Existing orders remain NULL: their historical delivery choice cannot be inferred safely.
SET @order_trade_type_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_info'
      AND COLUMN_NAME = 'trade_type'
);
SET @order_trade_type_sql = IF(
    @order_trade_type_exists = 0,
    'ALTER TABLE order_info ADD COLUMN trade_type TINYINT NULL DEFAULT NULL COMMENT ''下单交付方式快照：0面交，1邮寄；历史订单可为空'' AFTER delivery_fee',
    'SELECT 1'
);
PREPARE order_trade_type_statement FROM @order_trade_type_sql;
EXECUTE order_trade_type_statement;
DEALLOCATE PREPARE order_trade_type_statement;
