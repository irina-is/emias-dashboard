# Маршрутизация — инструкция для AI-ассистентов

Этот файл описывает как устроена маршрутизация в проекте.
Нарушение этих правил приводит к 404.

---

## Как работает `/spec` префикс

`app.base-path=/spec` — переменная, которая инжектируется во все Thymeleaf-шаблоны
через `GlobalModelAttributes.java`. Она используется для формирования ссылок и редиректов.

На **локальной** разработке (профиль `local`) также задан `server.servlet.context-path=/spec`
в `application-local.properties`. Благодаря ему Spring Boot принимает URL `/spec/dashboard`
и передаёт контроллеру путь `/dashboard`.

На **проде** `server.servlet.context-path` не установлен. Как именно работает маршрутизация
на проде — зависит от конфигурации сервера (Nginx и т.д.), которая не хранится в репозитории.
**Не меняй настройки маршрутизации без понимания продовой конфигурации.**

---

## Правила для контроллеров

Все существующие контроллеры маппятся **без** `/spec`. Добавляй новые маршруты так же.

```java
// ✅ Правильно — следуй существующему паттерну
@GetMapping("/my-new-page")
public String myPage(Model model) { ... }

@GetMapping("/api/my-data")
public ResponseEntity<?> getData() { ... }

// ❌ Неправильно — не добавляй /spec в маппинг
@GetMapping("/spec/my-new-page")
```

Существующие маппинги в `PageController.java`:
`/`, `/dashboard`, `/hepatitis`, `/pcr-dashboard`, `/naznachenie`, `/scheme-dashboard`, `/login`, `/admin`

Все API маппятся через `@RequestMapping("/api")` в `DataController.java`.

---

## Правила для шаблонов Thymeleaf

Все ссылки, href, action, src — только через `${basePath}`. Никаких других вариантов.

```html
<!-- ✅ Правильно -->
<a th:href="${basePath + '/dashboard'}">Дашборд</a>
<form th:action="${basePath + '/api/upload'}" method="post">
<link th:href="${basePath + '/css/style.css'}" rel="stylesheet">

<!-- ❌ Неправильно — любой из этих вариантов может сломать маршрутизацию -->
<a href="/dashboard">                          <!-- нет basePath -->
<a href="/spec/dashboard">                     <!-- захардкоженный /spec -->
<a th:href="@{/dashboard}">                    <!-- @{} добавляет context-path сверху basePath → /spec/spec/ на локале -->
<a th:href="@{${basePath + '/dashboard'}}">    <!-- то же самое -->
```

`basePath` = `/spec` на всех средах. Инжектируется автоматически через
`GlobalModelAttributes.java` — добавлять в каждый контроллер вручную не нужно.

---

## Правила для JavaScript

В начале каждого `<script>` блока объявляется:

```javascript
const BASE_PATH = /*[[${basePath}]]*/ '';
// Thymeleaf подставит '/spec' при рендере шаблона
```

Все fetch-запросы и JS-редиректы — только через эту константу:

```javascript
// ✅ Правильно
fetch(BASE_PATH + '/api/hcv/weekly-plan', { method: 'GET' })
window.location.href = BASE_PATH + '/dashboard';

// ❌ Неправильно
fetch('/api/hcv/weekly-plan')           // нет BASE_PATH
fetch('/spec/api/hcv/weekly-plan')      // захардкоженный /spec
```

---

## Правила для Spring Security (SecurityConfig.java)

`requestMatchers` в SecurityConfig матчит пути **без** `/spec` — следуй существующему паттерну:

```java
// ✅ Правильно — путь без /spec (как в существующем коде)
.requestMatchers("/my-new-page").permitAll()
.requestMatchers("/api/my-new-endpoint").permitAll()

// ❌ Неправильно
.requestMatchers("/spec/my-new-page").permitAll()
```

**Новая публичная страница** → добавить в список permitAll:
```java
.requestMatchers("/", "/dashboard", "/hepatitis", "/my-new-page").permitAll()
```

**Новый публичный API** → добавить отдельной строкой:
```java
.requestMatchers("/api/my-new-endpoint").permitAll()
```

Если эндпоинт не добавить в `permitAll` — неавторизованный пользователь
получит редирект на страницу логина вместо данных.

---

## Конфигурация по средам

| Параметр | Прод | Локально (профиль `local`) |
|---|---|---|
| `server.port` | 8081 | 8081 |
| `server.servlet.context-path` | **не установлен** | `/spec` |
| `app.base-path` | `/spec` | `/spec` |
| БД | H2 file `/opt/emias-dashboard/data/emias_db` | H2 file `./data/emias_db` |
| Uploads dir | `/opt/emias-uploads` | `./uploads` |
| H2 Console | выключена | включена |

Прод-конфиг лежит на сервере, **не в репозитории**.
Файл `application.properties` в JAR — дефолты для локальной разработки.
Прод-файл переопределяет только отличия.

**Никогда не добавлять** `server.servlet.context-path` в бандловый `application.properties` —
только в `application-local.properties`. Иначе сломается прод.

---

## Чеклист при добавлении нового маршрута

- [ ] Контроллер: `@GetMapping("/my-page")` — **без** `/spec`, как в существующих маппингах
- [ ] Шаблон: все ссылки `th:href="${basePath + '/my-page'}"` — через `${basePath}`, не `@{}`
- [ ] JS: все fetch и редиректы через `BASE_PATH + '/...'`
- [ ] SecurityConfig: публичная страница → добавить в `requestMatchers("/my-page").permitAll()`
- [ ] SecurityConfig: публичный API → добавить в `requestMatchers("/api/my-endpoint").permitAll()`
- [ ] Не трогать `server.servlet.context-path` в `application.properties`
