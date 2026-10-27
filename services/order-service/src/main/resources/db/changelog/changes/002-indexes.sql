--liquibase formatted sql

--changeset farhod:002-idempotency-unique
-- Один и тот же клиент с тем же Idempotency-Key не создаст два заказа. Это И ограничение, И индекс поиска.
CREATE UNIQUE INDEX uq_orders_customer_idem ON app.orders (customer_id, idempotency_key);

--changeset farhod:002-orders-by-customer
-- Основной запрос: «мои заказы, новые сверху». Составной индекс: фильтр по customer_id + готовая сортировка по created_at.
CREATE INDEX idx_orders_customer_created ON app.orders (customer_id, created_at DESC);

--changeset farhod:002-items-by-order
-- FK в Postgres НЕ создаёт индекс автоматически. Без него JOIN/каскадное удаление = seq scan.
CREATE INDEX idx_items_order ON app.order_items (order_id);
