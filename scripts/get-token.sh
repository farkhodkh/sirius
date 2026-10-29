#!/usr/bin/env bash
set -euo pipefail
AUTH_URL="${AUTH_URL:-http://localhost:9000}"
EMAIL="${1:-anna@example.com}"
PASSWORD="${2:-s3cret-pass}"
CLIENT_ID="${CLIENT_ID:-sirius-web}"
REDIRECT_URI="${REDIRECT_URI:-http://localhost:5173/callback}"
SCOPE="${SCOPE:-openid email orders.read orders.write}"
JAR=$(mktemp); trap 'rm -f "$JAR"' EXIT
fail() { echo "ОШИБКА: $*" >&2; exit 1; }
VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
CHALLENGE=$(printf %s "$VERIFIER" | openssl dgst -binary -sha256 | openssl base64 -A | tr '+/' '-_' | tr -d '=')
CSRF=$(curl -s -c "$JAR" "$AUTH_URL/login" | grep -o 'name="_csrf" type="hidden" value="[^"]*"' | sed 's/.*value="//; s/"$//' || true)
[ -n "$CSRF" ] || fail "не удалось открыть форму входа $AUTH_URL/login (сервис запущен? туннель открыт?)"
LOGIN_LOCATION=$(curl -s -o /dev/null -D - -b "$JAR" -c "$JAR" \
    --data-urlencode "username=$EMAIL" --data-urlencode "password=$PASSWORD" --data-urlencode "_csrf=$CSRF" \
    "$AUTH_URL/login" | tr -d '\r' | awk 'tolower($1)=="location:" {print $2}')
case "$LOGIN_LOCATION" in
  *error*) fail "вход не удался: неверный e-mail/пароль или e-mail не подтверждён" ;;
  "")      fail "вход не удался: сервер не вернул перенаправление" ;;
esac
AUTHORIZE_URL="$AUTH_URL/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&scope=${SCOPE// /%20}&redirect_uri=$REDIRECT_URI&code_challenge=$CHALLENGE&code_challenge_method=S256"
LOCATION=$(curl -s -o /dev/null -D - -b "$JAR" "$AUTHORIZE_URL" | tr -d '\r' | awk 'tolower($1)=="location:" {print $2}')
case "$LOCATION" in
  "$REDIRECT_URI"?code=*) ;;
  *error*) fail "сервер отклонил запрос авторизации: $LOCATION" ;;
  *)       fail "не получен code (Location: ${LOCATION:-пусто})" ;;
esac
CODE="${LOCATION#*code=}"; CODE="${CODE%%&*}"
RESPONSE=$(curl -s "$AUTH_URL/oauth2/token" -d grant_type=authorization_code -d client_id="$CLIENT_ID" \
    -d code="$CODE" -d redirect_uri="$REDIRECT_URI" -d code_verifier="$VERIFIER")
if [ "${PRINT_JSON:-}" = "1" ]; then echo "$RESPONSE"; exit 0; fi
TOKEN=$(printf %s "$RESPONSE" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || fail "обмен кода не удался: $RESPONSE"
echo "$TOKEN"
