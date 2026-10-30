# Сириус

Учебный backend для приёма заказов (микросервисы). Проект сделан, чтобы отработать на практике проектирование и эксплуатацию серверной части: идемпотентный API, обмен событиями через брокер, авторизацию по OAuth2, упаковку в Docker, шлюз Traefik и запуск в Docker Swarm.

> Статус: учебный проект, развёрнут на собственном сервере (Ubuntu). Не предназначен для боевого использования как есть.

## Архитектура

```
Клиент (Android / iOS / браузер)
        │ HTTPS
        ▼
     Traefik  ── TLS, маршрутизация по домену и пути, ограничение частоты запросов
   ┌────┼──────────────┬───────────────┐
   ▼    ▼              ▼               ▼
auth-service   order-service    ops-service     (панели под паролем)
OAuth2/OIDC,   заказы, кэш,     управление
JWT, e-mail    outbox           контейнерами
                  │
                  │ события (RabbitMQ)
                  ▼
          notification-service  ── письма (Mailpit в разработке)

Данные: PostgreSQL 17 (отдельная база на сервис), Redis, RabbitMQ
```

| Сервис | Порт | Назначение |
|---|---|---|
| `auth-service` | 9000 | сервер авторизации: регистрация с подтверждением e-mail, вход, выдача JWT (Authorization Code + PKCE) |
| `order-service` | 8081 | приём и чтение заказов; идемпотентное создание; шаблон Transactional Outbox |
| `notification-service` | 8082 | получает события о заказах из RabbitMQ и отправляет уведомления |
| `ops-service` | 8083 | список, логи и перезапуск контейнеров (через ограниченный прокси Docker) |

Служебные адреса Actuator вынесены на отдельный порт `9091` и наружу не публикуются.

## Что реализовано

- **Идемпотентность.** `POST /api/v1/orders` с заголовком `Idempotency-Key`: повтор запроса возвращает тот же заказ, гонки двух одинаковых запросов решаются на уровне БД.
- **Transactional Outbox.** Событие пишется в одной транзакции с заказом, публикуется в RabbitMQ планировщиком с подтверждениями брокера и возвратом недоставленных; несколько реплик не мешают друг другу (`FOR UPDATE SKIP LOCKED`).
- **Идемпотентный потребитель.** Таблица обработанных событий, повторные попытки и очередь недоставленных сообщений (DLQ).
- **Безопасность.** Собственный сервер авторизации (Spring Authorization Server), JWT, проверка токенов в сервисах по JWKS, доступ по scope и ролям. Роли БД разделены на мигратора и приложение.
- **Данные.** PostgreSQL, миграции Liquibase, кэш в Redis.
- **Docker.** Многоступенчатые образы на слоях Spring Boot, запуск не от root, `HEALTHCHECK`, Docker Compose.
- **Шлюз.** Traefik с HTTPS (собственный центр сертификации), ограничением частоты запросов, защитой панелей паролем.
- **Docker Swarm.** Реплики, секреты, плавные обновления; данные остаются в Compose.
- **Тесты.** JUnit 5, Spring Boot Test, проверки безопасности и обмена сообщениями.

## Технологии

Java 17 · Spring Boot 4.1 · Spring Security 7 · Hibernate 7 · Spring AMQP · Liquibase · PostgreSQL 17 · Redis 7 · RabbitMQ 4 · Docker / Compose / Swarm · Traefik 3 · docker-java · Maven · Ubuntu Server

## Структура репозитория

```
.
├── libs/
│   └── sirius-events/      # общий контракт событий (DTO), без Spring
├── services/
│   ├── auth-service/
│   ├── order-service/
│   ├── notification-service/
│   └── ops-service/
├── deploy/
│   ├── compose/            # docker-compose.*.yml, .env.example
│   ├── docker/             # Dockerfile (общий для сервисов)
│   ├── postgres/           # конфигурация и init-скрипты
│   ├── traefik/            # статическая и динамическая конфигурация, сертификаты (не в Git)
│   ├── swarm/              # stack.yml
│   └── observability/      # (в работе)
├── scripts/                # вспомогательные скрипты (например, получение токена)
└── pom.xml                 # родительский Maven-проект
```

## Требования

- JDK 17, Maven (или `./mvnw`)
- Docker и Docker Compose
- Для HTTPS-стенда: домен `sirius.test` в `/etc/hosts` клиента и сертификат из `deploy/traefik/certs`

## Быстрый старт (локально)

```bash
# 1. секреты: скопируйте пример и задайте свои значения (файл .env в Git не попадает)
cp deploy/compose/.env.example deploy/compose/.env

# 2. данные: PostgreSQL, Redis, RabbitMQ, Mailpit
cd deploy/compose
export COMPOSE_FILE=docker-compose.data.yml
docker compose up -d

# 3. сервисы из IDE или Maven
cd ../..
./mvnw -pl services/order-service -am spring-boot:run
```

Все сервисы в контейнерах и шлюз Traefik:

```bash
cd deploy/compose
export COMPOSE_FILE=docker-compose.data.yml:docker-compose.apps.yml:docker-compose.edge.yml
docker compose config -q && docker compose up -d --build
docker compose ps
```

## Проверка

```bash
# токен (Authorization Code + PKCE без браузера)
TOKEN=$(AUTH_URL=https://auth.sirius.test CURL_OPTS=-k ./scripts/get-token.sh)

# создать заказ (повторите команду с тем же ключом: вернётся тот же заказ)
curl -k -s -X POST https://api.sirius.test/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-0001" \
  -d '{"currency":"USD","items":[{"sku":"A-1","quantity":2,"unitPrice":1.50}]}'

# список заказов
curl -k -s "https://api.sirius.test/api/v1/orders?page=0&size=5" -H "Authorization: Bearer $TOKEN"
```

Формат тела запроса сверьте с `CreateOrderRequest` в `order-service`.

## Тесты

```bash
./mvnw clean test
```

## Безопасность репозитория

- Файлы `.env`, ключи и сертификаты (`certs/`) в Git **не коммитятся** (см. `.gitignore`).
- В примерах используются тестовые учётные данные; перед любым боевым запуском замените все пароли и ключи.
- Docker-сокет доступен только через прокси с ограниченным набором разрешённых запросов.

## Ограничения

- Метрики, логи и трассировка (Prometheus, Loki, Tempo, Grafana) в работе.
- Kubernetes не используется: оркестрация через Docker Swarm.
- Учебные упрощения: самоподписанный центр сертификации, один узел Swarm, данные в Compose на одном сервере.
