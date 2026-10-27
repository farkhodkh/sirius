#!/usr/bin/env bash
set -euo pipefail          # -e стоп при ошибке; -u ошибка на пустой переменной; pipefail — ошибка в конвейере

# Функция создаёт БД, схему, роль-мигратор и роль-приложение для одного сервиса.
# Аргументы: имя_бд  префикс_ролей  пароль_мигратора  пароль_приложения
create_service_db () {
  local db="$1" prefix="$2" mig_pass="$3" app_pass="$4"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
    -- роли: LOGIN = можно подключаться; NOSUPERUSER/NOCREATEDB — минимум полномочий
    CREATE ROLE ${prefix}_migrator LOGIN PASSWORD '${mig_pass}' NOSUPERUSER NOCREATEDB NOCREATEROLE;
    CREATE ROLE ${prefix}_app      LOGIN PASSWORD '${app_pass}' NOSUPERUSER NOCREATEDB NOCREATEROLE;

    -- владелец БД — мигратор
    CREATE DATABASE ${db} OWNER ${prefix}_migrator;

    -- по умолчанию PUBLIC может подключаться к любой БД — запрещаем
    REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
    GRANT CONNECT ON DATABASE ${db} TO ${prefix}_app;
SQL

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${db}" <<-SQL
    -- схема public по умолчанию доступна всем на CREATE — закрываем; используем свою схему
    REVOKE CREATE ON SCHEMA public FROM PUBLIC;
    CREATE SCHEMA app AUTHORIZATION ${prefix}_migrator;

    GRANT USAGE ON SCHEMA app TO ${prefix}_app;   -- «видеть» схему
    -- права на объекты, которые мигратор создаст В БУДУЩЕМ (иначе после каждой миграции пришлось бы делать GRANT)
    ALTER DEFAULT PRIVILEGES FOR ROLE ${prefix}_migrator IN SCHEMA app
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${prefix}_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE ${prefix}_migrator IN SCHEMA app
        GRANT USAGE, SELECT ON SEQUENCES TO ${prefix}_app;

    -- search_path: приложение работает со схемой app без префикса
    ALTER ROLE ${prefix}_app      SET search_path = app;
    ALTER ROLE ${prefix}_migrator SET search_path = app;
SQL

  # статистика запросов: расширение создаём в каждой БД (требует суперпользователя, поэтому здесь, а не в миграциях)
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${db}" -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
}

create_service_db auth_db   auth  "$AUTH_MIGRATOR_PASSWORD"  "$AUTH_APP_PASSWORD"
create_service_db orders_db order "$ORDER_MIGRATOR_PASSWORD" "$ORDER_APP_PASSWORD"
create_service_db notif_db  notif "$NOTIF_MIGRATOR_PASSWORD" "$NOTIF_APP_PASSWORD"
