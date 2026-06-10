import re

path = r"C:/Users/Stepler/IdeaProjects/emias-dashboard/src/main/java/com/emias/dashboard/service/HcvRegistryService.java"

with open(path, encoding='utf-8') as f:
    content = f.read()

# Replace the whole buildSchemeDashboard method body
new_method = '''    public SchemeDashboardStats buildSchemeDashboard() {
        // Только пациенты с заполненным столбцом B (Регистр ВИМИС)
        List<HcvRegistry> all = repo.findAll().stream()
                .filter(r -> r.getVimisStatus() != null && !r.getVimisStatus().isBlank())
                .collect(java.util.stream.Collectors.toList());

        long totalAll = all.size();
        // "Заключение МОНИКИ / Центр СПИД" = "да" (столбец H)
        long withScheme = all.stream()
                .filter(r -> containsIgnoreCase(r.getMonikConclusion(), "да"))
                .count();
        TreeSet<String> schemesSet = new TreeSet<>();
        TreeSet<String> routesSet  = new TreeSet<>();

        // scheme -> { duration -> count }
        Map<String, Map<String, Long>> schemeDurationMap = new LinkedHashMap<>();
        // scheme -> { routedTo -> count }
        Map<String, Map<String, Long>> schemeRouteMap    = new LinkedHashMap<>();
        // Топ маршруты: МО -> суммарное кол-во
        Map<String, Long> topRoutes = new TreeMap<>();

        for (HcvRegistry r : all) {
            String scheme   = notBlankOr(r.getTreatmentScheme(),        "Не указана");
            String duration = notBlankOr(r.getTreatmentDurationWeeks(), "Не указана");
            String routed   = notBlankOr(r.getRoutedTo(),               "Не указана");

            if (!scheme.equals("Не указана")) schemesSet.add(scheme);
            if (!routed.equals("Не указана"))  routesSet.add(routed);

            schemeDurationMap.computeIfAbsent(scheme, k -> new LinkedHashMap<>())
                    .merge(duration, 1L, Long::sum);
            schemeRouteMap.computeIfAbsent(scheme, k -> new LinkedHashMap<>())
                    .merge(routed, 1L, Long::sum);
            topRoutes.merge(routed, 1L, Long::sum);
        }

        // Строим строки — одна строка на уникальную схему
        List<SchemeGroupRow> groups = new ArrayList<>();
        for (String scheme : schemeDurationMap.keySet()) {
            long count = schemeDurationMap.get(scheme).values().stream()
                    .mapToLong(Long::longValue).sum();

            Map<String, Long> durSorted = new LinkedHashMap<>();
            schemeDurationMap.get(scheme).entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> durSorted.put(e.getKey(), e.getValue()));

            Map<String, Long> routedSorted = new LinkedHashMap<>();
            schemeRouteMap.getOrDefault(scheme, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> routedSorted.put(e.getKey(), e.getValue()));

            groups.add(new SchemeGroupRow(scheme, count, durSorted, routedSorted));
        }

        // Сортировка: "Не указана" в конец, остальные по убыванию кол-ва
        groups.sort((a, b) -> {
            boolean aBlank = a.scheme.equals("Не указана");
            boolean bBlank = b.scheme.equals("Не указана");
            if (aBlank != bBlank) return aBlank ? 1 : -1;
            return Long.compare(b.count, a.count);
        });

        // Топ-маршруты: убираем "Не указана", сортируем по убыванию
        Map<String, Long> topRoutesSorted = new LinkedHashMap<>();
        topRoutes.entrySet().stream()
                .filter(e -> !e.getKey().equals("Не указана"))
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .forEach(e -> topRoutesSorted.put(e.getKey(), e.getValue()));

        return new SchemeDashboardStats(totalAll, withScheme,
                schemesSet.size(), routesSet.size(),
                groups, topRoutesSorted);
    }'''

# Replace everything from start of buildSchemeDashboard to closing }
pattern = re.compile(
    r'    public SchemeDashboardStats buildSchemeDashboard\(\).*?^    \}',
    re.DOTALL | re.MULTILINE
)

result, n = pattern.subn(new_method, content)
print(f"Replacements: {n}")

if n > 0:
    with open(path, 'w', encoding='utf-8') as f:
        f.write(result)
    print("Done")
else:
    print("Pattern not matched")
