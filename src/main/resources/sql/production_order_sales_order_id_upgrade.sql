ALTER TABLE production_order
    ADD COLUMN sales_order_id VARCHAR(50) NULL AFTER order_id;
