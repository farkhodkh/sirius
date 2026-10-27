#!/usr/bin/env bash
# Выпускает ТЕСТОВЫЙ JWT (HS256) для профиля dev. Использование: ./dev-token.sh [user-uuid] [email] [scope]
set -euo pipefail
SECRET="${SIRIUS_DEV_JWT_SECRET:?задайте SIRIUS_DEV_JWT_SECRET (не короче 32 символов)}"
SUB="${1:-11111111-1111-1111-1111-111111111111}"
EMAIL="${2:-dev@example.com}"
SCOPE="${3:-orders.read orders.write}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }      # base64url без заполнения "="
NOW=$(date +%s); EXP=$((NOW + 3600))                              # токен живёт 1 час
HEADER=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
PAYLOAD=$(printf '{"sub":"%s","email":"%s","scope":"%s","iat":%s,"exp":%s}' "$SUB" "$EMAIL" "$SCOPE" "$NOW" "$EXP" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)
printf '%s.%s.%s\n' "$HEADER" "$PAYLOAD" "$SIG"