# syntax=docker/dockerfile:1

# Один Dockerfile на все сервисы. Какой сервис собирать, задаёт аргумент SERVICE:
#   docker build -f deploy/docker/Dockerfile --build-arg SERVICE=order-service -t sirius/order-service:0.1.0 .

# ---------- Стадия 1: сборка ----------
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace

# Весь проект (лишнее отсекает .dockerignore). Для сборки одного модуля Maven нужны родительский pom,
# все объявленные в нём модули и библиотека sirius-events: флаг -am соберёт её вместе с сервисом.
COPY . .

# Кэш ~/.m2 живёт между сборками: зависимости скачиваются один раз, а не при каждой правке кода.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl services/${SERVICE} -am -DskipTests package

# Распаковать jar по слоям и дать ему постоянное имя (в имени jar есть версия, а ENTRYPOINT от неё зависеть не должен).
RUN java -Djarmode=tools -jar services/${SERVICE}/target/${SERVICE}-*.jar extract --layers --destination /extracted \
 && mv /extracted/application/${SERVICE}-*.jar /extracted/application/app.jar

# ---------- Стадия 2: рантайм ----------
FROM eclipse-temurin:21-jre-alpine

# Непривилегированный пользователь: процесс в контейнере не должен работать от root.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Слои копируются от редко меняющихся к часто меняющимся: при правке кода Docker пересоберёт только последний.
# dependencies - сторонние библиотеки (десятки мегабайт, меняются редко);
# snapshot-dependencies - сторонние SNAPSHOT-библиотеки (у нас обычно пусто);
# application - код сервиса и модули нашего проекта (sirius-events лежит в application/lib/), несколько килобайт.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

USER app

# JVM берёт 75% памяти, выделенной КОНТЕЙНЕРУ (а не всего сервера); при нехватке памяти процесс завершается и перезапускается.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Готовность проверяется на порту управления 9091 (его задаёт MANAGEMENT_SERVER_PORT в compose).
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -q --spider http://localhost:9091/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]