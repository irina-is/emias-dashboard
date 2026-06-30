# Маршрутизация — инструкция для AI-ассистентов

Этот файл описывает как устроена маршрутизация в проекте.
Нарушение этих правил приводит к 404.

---

## Как работает `/spec` префикс

### Прод (onko-search.emias.mosreg.ru) — деплой только по согласованию

На сервере стоит Nginx. Конфиг (`/etc/nginx/sites-enabled/`):

```nginx
server {
    server_name onko-search.emias.mosreg.ru;
    listen 80;

    # Python-приложение (uvicorn, порт 8000)
    location / {
        include proxy_params;
        proxy_pass http://127.0.0.1:8000;
    }

    # Java-приложение (Spring Boot, порт 8081)
    location /spec/ {
        include proxy_params;
        proxy_pass http://127.0.0.1:8081/;   # trailing slash — стрипит /spec/
    }
}
```

Цепочка запроса:
```
Браузер → https://onko-search.emias.mosreg.ru/spec/dashboard
                          ↓
                       Nginx  (стрипит /spec/)
                          ↓
             Spring Boot :8081 → /dashboard → PageController
```

Именно поэтому:
- `app.base-path=/spec` — шаблоны генерируют ссылки `/spec/xxx`
- Nginx получает `/spec/xxx`, стрипит префикс, передаёт Spring Boot `/xxx`
- Контроллеры маппятся **без** `/spec` (`@GetMapping("/dashboard")`)
- `server.servlet.context-path` на проде **не нужен** — роль префикса выполняет Nginx

### Локальная разработка (профиль `local`)

Nginx нет. Роль стрипинга выполняет `server.servlet.context-path=/spec`
из `application-local.properties`:

```
Браузер → http://localhost:8081/spec/dashboard
                          ↓
             Spring Boot (context-path=/spec) → /dashboard → PageController
```

**Не добавлять** `server.servlet.context-path` в бандловый `application.properties` —
только в `application-local.properties`. Иначе на проде появится двойной `/spec/spec/`.

### Добавление нового сервиса на прод

Новый сервис на порту `808X` — добавить `location` в Nginx:
```nginx
location /новый-путь/ {
    include proxy_params;
    proxy_pass http://127.0.0.1:808X/;
}
```

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

### IIFE-блоки (function() { ... })()

Шаблоны используют IIFE для изоляции переменных. `BASE_PATH` объявленный снаружи **недоступен внутри** IIFE — каждый блок должен объявить его сам.

```javascript
// ✅ Правильно — BASE_PATH объявлен внутри IIFE
(function() {
    const BASE_PATH = /*[[${basePath}]]*/ '';

    function loadData() {
        fetch(BASE_PATH + '/api/contracts')
    }
    loadData();
})();

// ❌ Неправильно — BASE_PATH из другого блока сюда не виден
(function() {
    function loadData() {
        fetch(BASE_PATH + '/api/contracts')  // ReferenceError: BASE_PATH is not defined
    }
    loadData();
})();
```

**Правило:** добавляешь новый IIFE-блок с fetch — первой строкой объявляй `const BASE_PATH = /*[[${basePath}]]*/ '';`.

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
| Среда | Прод (`onko-search.emias.mosreg.ru`) | Локально |
| URL | `https://onko-search.emias.mosreg.ru/spec/` | `http://localhost:8081/spec/` |
| `server.port` | 8081 | 8081 |
| `server.servlet.context-path` | **не установлен** | `/spec` |
| `app.base-path` | `/spec` | `/spec` |
| Nginx | есть, `location /spec/` → `:8081` | нет |
| БД | H2 file `/opt/emias-dashboard/data/emias_db` | H2 file `./data/emias_db` |
| Uploads dir | `/opt/emias-uploads` | `./uploads` |
| H2 Console | выключена | включена |

Прод-конфиг лежит на сервере в `/opt/emias-dashboard/application.properties`, **не в репозитории**.
Файл `application.properties` в JAR — дефолты для локальной разработки.
Прод-файл переопределяет только отличия.

---

## Чеклист при добавлении нового маршрута

- [ ] Контроллер: `@GetMapping("/my-page")` — **без** `/spec`, как в существующих маппингах
- [ ] Шаблон: все ссылки `th:href="${basePath + '/my-page'}"` — через `${basePath}`, не `@{}`
- [ ] JS: все fetch и редиректы через `BASE_PATH + '/...'`
- [ ] SecurityConfig: публичная страница → добавить в `requestMatchers("/my-page").permitAll()`
- [ ] SecurityConfig: публичный API → добавить в `requestMatchers("/api/my-endpoint").permitAll()`
- [ ] Не трогать `server.servlet.context-path` в `application.properties`
