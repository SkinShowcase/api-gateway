# API Gateway (Skins Showcase)

Единая точка входа для клиентов (браузер, мобильное приложение): **один хост и порт** для REST, WebSocket и проверки сессии перед проксированием.

Репозиторий сервиса: https://github.com/SkinShowcase/api-gateway  
Полный стек в Docker: https://github.com/SkinShowcase/infrastructure (`docker-compose.yml`).

## Назначение

- Проксирует HTTP-запросы на микросервисы **без изменения path**.
- Перед проксированием для запросов с `Authorization: Bearer …` вызывает `GET {auth}/auth/session` (JWT + блокировка аккаунта).
- Поднимает **TCP-прокси** для уведомлений о новых сообщениях: клиент подключается к порту gateway, трафик уходит в `messaging` (см. ниже).

## Стек

- Java 21, Spring Boot 3.2, Spring Cloud Gateway (reactive)
- Actuator: `/actuator/health`, `/actuator/prometheus`
- OpenAPI агрегатор на gateway: `/swagger-ui.html`, `/api-docs` (часть описаний задаётся вручную в `OpenApiConfig`)

## Порт и профили

- HTTP: `SERVER_PORT` (по умолчанию **8080**)
- Профили: обычно `default`; в Docker используется `docker` (`src/main/resources/application-docker.yml`) с DNS-именами сервисов.

## Маршрутизация (актуально из `application.yml` / `application-docker.yml`)

| Path prefix | Бэкенд (env по умолчанию для локали) |
|-------------|--------------------------------------|
| `/auth/**` | `GATEWAY_AUTH_BASE_URL` → `http://localhost:8081` |
| `/api/v1/items/**` | `GATEWAY_ITEMS_BASE_URL` → `http://localhost:8083` |
| `/api/v1/admin/**` | тот же `items` |
| `/api/admin/messaging/**` | `GATEWAY_MESSAGING_BASE_URL` → `http://localhost:8082` |
| `/ws/messages/**` | `messaging` (WebSocket upgrade) |
| `/api/chats/**` | `messaging` |
| `/api/v1/inventory/**` | `GATEWAY_STEAM_GATEWAY_BASE_URL` → `http://localhost:8084` |
| `/api/v1/market/**` | `steam-gateway` |
| `/api/v1/trades/**` | `GATEWAY_TRADES_BASE_URL` → `http://localhost:8085` |

Важно: **внутренние** эндпоинты бэкендов (`/internal/**`, `/auth/internal/**`) через gateway **не публикуются** — ими пользуются только сервисы внутри сети/Docker.

## Проверка сессии

Для запросов с заголовком `Authorization: Bearer <JWT>` gateway перед проксированием дергает:

- `GET {GATEWAY_AUTH_BASE_URL}/auth/session` → `204` ок, `401` нет/битый токен, `403` заблокирован.

## TCP-уведомления о сообщениях

- Порт на gateway: `GATEWAY_TCP_NOTIFICATIONS_PORT` (по умолчанию **9092**)
- Прокси на `messaging`: `GATEWAY_MESSAGING_TCP_HOST` / `GATEWAY_MESSAGING_TCP_PORT` (по умолчанию `localhost:9090`)

Протокол такой же, как у `messaging` TCP-сервера: первая строка — JWT, далее строки событий (`NEW_MESSAGE …`, `PING`/`PONG`).

## CORS

`CORS_ALLOWED_ORIGINS` — список origin через запятую (см. `application.yml`).

## Клиент (фронт)

- REST/WebSocket: только gateway (`http(s)://<host>:8080/...` в типичной локальной схеме).
- Steam login: `GET /auth/steam` и callback `GET /auth/steam/callback` на **том же публичном хосте**, что и `AUTH_STEAM_REALM` / `AUTH_STEAM_RETURN_TO` у `auth`.
- WebSocket чатов: `ws(s)://<gateway>/ws/messages?token=<JWT>` (см. `messaging`).

## Docker

- Образ собирается из этого репозитория: `Dockerfile` в корне сервиса (`docker build -t skins-showcase/api-gateway .`).
- Полный compose: https://github.com/SkinShowcase/infrastructure

## Заметка про OpenAPI на gateway

`OpenApiConfig` описывает публичные маршруты для Swagger UI на gateway. Если заметили расхождение с фактическим телом ответа бэкенда — **истина в коде контроллеров сервиса**, а gateway-док можно поправить отдельным PR.
