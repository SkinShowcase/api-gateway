package com.skinsshowcase.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) для API Gateway — описание маршрутов, проксируемых на auth, items, messaging, steam-gateway, trades.
 * Контроллеров в Gateway нет, поэтому спецификация задаётся вручную.
 */
@Configuration
public class OpenApiConfig {

    @Value("${gateway.openapi.public-host}")
    private String openApiPublicHost;

    @Value("${server.port}")
    private int serverPort;

    @Bean
    public OpenAPI gatewayOpenApi() {
        var openApi = new OpenAPI()
                .info(new Info()
                        .title("Skins Showcase — API Gateway")
                        .description("Единая точка входа для клиента (фронт): только этот хост и порт. Запросы проксируются на auth, items, messaging, steam-gateway, trades. " +
                                "Маршруты: /auth/**, /api/v1/items/**, /api/v1/admin/**, /api/admin/messaging/**, /api/chats/**, /ws/messages/** (WebSocket), " +
                                "/api/v1/inventory/**, /api/v1/market/**, /api/v1/trades/**. TCP-уведомления — порт gateway (см. README).")
                        .version("1.0"))
                .addServersItem(new Server().url("http://" + openApiPublicHost + ":" + serverPort).description("Gateway"));

        openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        var bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT от сервиса auth (GET /auth/steam → callback → token в fragment)");
        openApi.components(new Components().addSecuritySchemes("bearerAuth", bearerScheme));

        openApi.path("/auth/steam", authSteamPath());
        openApi.path("/auth/steam/callback", authSteamCallbackPath());
        openApi.path("/auth/session", authSessionPath());
        openApi.path("/auth/me", authMePath());
        openApi.path("/auth/me/privacy", authMePrivacyPath());
        openApi.path("/auth/me/last-online", authMeLastOnlinePath());
        openApi.path("/auth/me/trade-link", authMeTradeLinkPath());
        openApi.path("/auth/me/display-name", authMeDisplayNamePath());
        openApi.path("/auth/avatars", authAvatarsListPath());
        openApi.path("/auth/avatars/{presetId}", authAvatarsImagePath());
        openApi.path("/auth/me/avatar", authMeAvatarPath());
        openApi.path("/auth/documents", authDocumentsListPath());
        openApi.path("/auth/documents/{slug}", authDocumentsGetPath());
        openApi.path("/auth/users/{steamId}/trade-link", authUserTradeLinkPath());
        openApi.path("/auth/users/{steamId}/report", authUserReportPath());
        openApi.path("/auth/users/by-username/{username}", authUsersByUsernamePath());
        openApi.path("/auth/users/preset-avatar-ids", authUsersPresetAvatarIdsPath());
        openApi.path("/api/v1/items", itemsListPath());
        openApi.path("/api/v1/items/prices", itemsPricesBatchPath());
        openApi.path("/api/v1/items/{itemId}", itemsGetPath());
        openApi.path("/api/v1/items/{itemId}/screenshot", itemsScreenshotPath());
        openApi.path("/api/v1/admin/sync", itemsAdminSyncPath());
        openApi.path("/api/chats", messagingChatsPath());
        openApi.path("/api/chats/by-username/{username}", messagingChatByUsernamePath());
        openApi.path("/api/chats/{recipientSteamId}/messages", messagingMessagesPath());
        openApi.path("/api/chats/{counterpartySteamId}/messages/{messageId}", messagingDeleteMessagePath());
        openApi.path("/api/v1/inventory/{steamId}", inventoryPath());
        openApi.path("/api/v1/inventory/{steamId}/item", inventoryItemDetailPath());
        openApi.path("/api/v1/market/cs2/export", marketExportPath());
        openApi.path("/api/v1/trades/selection/{steamId}", tradesSelectionPath());
        openApi.path("/api/v1/trades/showcase/{steamId}", tradesShowcasePath());
        openApi.path("/api/v1/trades/feed", tradesFeedPath());
        openApi.path("/api/v1/trades/feed/sets/filtered", tradesFeedSetsFilteredPath());
        openApi.path("/api/v1/trades/selection/{steamId}/items", tradesSelectionItemsPath());

        return openApi;
    }

