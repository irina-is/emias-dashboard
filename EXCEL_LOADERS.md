# Паттерн загрузчиков Excel в проекте EMIAS Dashboard

Этот документ описывает единый подход к созданию загрузчиков Excel-файлов.
Следуй этим принципам при добавлении новых загрузчиков или изменении существующих.

---

## Принципы

1. **FormulaEvaluator обязателен.** Ячейки в xlsx могут содержать формулы (например, `=G3-F3`). Без `FormulaEvaluator` Apache POI вернёт пустую строку — данные сохранятся как `null`.

2. **Фильтр строк "Итого".** Пользователи добавляют строки "Итого" для удобства в Excel. Такие строки не должны загружаться в БД. Проверяй: `orgName.toLowerCase().contains("итого")`.

3. **Предпросмотр без загрузки.** При открытии admin.html данные из БД подгружаются автоматически — не нужно загружать файл, чтобы увидеть что уже есть. `loadMyPeriods(null)` вызывается в `DOMContentLoaded`.

4. **Шаблон генерируется из структуры загруженного файла.** AI-ассистент при создании нового загрузчика должен изучить реальный xlsx-файл пользователя и сгенерировать `generateTemplate()` точно под его структуру — не делай generic-шаблон.

5. **Ошибки в модальном окне.** При ошибке загрузки (сервер вернул ошибку, нет соединения) вызывается `showErrors([...])` — показывает Bootstrap-модал с подробностями. Уже реализован в admin.html.

6. **Загрузка идемпотентна.** Перед сохранением старые данные за тот же период удаляются: `repo.deleteByPeriod(period)`. Повторная загрузка того же файла не создаёт дублей.

7. **Номера секций в admin совпадают с фронтом.** Секция получает тот же badge-номер, что и соответствующий раздел на `hepatitis.html`. Если прямого соответствия нет — badge `→NN` (питает раздел NN) или `—` (unnumbered на фронте). Порядок секций в admin повторяет порядок на фронте.

---

## Порядок секций в admin.html (соответствует hepatitis.html)

| Badge  | Раздел в admin                     | Раздел на фронте                 |
|--------|------------------------------------|----------------------------------|
| **01** | КВЦ                                | 01 Критически важные цели        |
| **02** | Ключевые показатели 2026           | 02 Ключевые показатели 2026      |
| **—**  | Риски проекта                      | (без номера, между 02 и 03)      |
| **03** | Загрузка недельного плана          | 03 Рейтинг исполнения            |
| **03** | Понедельный план отработки МО      | 03 (продолжение)                 |
| →04,06 | Загрузка свода ВГС                 | питает 04 Путь пациента, 06 МО   |
| →01,02 | Загрузка 4ДИ                       | питает 01 КВЦ, 02 КПИ            |
| **05** | План мероприятий                   | 05 План мероприятий              |
| **06** | Поручения МО                       | 06 Поручения МО                  |

---

## Шаблон: Java-сервис

```java
@Transactional
public int upload(MultipartFile file, LocalDate period) throws Exception {
    try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
        Sheet sheet = wb.getSheetAt(0);
        DataFormatter fmt = new DataFormatter();
        // ОБЯЗАТЕЛЬНО: без evaluator формулы вернут пустую строку
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

        repo.deleteByPeriod(period); // идемпотентность

        List<MyEntity> rows = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String name = fmt.formatCellValue(row.getCell(0), evaluator).trim();

            // Пропускаем пустые строки и строки "Итого" (пользователи добавляют их в Excel)
            if (name.isEmpty() || name.equalsIgnoreCase("итого")) continue;

            Integer value = parseIntOrNull(fmt, evaluator, row.getCell(1));
            rows.add(new MyEntity(name, value, period));
        }
        repo.saveAll(rows);
        return rows.size();
    }
}

// Всегда используй эту сигнатуру с evaluator
private Integer parseIntOrNull(DataFormatter fmt, FormulaEvaluator evaluator, Cell cell) {
    if (cell == null) return null;
    String s = fmt.formatCellValue(cell, evaluator).trim()
                  .replace(" ", "") // неразрывный пробел
                  .replace(" ", "")
                  .replace(",", ".");
    if (s.isEmpty() || s.equals("—") || s.equals("-")) return null;
    try { return (int) Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
}
```

