\timing on

-- 1 млн заказов 10 000 клиентов (реалистичные данные для планов запросов)
INSERT INTO app.orders (id, customer_id, customer_email, status, total_amount, currency, idempotency_key, created_at)
SELECT gen_random_uuid(),
       ('00000000-0000-0000-0000-' || lpad((g % 10000)::text, 12, '0'))::uuid,
       'user' || (g % 10000) || '@example.com', 'CREATED',
       (random() * 1000)::numeric(12,2), 'USD', 'k' || g,
       now() - (g || ' seconds')::interval
FROM generate_series(1, 1000000) g;

ANALYZE app.orders;   -- обновить статистику планировщика (без неё план запросов может быть неверным)

SELECT count(*) AS orders_total, count(DISTINCT customer_id) AS customers FROM app.orders;