    private static PathItem authSteamPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Редирект на Steam OpenID")
                        .description("Вход через Steam. Редирект на steamcommunity.com, затем callback на /auth/steam/callback.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement())
                        .responses(defaultRedirectResponses()));
    }

    private static PathItem authSteamCallbackPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Callback от Steam")
                        .description("Вызывается Steam после логина. Редирект на фронт с токеном в fragment (#token=...).")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement())
                        .responses(defaultRedirectResponses()));
    }

    private static PathItem authSessionPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Проверка JWT и блокировки (лёгкая сессия)")
                        .description("Используется API Gateway перед проксированием запросов с Authorization. " +
                                "204 — токен валиден и пользователь не заблокирован; 401 — нет/невалидный JWT; 403 — аккаунт заблокирован.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("OK"))
                                .addApiResponse("401", new ApiResponse().description("Нет или невалидный JWT"))
                                .addApiResponse("403", new ApiResponse().description("Аккаунт заблокирован"))));
    }

    private static PathItem authMePath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Текущий пользователь")
                        .description("Профиль по JWT. Поля соответствуют MeResponseDto в сервисе auth: steamId, displayName, privateProfile, successfulTradesCount, lastOnlineAt, " +
                                "steamTradeLink, effectiveAvatarUrl (URL пресета), selectedPresetAvatarId (1–8), avatarSource, blocked.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("OK")
                                        .content(new Content().addMediaType("application/json", new MediaType().example(java.util.Map.of(
                                                "steamId", "76561198000000000",
                                                "displayName", "PlayerName",
                                                "privateProfile", false,
                                                "successfulTradesCount", 0,
                                                "lastOnlineAt", "2026-04-25T12:00:00Z",
                                                "steamTradeLink", "https://steamcommunity.com/tradeoffer/new/?partner=123&token=abc",
                                                "effectiveAvatarUrl", "http://localhost:8080/auth/avatars/1",
                                                "selectedPresetAvatarId", 1,
                                                "avatarSource", "PRESET",
                                                "blocked", false
                                        )))))
                                .addApiResponse("401", new ApiResponse().description("Нет или невалидный JWT"))));
    }

    private static PathItem authMePrivacyPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Получить настройку приватности профиля")
                        .description("Текущее значение private (true/false). Без тела запроса.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("OK, тело: {\"private\": true|false}"))
                                .addApiResponse("400", new ApiResponse().description("Неверный запрос"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))))
                .patch(new Operation()
                        .summary("Переключить приватность профиля")
                        .description("Без тела. Текущее true → false, false → true.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("OK"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))));
    }

    private static PathItem authMeLastOnlinePath() {
        return new PathItem()
                .patch(new Operation()
                        .summary("Heartbeat — обновить время последнего онлайна")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("OK"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))));
    }

    private static PathItem authMeTradeLinkPath() {
        return new PathItem()
                .patch(new Operation()
                        .summary("Обновить trade-ссылку текущего пользователя")
                        .description("Тело: {\"tradeUrl\":\"https://steamcommunity.com/tradeoffer/new/?partner=...&token=...\"}. " +
                                "Для сброса: tradeUrl = null или пустая строка.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType()
                                                .schema(new ObjectSchema()
                                                        .addProperty("tradeUrl", new StringSchema()
                                                                .nullable(true)
                                                                .example("https://steamcommunity.com/tradeoffer/new/?partner=123456&token=abcdef")))
                                                .example(java.util.Map.of(
                                                        "tradeUrl", "https://steamcommunity.com/tradeoffer/new/?partner=123456&token=abcdef"
                                                )))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Профиль обновлён (MeResponseDto)"))
                                .addApiResponse("400", new ApiResponse().description("Невалидный формат tradeUrl"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))));
    }

    private static PathItem authMeDisplayNamePath() {
        return new PathItem()
                .patch(new Operation()
                        .summary("Сменить отображаемое имя")
                        .description("Тело: {\"displayName\":\"...\"}. 2–32 символа: буквы, цифры, пробел, _, -. Конфликт — 409.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new ObjectSchema().addProperty("displayName", new StringSchema())))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("MeResponseDto"))
                                .addApiResponse("400", new ApiResponse().description("Невалидное имя"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))
                                .addApiResponse("409", new ApiResponse().description("Имя занято"))));
    }

    private static PathItem authAvatarsListPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Список пресетных аватарок (8 вариантов)")
                        .description("Публично, без JWT. Массив {id, url} — абсолютный URL к файлу пресета (JPEG) на сервере.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement())
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Массив PresetAvatarOptionDto"))));
    }

    private static PathItem authAvatarsImagePath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Файл пресетной аватарки")
                        .description("Публично. presetId от 1 до 8, ответ image/jpeg.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter()
                                .name("presetId")
                                .in("path")
                                .required(true)
                                .schema(new IntegerSchema()))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("JPEG (image/jpeg)"))
                                .addApiResponse("400", new ApiResponse().description("Неверный id"))));
    }

    private static PathItem authMeAvatarPath() {
        return new PathItem()
                .patch(new Operation()
                        .summary("Выбор пресетной аватарки")
                        .description("Тело: {\"presetAvatarId\":1} — только пресеты 1–8 с сервера; отображаемый аватар не переключается на картинку Steam.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new ObjectSchema()
                                                .addRequiredItem("presetAvatarId")
                                                .addProperty("presetAvatarId", new IntegerSchema().description("1–8"))))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("MeResponseDto"))
                                .addApiResponse("400", new ApiResponse().description("Невалидные данные"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))));
    }

    private static PathItem authDocumentsListPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Список юридических документов (метаданные)")
                        .description("Публично, без JWT. Актуальная версия каждого slug.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement())
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Список {slug, version, title, effectiveFrom}"))));
    }

    private static PathItem authDocumentsGetPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Текст юридического документа")
                        .description("Публично. Параметр query version — опционально; без него — последняя по effectiveFrom.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter()
                                .name("slug")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().example("terms")))
                        .addParametersItem(new Parameter()
                                .name("version")
                                .in("query")
                                .required(false)
                                .schema(new IntegerSchema().description("Номер версии")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Полный документ"))
                                .addApiResponse("404", new ApiResponse().description("Не найден"))));
    }

    private static PathItem authUserReportPath() {
        return new PathItem()
                .post(new Operation()
                        .summary("Пожаловаться на пользователя")
                        .description("Тело: {\"reason\":\"...\",\"details\":\"...\" (опционально)}. Не чаще одного репорта на пару за 24 ч.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .addParametersItem(new Parameter()
                                .name("steamId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().example("76561198000000000")))
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new ObjectSchema()
                                                .addProperty("reason", new StringSchema())
                                                .addProperty("details", new StringSchema())))))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("Принято"))
                                .addApiResponse("400", new ApiResponse().description("Невалидные данные / репорт на себя"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))
                                .addApiResponse("409", new ApiResponse().description("Уже был репорт недавно"))));
    }

    private static PathItem authUserTradeLinkPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Получить trade-ссылку пользователя по Steam ID")
                        .description("Требуется JWT. При приватном профиле (и чужом просмотре) возвращается 404.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .addParametersItem(new Parameter()
                                .name("steamId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().example("76561198000000000").description("SteamID64 (17 цифр)")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("OK, тело: {\"tradeUrl\":\"https://...\"}"))
                                .addApiResponse("400", new ApiResponse().description("Неверный формат steamId"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))
                                .addApiResponse("404", new ApiResponse().description("Пользователь не найден / профиль приватный / tradeUrl не задан"))));
    }

    private static PathItem itemsListPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Список скинов с фильтрами и пагинацией")
                        .description("Умный поиск: фильтр по названию (подстрока), по диапазону цены, сортировка. Пагинация: page (0-based), size (1–100). Проксируется в сервис items.")
                        .addTagsItem("Items")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter()
                                .name("query")
                                .in("query")
                                .required(false)
                                .schema(new StringSchema().description("Поиск по названию (подстрока, без учёта регистра)")))
                        .addParametersItem(new Parameter()
                                .name("minPrice")
                                .in("query")
                                .required(false)
                                .schema(new NumberSchema().description("Минимальная цена USD")))
                        .addParametersItem(new Parameter()
                                .name("maxPrice")
                                .in("query")
                                .required(false)
                                .schema(new NumberSchema().description("Максимальная цена USD")))
                        .addParametersItem(new Parameter()
                                .name("sort")
                                .in("query")
                                .required(false)
                                .schema(new StringSchema()
                                        ._enum(List.of("NAME_ASC", "NAME_DESC", "PRICE_ASC", "PRICE_DESC", "UPDATED_DESC"))
                                        ._default("NAME_ASC")
                                        .description("Сортировка")))
                        .addParametersItem(new Parameter()
                                .name("page")
                                .in("query")
                                .required(false)
                                .schema(new IntegerSchema()._default(0).description("Номер страницы (0-based)")))
                        .addParametersItem(new Parameter()
                                .name("size")
                                .in("query")
                                .required(false)
                                .schema(new IntegerSchema()._default(20).description("Размер страницы (1–100)")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Пагинированный список: content[], totalElements, totalPages, size, number"))));
    }

    private static PathItem itemsGetPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Информация о скине")
                        .description("По Steam item_id (classid): название, цена, дата обновления.")
                        .addTagsItem("Items")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter()
                                .name("itemId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().example("310776785")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Данные предмета"))
                                .addApiResponse("404", new ApiResponse().description("Предмет не найден"))));
    }

    private static PathItem itemsScreenshotPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Ссылка на изображение предмета")
                        .description("Параметр size — сторона в пикселях (по умолчанию 360).")
                        .addTagsItem("Items")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter()
                                .name("itemId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema()))
                        .addParametersItem(new Parameter()
                                .name("size")
                                .in("query")
                                .schema(new IntegerSchema()._default(360)))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("URL изображения")
                                        .content(new Content().addMediaType("application/json", new MediaType().example(java.util.Map.of(
                                                "screenshotUrl", "https://community.akamai.steamstatic.com/economy/image/class/730/310776785/360fx360f"
                                        )))))
                                .addApiResponse("404", new ApiResponse().description("Предмет не найден"))));
    }

    private static PathItem messagingChatsPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Список чатов пользователя")
                        .description("Возвращает чаты текущего пользователя. Всегда есть чат поддержки: counterpartySteamId = " +
                                "0".repeat(17) + " (строка из 17 нулей). Поле support=true помечает этот чат. counterpartyPresetAvatarId — id пресета 1–8, если у собеседника PRESET; иначе null (нужен Authorization для обогащения из auth).")
                        .addTagsItem("Messaging")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Список чатов: counterpartySteamId, lastMessagePreview, lastMessageAt, support, counterpartyPresetAvatarId"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))));
    }

    private static PathItem messagingChatByUsernamePath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Найти чат по имени пользователя")
                        .description("Резолв по Steam ID (17 цифр), display name или persona name через auth. Возвращает сводку чата с этим пользователем.")
                        .addTagsItem("Messaging")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .addParametersItem(new Parameter()
                                .name("username")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().description("Steam ID, display name или persona name")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Сводка чата: counterpartySteamId, lastMessagePreview, lastMessageAt, support, counterpartyPresetAvatarId"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))
                                .addApiResponse("404", new ApiResponse().description("Пользователь не найден"))));
    }

    private static PathItem messagingMessagesPath() {
        var pathItem = new PathItem();
        pathItem.post(new Operation()
                .summary("Отправить сообщение")
                .description("JSON-тело с полем text (1–4096 символов, не пустое). Параметр recipientSteamId в пути — SteamID64 получателя. Нельзя отправить сообщение самому себе.")
                .addTagsItem("Messaging")
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addParametersItem(new Parameter()
                        .name("recipientSteamId")
                        .in("path")
                        .required(true)
                        .schema(new StringSchema().example("76561198000000001").description("SteamID64 получателя")))
                .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content().addMediaType("application/json",
                                new MediaType()
                                        .schema(new ObjectSchema()
                                                .addRequiredItem("text")
                                                .addProperty("text", new StringSchema()
                                                        .minLength(1)
                                                        .maxLength(4096)
                                                        .example("Привет!")
                                                        .description("Текст сообщения")))
                                        .example(java.util.Map.of("text", "Привет!")))))
                .responses(new ApiResponses()
                        .addApiResponse("201", new ApiResponse().description("Сообщение создано (тело сообщения)"))
                        .addApiResponse("400", new ApiResponse().description("Пустой/слишком длинный text, неверный recipientSteamId, отправка самому себе"))
                        .addApiResponse("401", new ApiResponse().description("Не авторизован"))));
        pathItem.get(new Operation()
                .summary("История чата с пользователем")
                .description("counterpartySteamId — собеседник. Параметры: page (default 0), size (default 50, max 100).")
                .addTagsItem("Messaging")
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addParametersItem(new Parameter()
                        .name("counterpartySteamId")
                        .in("path")
                        .required(true)
                        .schema(new StringSchema().example("76561198000000001")))
                .addParametersItem(new Parameter()
                        .name("page")
                        .in("query")
                        .schema(new IntegerSchema()._default(0)))
                .addParametersItem(new Parameter()
                        .name("size")
                        .in("query")
                        .schema(new IntegerSchema()._default(50)))
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("Список сообщений"))));
        return pathItem;
    }

    private static PathItem inventoryPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Инвентарь Steam по Steam ID")
                        .description("SteamID64 (17 цифр). Только tradable; медали исключены. У предметов поле catalogMinPriceUsd (каталог items). " +
                                "Кеш до 1 ч. Параметры: appId (default 730), contextId (default 2).")
                        .addTagsItem("Steam Gateway")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter()
                                .name("steamId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().example("76561198000000000")))
                        .addParametersItem(new Parameter().name("appId").in("query").schema(new IntegerSchema()._default(730)))
                        .addParametersItem(new Parameter().name("contextId").in("query").schema(new IntegerSchema()._default(2)))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Список предметов"))
                                .addApiResponse("400", new ApiResponse().description("Некорректный Steam ID"))
                                .addApiResponse("502", new ApiResponse().description("Ошибка Steam API"))));
    }

    private static PathItem inventoryItemDetailPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Один предмет инвентаря с ценой из каталога items")
                        .description("Параметры как у trades при выборе предметов: assetId + classId (Steam classid). " +
                                "steam-gateway проверяет наличие в инвентаре Steam и запрашивает цену у сервиса items. " +
                                "catalogPrice: null, если предмета нет в каталоге, цена не задана или items недоступен.")
                        .addTagsItem("Steam Gateway")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter()
                                .name("steamId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().example("76561198000000000")))
                        .addParametersItem(new Parameter()
                                .name("assetId")
                                .in("query")
                                .required(true)
                                .schema(new StringSchema().example("12345678901").description("Steam asset id")))
                        .addParametersItem(new Parameter()
                                .name("classId")
                                .in("query")
                                .required(true)
                                .schema(new StringSchema().example("310776785").description("Steam classid (item_id в items)")))
                        .addParametersItem(new Parameter().name("appId").in("query").schema(new IntegerSchema()._default(730)))
                        .addParametersItem(new Parameter().name("contextId").in("query").schema(new IntegerSchema()._default(2)))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Предмет и опционально цена каталога"))
                                .addApiResponse("400", new ApiResponse().description("Некорректные параметры"))
                                .addApiResponse("404", new ApiResponse().description("Нет такого предмета в инвентаре"))
                                .addApiResponse("502", new ApiResponse().description("Ошибка Steam API"))));
    }

    private static PathItem marketExportPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Экспорт цен CS2 (lis-skins)")
                        .description("Сырой JSON для сервиса items.")
                        .addTagsItem("Steam Gateway")
                        .addSecurityItem(new SecurityRequirement())
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Экспорт получен"))
                                .addApiResponse("502", new ApiResponse().description("Ошибка lis-skins"))));
    }

    private static PathItem authUsersByUsernamePath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Найти пользователя по имени")
                        .description("Резолв по Steam ID (17 цифр), display_name или persona name. Для чатов и др.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .addParametersItem(new Parameter()
                                .name("username")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().description("Steam ID, display name или persona name")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("steamId")
                                        .content(new Content().addMediaType("application/json", new MediaType().example(java.util.Map.of(
                                                "steamId", "76561198000000001"
                                        )))))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))
                                .addApiResponse("404", new ApiResponse().description("Пользователь не найден"))));
    }

    private static PathItem authUsersPresetAvatarIdsPath() {
        return new PathItem()
                .post(new Operation()
                        .summary("Пакетно: id пресетной аватарки по Steam ID")
                        .description("Тело: {\"steamIds\":[\"765611...\"] } — до 100 уникальных SteamID64. Ответ: presetAvatarIdBySteamId — для каждого id значение 1–8 или null (пользователь не в БД). Вызывается из messaging для списка чатов.")
                        .addTagsItem("Auth")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType()
                                                .schema(new ObjectSchema()
                                                        .addRequiredItem("steamIds")
                                                        .addProperty("steamIds", new ArraySchema()
                                                                .items(new StringSchema().example("76561198000000001"))))
                                                .example(java.util.Map.of(
                                                        "steamIds", java.util.List.of("76561198000000001", "76561198000000002")
                                                )))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("presetAvatarIdBySteamId: map steamId → 1..8 | null")
                                        .content(new Content().addMediaType("application/json", new MediaType().example(java.util.Map.of(
                                                "presetAvatarIdBySteamId", java.util.Map.of(
                                                        "76561198000000001", 2,
                                                        "76561198000000002", 1
                                                )
                                        )))))
                                .addApiResponse("400", new ApiResponse().description("Неверный steamId в списке или >100 id"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))));
    }

    private static PathItem itemsPricesBatchPath() {
        return new PathItem()
                .post(new Operation()
                        .summary("Пакет цен по Steam classid")
                        .description("Тело: {\"itemIds\":[\"...\"] } до 500 id. В ответе только записи из каталога.")
                        .addTagsItem("Items")
                        .addSecurityItem(new SecurityRequirement())
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType()
                                                .schema(new ObjectSchema()
                                                        .addRequiredItem("itemIds")
                                                        .addProperty("itemIds", new ArraySchema()
                                                                .items(new StringSchema().example("310776785"))))
                                                .example(java.util.Map.of(
                                                        "itemIds", java.util.List.of("310776785", "1812814373")
                                                )))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("{\"prices\": { itemId: ItemResponseDto }}"))
                                .addApiResponse("400", new ApiResponse().description("Пустой список или >500 id"))));
    }

    private static PathItem itemsAdminSyncPath() {
        return new PathItem()
                .post(new Operation()
                        .summary("Запустить синхронизацию каталога с steam-gateway (admin)")
                        .description("Доступно только при профилях local или docker у сервиса items. Тело не требуется. Заголовок: X-Admin-Api-Key.")
                        .addTagsItem("Items Admin")
                        .addSecurityItem(new SecurityRequirement())
                        .requestBody(new RequestBody().required(false))
                        .addParametersItem(new Parameter()
                                .name("X-Admin-Api-Key")
                                .in("header")
                                .required(true)
                                .schema(new StringSchema().example("change-me-admin-api-key")))
                        .responses(new ApiResponses()
                                .addApiResponse("202", new ApiResponse().description("Синхронизация принята, выполняется в фоне")
                                        .content(new Content().addMediaType("application/json", new MediaType().example(java.util.Map.of(
                                                "message", "Sync request received. Sync runs in background, see logs for progress."
                                        )))))
                                .addApiResponse("401", new ApiResponse().description("Неверный admin key"))
                                .addApiResponse("503", new ApiResponse().description("Admin API не сконфигурирован / профиль не local|docker"))));
    }

    private static PathItem messagingDeleteMessagePath() {
        return new PathItem()
                .delete(new Operation()
                        .summary("Удалить сообщение в чате")
                        .description("Только участник чата (отправитель или получатель). Удаление только если сообщению меньше 24 ч; иначе 409.")
                        .addTagsItem("Messaging")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .addParametersItem(new Parameter()
                                .name("counterpartySteamId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().description("Steam ID собеседника (counterparty)")))
                        .addParametersItem(new Parameter()
                                .name("messageId")
                                .in("path")
                                .required(true)
                                .schema(new StringSchema().format("uuid")))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("Удалено"))
                                .addApiResponse("401", new ApiResponse().description("Не авторизован"))
                                .addApiResponse("403", new ApiResponse().description("Не участник чата"))
                                .addApiResponse("404", new ApiResponse().description("Сообщение не найдено"))
                                .addApiResponse("409", new ApiResponse().description("Сообщение старше 24 ч"))));
    }

    private static PathItem tradesSelectionPath() {
        var steamIdParam = new Parameter()
                .name("steamId")
                .in("path")
                .required(true)
                .schema(new StringSchema().example("76561198000000000").description("SteamID64"));
        var getOp = new Operation()
                .summary("Получить набор предметов для обмена")
                .description("Один набор на пользователя (до 5 предметов). В ответе steamId — владелец. " +
                        "Если профиль владельца приватный в auth, чужие пользователи получают 404; владелец может добавить Authorization: Bearer <JWT>.")
                .addTagsItem("Trades")
                .addSecurityItem(new SecurityRequirement())
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addParametersItem(steamIdParam)
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Набор найден"))
                        .addApiResponse("404", new ApiResponse().description("Набор не найден")));
        var putOp = new Operation()
                .summary("Создать или обновить набор предметов для обмена")
                .description("Тело: {\"items\": [{\"assetId\", \"classId\"}]}, не более 5 элементов. Предметы должны быть из инвентаря пользователя. " +
                        "Требуется Authorization: Bearer <JWT> и sub токена должен совпадать с steamId в пути.")
                .addTagsItem("Trades")
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addParametersItem(new Parameter().name("steamId").in("path").required(true).schema(new StringSchema()))
                .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content().addMediaType("application/json",
                                new MediaType()
                                        .schema(new ObjectSchema()
                                                .addRequiredItem("items")
                                                .addProperty("items", new ArraySchema().items(new ObjectSchema()
                                                        .addRequiredItem("assetId")
                                                        .addRequiredItem("classId")
                                                        .addProperty("assetId", new StringSchema().example("12345678901"))
                                                        .addProperty("classId", new StringSchema().example("310776785")))))
                                        .example(java.util.Map.of(
                                                "items", java.util.List.of(
                                                        java.util.Map.of("assetId", "12345678901", "classId", "310776785")
                                                )
                                        )))))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Набор сохранён"))
                        .addApiResponse("400", new ApiResponse().description("Невалидный запрос или предметы не из инвентаря"))
                        .addApiResponse("401", new ApiResponse().description("Нет/невалидный JWT"))
                        .addApiResponse("403", new ApiResponse().description("JWT не совпадает с steamId в пути"))
                        .addApiResponse("502", new ApiResponse().description("Ошибка steam-gateway")));
        var deleteOp = new Operation()
                .summary("Удалить набор предметов для обмена")
                .description("Требуется Authorization: Bearer <JWT> и sub токена должен совпадать с steamId в пути.")
                .addTagsItem("Trades")
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addParametersItem(new Parameter().name("steamId").in("path").required(true).schema(new StringSchema()))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Набор удалён"))
                        .addApiResponse("401", new ApiResponse().description("Нет/невалидный JWT"))
                        .addApiResponse("403", new ApiResponse().description("JWT не совпадает с steamId в пути")));
        return new PathItem().get(getOp).put(putOp).delete(deleteOp);
    }

    private static PathItem tradesShowcasePath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Набор пользователя (витрина) по Steam ID")
                        .description("Эквивалентно GET /api/v1/trades/selection/{steamId}: набор до 5 предметов, в ответе steamId владельца. " +
                                "При приватном профиле чужие получают 404; владелец может добавить Authorization: Bearer <JWT>.")
                        .addTagsItem("Trades")
                        .addSecurityItem(new SecurityRequirement())
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .addParametersItem(new Parameter().name("steamId").in("path").required(true).schema(new StringSchema().example("76561198000000000")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Набор найден"))
                                .addApiResponse("404", new ApiResponse().description("Набор не найден"))));
    }

    private static PathItem tradesFeedPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Лента предложений для обмена")
                        .description("Страница наборов (один на пользователя, до 5 предметов). В каждом элементе steamId — владелец. Сортировка по дате обновления. excludeSteamId — исключить набор текущего пользователя.")
                        .addTagsItem("Trades")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter().name("page").in("query").schema(new IntegerSchema()._default(0)))
                        .addParametersItem(new Parameter().name("size").in("query").schema(new IntegerSchema()._default(20)))
                        .addParametersItem(new Parameter().name("excludeSteamId").in("query").schema(new StringSchema().description("Steam ID — его набор не попадёт в ленту")))
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("Страница предложений (content, totalElements, totalPages)"))));
    }

    private static PathItem tradesFeedSetsFilteredPath() {
        return new PathItem()
                .get(new Operation()
                        .summary("Наборы из ленты с фильтрами")
                        .description("Возвращает наборы, у которых хотя бы один предмет проходит фильтры; в ответе целые наборы с steamId владельца. Фильтры: float, название, коллекция, износ, паттерн, type, special, minPriceUsd/maxPriceUsd (каталог items). excludeSteamId — исключить набор пользователя.")
                        .addTagsItem("Trades")
                        .addSecurityItem(new SecurityRequirement())
                        .addParametersItem(new Parameter().name("minFloat").in("query").schema(new NumberSchema().description("0–1")))
                        .addParametersItem(new Parameter().name("maxFloat").in("query").schema(new NumberSchema().description("0–1")))
                        .addParametersItem(new Parameter().name("name").in("query").schema(new StringSchema()))
                        .addParametersItem(new Parameter().name("collectionName").in("query").schema(new StringSchema()))
                        .addParametersItem(new Parameter().name("wearName").in("query").schema(new StringSchema()))
                        .addParametersItem(new Parameter().name("pattern").in("query").schema(new IntegerSchema()))
                        .addParametersItem(new Parameter().name("type").in("query").schema(new StringSchema()))
                        .addParametersItem(new Parameter().name("special").in("query").schema(new StringSchema()._enum(List.of("STATTRACK", "SOUVENIR", "NORMAL"))))
                        .addParametersItem(new Parameter().name("minPriceUsd").in("query").schema(new NumberSchema()))
                        .addParametersItem(new Parameter().name("maxPriceUsd").in("query").schema(new NumberSchema()))
                        .addParametersItem(new Parameter().name("excludeSteamId").in("query").schema(new StringSchema().description("Не включать набор этого Steam ID")))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Список наборов с предметами"))
                                .addApiResponse("502", new ApiResponse().description("Ошибка steam-gateway / items"))));
    }

    private static PathItem tradesSelectionItemsPath() {
        return new PathItem()
                .delete(new Operation()
                        .summary("Удалить предметы из набора для обмена")
                        .description("Тело: {\"items\": [{\"assetId\", \"classId\"}]}. Убирает скины из списка участвующих в обмене. " +
                                "Как и PUT/DELETE /api/v1/trades/selection/{steamId}: TradesOwnerJwtFilter требует Authorization: Bearer <JWT>, sub токена должен совпадать с steamId в пути.")
                        .addTagsItem("Trades")
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                        .addParametersItem(new Parameter().name("steamId").in("path").required(true).schema(new StringSchema().example("76561198000000000")))
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType()
                                                .schema(new ObjectSchema()
                                                        .addRequiredItem("items")
                                                        .addProperty("items", new ArraySchema().items(new ObjectSchema()
                                                                .addRequiredItem("assetId")
                                                                .addRequiredItem("classId")
                                                                .addProperty("assetId", new StringSchema().example("12345678901"))
                                                                .addProperty("classId", new StringSchema().example("310776785")))))
                                                .example(java.util.Map.of(
                                                        "items", java.util.List.of(
                                                                java.util.Map.of("assetId", "12345678901", "classId", "310776785")
                                                        )
                                                )))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().description("Набор обновлён"))
                                .addApiResponse("400", new ApiResponse().description("Невалидное тело запроса (валидация)"))
                                .addApiResponse("401", new ApiResponse().description("Нет/невалидный JWT"))
                                .addApiResponse("403", new ApiResponse().description("JWT не совпадает с steamId в пути"))
                                .addApiResponse("404", new ApiResponse().description("Набор не найден"))));
    }

    private static ApiResponses defaultRedirectResponses() {
        return new ApiResponses()
                .addApiResponse("302", new ApiResponse().description("Редирект"));
    }
}