**Почему FormulaEvaluator?**  
Пользователи часто считают динамику или проценты формулой в Excel (`=G3-F3`).
POI без evaluator возвращает `""` для таких ячеек → данные сохраняются как `null` → столбец на фронте показывает `—`.

**Почему фильтр "Итого"?**  
Пользователь добавляет строку "Итого" в свой Excel-файл для удобства просмотра.
Эта строка не является МО и не должна загружаться в таблицу.

---

## Шаблон: REST-контроллер

```java
// Загрузка
@PostMapping("/api/my-entity/upload")
public ResponseEntity<?> upload(@RequestParam MultipartFile file,
                                 @RequestParam String period) {
    try {
        int count = myService.upload(file, LocalDate.parse(period));
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Загружено " + count + " строк",
            "count", count,
            "period", period
        ));
    } catch (Exception e) {
        return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
    }
}

// Предпросмотр
@GetMapping("/api/my-entity")
public ResponseEntity<?> getData(@RequestParam String period) {
    return ResponseEntity.ok(myService.getByPeriod(LocalDate.parse(period))
        .stream().map(r -> Map.of("name", r.getName(), "value", r.getValue())).toList());
}

// Список доступных периодов (для select в предпросмотре)
@GetMapping("/api/my-entity/periods")
public ResponseEntity<?> getPeriods() {
    return ResponseEntity.ok(myService.getAvailablePeriods());
}

// Скачать шаблон (генерируется из структуры реального файла пользователя)
@GetMapping("/api/my-entity/template")
public ResponseEntity<byte[]> getTemplate() throws Exception {
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template.xlsx\"")
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(myService.generateTemplate());
}
```

---

## Шаблон: HTML (admin.html)

```html
<div class="card shadow-sm mb-4">
    <div class="card-body p-4">
        <h5 class="mb-1">
            <span class="badge bg-primary me-2" style="font-size:.7rem;vertical-align:middle;">NN</span>
            Название загрузчика
        </h5>
        <p class="text-muted mb-3" style="font-size:0.85rem;">
            Описание формата. Строки «Итого» пропускаются автоматически.
        </p>

        <div class="d-flex align-items-end gap-3 flex-wrap mb-3">
            <div>
                <label class="form-label mb-1" style="font-size:0.8rem;color:var(--emias-muted);">Период</label>
                <input type="month" id="myPeriodInput" class="form-control" style="max-width:200px;">
            </div>
            <input type="file" id="myFile" accept=".xlsx" class="form-control" style="max-width:280px;align-self:flex-end;">
            <div style="align-self:flex-end;display:flex;gap:8px;">
                <button class="btn btn-primary" onclick="uploadMyData()">
                    <i class="bi bi-upload me-1"></i>Загрузить
                </button>
                <a class="btn btn-outline-secondary" th:href="${basePath + '/api/my-entity/template'}">
                    <i class="bi bi-download me-1"></i>Скачать шаблон
                </a>
            </div>
            <span id="myStatus" class="text-muted" style="font-size:0.85rem;"></span>
        </div>

        <!-- Предпросмотр — всегда виден если есть данные в БД -->
        <div id="myPreview" style="display:none;">
            <hr class="my-3">
            <div class="d-flex align-items-center gap-3 mb-3 flex-wrap">
                <span class="fw-semibold" style="font-size:0.88rem;">Данные за период:</span>
                <select id="myPeriodSelect" class="form-select form-select-sm"
                        style="max-width:200px;" onchange="loadMyPreview()"></select>
                <span id="myCount" class="text-muted ms-auto" style="font-size:0.82rem;"></span>
            </div>
            <div style="max-height:420px;overflow-y:auto;border-radius:8px;border:1px solid var(--emias-border,#E8ECF5);">
                <table class="table table-sm table-hover mb-0" style="font-size:0.8rem;">
                    <thead style="position:sticky;top:0;z-index:1;" class="table-light">
                        <tr>
                            <th style="width:36px;">№</th>
                            <th>Медицинская организация</th>
                            <th class="text-end">Значение</th>
                        </tr>
                    </thead>
                    <tbody id="myTableBody"></tbody>
                </table>
            </div>
        </div>
    </div>
</div>
```

