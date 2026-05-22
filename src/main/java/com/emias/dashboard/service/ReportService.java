package com.emias.dashboard.service;

import com.emias.dashboard.entity.Screening;
import com.emias.dashboard.entity.Upload;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.repository.ScreeningRepository;
import com.emias.dashboard.repository.UploadRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Отвечает за загрузку файлов и чтение данных из базы.
 *
 * Когда пользователь загружает Excel:
 *   1. Файл сохраняется на диск как архив
 *   2. Все строки парсятся и записываются в таблицу screenings
 *   3. В таблицу uploads добавляется запись о загрузке
 *
 * При повторной загрузке за ту же дату — старые данные удаляются и заменяются новыми.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final int[]    DATE_COL_INDICES = {7, 8, 9, 10, 27, 28, 29};
    private static final String[] DATE_COL_NAMES   = {
        "Дата диспансеризации", "Дата исследования", "Дата закрытия карты",
        "Дата рождения", "Дата забора биоматериала", "Дата доставки",
        "Дата проведения исследования"
    };

    @Value("${report.upload.dir}")
    private String uploadDir;

    private final ScreeningRepository screeningRepository;
    private final UploadRepository    uploadRepository;

    public ReportService(ScreeningRepository screeningRepository, UploadRepository uploadRepository) {
        this.screeningRepository = screeningRepository;
        this.uploadRepository    = uploadRepository;
    }

    /**
     * Сохраняет файл и загружает данные в базу.
     * Если за эту дату уже был файл — данные перезаписываются.
     *
     * @param file загруженный файл
     * @param date дата в формате "2026-05-16"
     */
    @Transactional
    public void saveUploadedFile(MultipartFile file, String date) throws IOException {
        String originalName = file.getOriginalFilename();
        long fileSize = file.getSize();
        log.info("=== Начало загрузки файла '{}', дата отчёта: {}, размер: {} байт ===",
                originalName, date, fileSize);

        // Шаг 1: сохраняем файл на диск как архив
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path destination = dir.resolve("report_" + date + ".xlsx");
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Файл сохранён на диск: {}", destination.toAbsolutePath());

        // Шаг 2: валидация перед парсингом
        List<String> errors = validateFile(destination.toString());
        if (!errors.isEmpty()) {
            log.error("Файл не прошёл валидацию: {} ошибок", errors.size());
            errors.forEach(e -> log.error("  {}", e));
            throw new FileValidationException(errors);
        }

        // Шаг 3: парсим Excel
        List<PatientRecord> records = parseFile(destination.toString());
        log.info("Парсинг завершён, успешно прочитано записей: {}", records.size());

        // Шаг 4: удаляем старые данные за эту дату (если были)
        LocalDate reportDate = LocalDate.parse(date);
        long existingCount = screeningRepository.countByReportDate(reportDate);
        if (existingCount > 0) {
            log.info("Удаляю {} старых записей за {} (перезагрузка)", existingCount, reportDate);
        }
        screeningRepository.deleteByReportDate(reportDate);
        uploadRepository.deleteByReportDate(reportDate);

        // Шаг 5: сохраняем все строки в базу
        List<Screening> screenings = new ArrayList<>();
        for (PatientRecord record : records) {
            Screening s = new Screening();
            s.setReportDate(reportDate);
            s.setMkabNumber(record.getMkabNumber());
            s.setLastName(record.getLastName());
            s.setFirstName(record.getFirstName());
            s.setMiddleName(record.getMiddleName());
            s.setVisitType(record.getVisitType());
            s.setSnils(record.getSnils());
            s.setOmsPolicy(record.getOmsPolicy());
            s.setDispensarizationDate(record.getDispensarizationDate());
            s.setResearchDate(record.getResearchDate());
            s.setCardClosingDate(record.getCardClosingDate());
            s.setBirthDate(record.getBirthDate());
            s.setTfomsServiceCode(record.getTfomsServiceCode());
            s.setValueText(record.getValueText());
            s.setReferralNumber(record.getReferralNumber());
            s.setRefusal(record.getRefusal());
            s.setResearchResult(record.getResearchResult());
            s.setServiceCode(record.getServiceCode());
            s.setResearchStatus(record.getResearchStatus());
            s.setDoctorName(record.getDoctorName());
            s.setOgrnFrom(record.getOgrnFrom());
            s.setFacilityFrom(record.getFacilityFrom());
            s.setOgrnTo(record.getOgrnTo());
            s.setFacilityTo(record.getFacilityTo());
            s.setPcrResult(record.getPcrResult());
            s.setPcrDone(record.getPcrDone());
            s.setAgeAtExport(record.getAgeAtExport());
            s.setAgeAtResearch(record.getAgeAtResearch());
            s.setBiomaterialDate(record.getBiomaterialDate());
            s.setDeliveryDate(record.getDeliveryDate());
            s.setResearchConductedDate(record.getResearchConductedDate());
            screenings.add(s);
        }
        screeningRepository.saveAll(screenings);
        log.info("Сохранено {} записей в базу за {}", screenings.size(), reportDate);

        // Шаг 6: сохраняем запись о загрузке
        uploadRepository.save(new Upload(reportDate, records.size()));
        log.info("=== Загрузка завершена успешно: {} записей за {} ===", records.size(), reportDate);
    }

    /**
     * Возвращает список дат всех загруженных файлов, от новой к старой.
     * Например: ["2026-05-16", "2026-05-09"]
     */
    public List<String> getUploadedDates() {
        List<Upload> uploads = uploadRepository.findAllByOrderByReportDateDesc();
        List<String> dates = new ArrayList<>();
        for (Upload upload : uploads) {
            dates.add(upload.getReportDate().toString());
        }
        return dates;
    }

    /**
     * Читает из базы все записи за указанную дату и возвращает как список PatientRecord.
     *
     * @param date дата в формате "2026-05-16"
     */
    public List<PatientRecord> readRecords(String date) {
        LocalDate reportDate = LocalDate.parse(date);
        List<Screening> screenings = screeningRepository.findByReportDate(reportDate);

        List<PatientRecord> records = new ArrayList<>();
        for (Screening s : screenings) {
            records.add(new PatientRecord(
                    s.getMkabNumber(), s.getLastName(), s.getFirstName(), s.getMiddleName(),
                    s.getVisitType(), s.getSnils(), s.getOmsPolicy(),
                    s.getDispensarizationDate(), s.getResearchDate(), s.getCardClosingDate(),
                    s.getBirthDate(), s.getTfomsServiceCode(), s.getValueText(),
                    s.getReferralNumber(), s.getRefusal(), s.getResearchResult(),
                    s.getServiceCode(), s.getResearchStatus(), s.getDoctorName(),
                    s.getOgrnFrom(), s.getFacilityFrom(), s.getOgrnTo(), s.getFacilityTo(),
                    s.getPcrResult(), s.getPcrDone(), s.getAgeAtExport(), s.getAgeAtResearch(),
                    s.getBiomaterialDate(), s.getDeliveryDate(), s.getResearchConductedDate()
            ));
        }
        return records;
    }

    /**
     * Читает самый свежий файл.
     */
    public List<PatientRecord> readLatestRecords() {
        List<String> dates = getUploadedDates();
        if (dates.isEmpty()) {
            return new ArrayList<>();
        }
        return readRecords(dates.get(0));
    }

    private static final int MAX_ERRORS = 20;

    // Проверяет файл на ошибки. Возвращает список ошибок (пустой — если всё ок)
    private List<String> validateFile(String filePath) throws IOException {
        List<String> errors = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if (errors.size() >= MAX_ERRORS) {
                    errors.add("Показаны первые " + MAX_ERRORS + " ошибок. Исправьте их и загрузите файл повторно.");
                    break;
                }

                Row row = sheet.getRow(i);
                if (row == null) continue;

                String mkab      = getCellValueSafe(row, 0);
                String lastName  = getCellValueSafe(row, 1);
                String firstName = getCellValueSafe(row, 2);

                // Пустая строка — тихо пропускаем
                if (mkab.isBlank() && lastName.isBlank() && firstName.isBlank()) continue;

                // Есть данные, но нет МКАБ
                if (mkab.isBlank()) {
                    errors.add("Строка " + (i + 1) + ": не заполнен МКАБ");
                }

                // Проверяем форматы дат
                for (int d = 0; d < DATE_COL_INDICES.length; d++) {
                    if (errors.size() >= MAX_ERRORS) break;
                    String value = getCellValueSafe(row, DATE_COL_INDICES[d]);
                    if (!value.isBlank()) {
                        try {
                            LocalDate.parse(value, DATE_FMT);
                        } catch (Exception e) {
                            errors.add("Строка " + (i + 1) + ", \"" + DATE_COL_NAMES[d]
                                    + "\": неверный формат даты \"" + value + "\"");
                        }
                    }
                }
            }
        }

        return errors;
    }

    // Читает Excel-файл по пути и возвращает список записей
    private List<PatientRecord> parseFile(String filePath) throws IOException {
        List<PatientRecord> records = new ArrayList<>();
        int skippedRows = 0;

        log.info("Открываю Excel-файл: {}", filePath);
        FileInputStream fis = new FileInputStream(filePath);
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        int totalRows = sheet.getLastRowNum();
        log.info("Листов в книге: {}, используем лист 0. Строк данных (без заголовка): {}",
                workbook.getNumberOfSheets(), totalRows);

        // Строка 0 — заголовок, начинаем с 1
        for (int i = 1; i <= totalRows; i++) {
            Row row = sheet.getRow(i);

            if (row == null) {
                log.debug("Строка {} пустая (null), пропускаю", i);
                skippedRows++;
                continue;
            }

            try {
                String mkab     = getCellValue(row, 0);
                String lastName = getCellValue(row, 1);
                String firstName = getCellValue(row, 2);

                if (mkab.isBlank() && lastName.isBlank()) {
                    log.debug("Строка {} пустая (нет МКАБ и фамилии), пропускаю", i);
                    skippedRows++;
                    continue;
                }

                log.debug("Строка {}: МКАБ={}, {} {} {}",
                        i, mkab, lastName, firstName, getCellValue(row, 3));

                PatientRecord record = new PatientRecord(
                        mkab,
                        lastName,
                        firstName,
                        getCellValue(row, 3),   // Отчество
                        getCellValue(row, 4),   // Диспансеризация / профосмотр
                        getCellValue(row, 5),   // СНИЛС
                        getCellValue(row, 6),   // Полис ОМС
                        getCellValue(row, 7),   // Дата диспансеризации
                        getCellValue(row, 8),   // Дата исследования
                        getCellValue(row, 9),   // Дата закрытия дисп. карты
                        getCellValue(row, 10),  // Дата рождения
                        getCellValue(row, 11),  // Код услуги ТФОМС
                        getCellValue(row, 12),  // Значение (Текст)
                        getCellValue(row, 13),  // Номер направления
                        getCellValue(row, 14),  // Отказ
                        getCellValue(row, 15),  // Результат исследования
                        getCellValue(row, 16),  // Код услуги
                        getCellValue(row, 17),  // Статус исследования
                        getCellValue(row, 18),  // Врач
                        getCellValue(row, 19),  // ОГРН откуда направили
                        getCellValue(row, 20),  // ЛПУ откуда направили
                        getCellValue(row, 21),  // ОГРН куда направили
                        getCellValue(row, 22),  // ЛПУ куда направили
                        getCellValue(row, 23),  // Результат ПЦР
                        getCellValue(row, 24),  // Проводился ли ПЦР
                        getCellValue(row, 25),  // Возраст на момент выгрузки
                        getCellValue(row, 26),  // Возраст на момент исследования
                        getCellValue(row, 27),  // Дата забора биоматериала
                        getCellValue(row, 28),  // Дата доставки
                        getCellValue(row, 29)   // Дата проведения исследования
                );

                records.add(record);

            } catch (Exception e) {
                log.error("Ошибка парсинга строки {} (МКАБ='{}'): {}",
                        i, getCellValueSafe(row, 0), e.getMessage(), e);
                skippedRows++;
            }
        }

        workbook.close();
        fis.close();

        if (skippedRows > 0) {
            log.warn("Пропущено строк при парсинге: {}", skippedRows);
        }

        return records;
    }

    private String getCellValueSafe(Row row, int colIndex) {
        try { return getCellValue(row, colIndex); } catch (Exception e) { return "?"; }
    }

    // Читает значение ячейки и возвращает его как строку
    private String getCellValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);

        if (cell == null) {
            return "";
        }

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue()
                        .toLocalDate()
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
            // long вместо int — чтобы не обрезались большие номера МКАБ
            return String.valueOf((long) cell.getNumericCellValue());
        }

        if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }

        if (cell.getCellType() == CellType.FORMULA) {
            if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                return String.valueOf((int) cell.getNumericCellValue());
            }
            if (cell.getCachedFormulaResultType() == CellType.STRING) {
                return cell.getStringCellValue().trim();
            }
        }

        return "";
    }
}