---

## Шаблон: JavaScript

```javascript
function uploadMyData() {
    const fileInput = document.getElementById('myFile');
    const periodInput = document.getElementById('myPeriodInput');
    const status = document.getElementById('myStatus');

    if (!fileInput.files.length) { status.textContent = 'Выберите файл'; return; }
    if (!periodInput.value) { status.textContent = 'Выберите период'; return; }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('period', periodInput.value + '-01');

    status.innerHTML = '<span class="text-primary">Загрузка...</span>';
    fetch(BASE_PATH + '/api/my-entity/upload', { method: 'POST', body: formData })
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                status.innerHTML = '<span class="text-success"><i class="bi bi-check-circle me-1"></i>'
                    + safeEsc(data.message) + '</span>';
                fileInput.value = '';
                loadMyPeriods(data.period);
            } else {
                status.innerHTML = '<span class="text-danger"><i class="bi bi-x-circle me-1"></i>'
                    + safeEsc(data.message) + '</span>';
                showErrors([data.message || 'Ошибка загрузки файла']); // модальное окно
            }
        })
        .catch(() => {
            status.innerHTML = '<span class="text-danger">Ошибка соединения</span>';
            showErrors(['Ошибка соединения с сервером. Проверьте сеть и попробуйте снова.']);
        });
}

function loadMyPeriods(selectPeriod) {
    fetch(BASE_PATH + '/api/my-entity/periods')
        .then(r => r.json())
        .then(periods => {
            if (!Array.isArray(periods) || periods.length === 0) return;
            const sel = document.getElementById('myPeriodSelect');
            sel.innerHTML = periods.map(p =>
                `<option value="${p}" ${p === selectPeriod ? 'selected' : ''}>${p}</option>`
            ).join('');
            document.getElementById('myPreview').style.display = 'block';
            loadMyPreview();
        })
        .catch(() => {});
}

function loadMyPreview() {
    const sel = document.getElementById('myPeriodSelect');
    if (!sel?.value) return;
    fetch(BASE_PATH + '/api/my-entity?period=' + sel.value)
        .then(r => r.json())
        .then(rows => {
            document.getElementById('myCount').textContent = rows.length + ' организаций';
            document.getElementById('myTableBody').innerHTML = rows.map((r, i) => `
                <tr>
                    <td class="text-muted">${i + 1}</td>
                    <td>${safeEsc(r.name)}</td>
                    <td class="text-end">${r.value ?? '—'}</td>
                </tr>
            `).join('');
        })
        .catch(() => {});
}

// ОБЯЗАТЕЛЬНО: загружаем существующие данные при открытии страницы
document.addEventListener('DOMContentLoaded', () => loadMyPeriods(null));
```

---

## Чеклист при создании нового загрузчика

- [ ] `FormulaEvaluator` создан из `wb.getCreationHelper().createFormulaEvaluator()`
- [ ] Все `fmt.formatCellValue(cell)` → `fmt.formatCellValue(cell, evaluator)`
- [ ] Фильтр пустых строк: `if (name.isEmpty() || name.equalsIgnoreCase("итого")) continue;`
- [ ] `generateTemplate()` создан на основе реального файла пользователя (не generic)
- [ ] Предпросмотр загружается в `DOMContentLoaded` (не только после загрузки файла)
- [ ] Кнопка «Скачать шаблон» с `Content-Disposition: attachment`
- [ ] При ошибке вызывается `showErrors([...])` (модальное окно Bootstrap)
- [ ] Загрузка идемпотентна: `repo.deleteByPeriod(period)` перед `saveAll`
- [ ] Badge в admin.html соответствует номеру раздела на фронте
- [ ] Секция стоит в том же порядке, что и соответствующий раздел на `hepatitis.html`
