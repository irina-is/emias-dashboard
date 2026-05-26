package com.emias.dashboard.service;

import com.emias.dashboard.entity.Screening;
import com.emias.dashboard.entity.Upload;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.repository.ScreeningRepository;
import com.emias.dashboard.repository.UploadRepository;
<<<<<<< HEAD
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
=======
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
>>>>>>> dev
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
<<<<<<< HEAD

import java.io.FileInputStream;
import java.io.IOException;
=======
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
>>>>>>> dev
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
<<<<<<< HEAD
=======
import java.util.Arrays;
>>>>>>> dev
import java.util.List;

/**
 * Отвечает за загрузку файлов и чтение данных из базы.
 *
 * Когда пользователь загружает Excel:
 *   1. Файл сохраняется на диск как архив
<<<<<<< HEAD
 *   2. Все строки парсятся и записываются в таблицу screenings
 *   3. В таблицу uploads добавляется запись о загрузке
 *
 * При повторной загрузке за ту же дату — старые данные удаляются и заменяются новыми.
=======
 *   2. Все строки парсятся за ОДИН проход (валидация + парсинг вместе)
 *   3. Записи сохраняются в БД порциями по BATCH_SIZE
 *   4. В таблицу uploads добавляется запись о загрузке
>>>>>>> dev
 */
@Service
public class ReportService {

<<<<<<< HEAD
    @Value("${report.upload.dir}")
    private String uploadDir;

=======
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final int[]    DATE_COL_INDICES = {7, 9, 27, 28, 29};
    private static final String[] DATE_COL_NAMES   = {
        "Дата проведения мероприятия", "Дата закрытия карты",
        "Дата забора биоматериала", "Дата доставки",
        "Дата проведения исследования"
    };

    private static final int MAX_ERRORS = 20;
    /** Размер порции для батч-сохранения в БД */
    private static final int BATCH_SIZE = 500;

    // Прогресс текущей загрузки (читается из другого потока — volatile)
    private volatile boolean uploadInProgress = false;
    private volatile int     progressCurrent  = 0;
    private volatile int     progressTotal    = 0;

    public boolean isUploadInProgress() { return uploadInProgress; }
    public int getProgressCurrent()     { return progressCurrent; }
    public int getProgressTotal()       { return progressTotal; }

    @Value("${report.upload.dir}")
    private String uploadDir;

    @PersistenceContext
    private EntityManager entityManager;

>>>>>>> dev
    private final ScreeningRepository screeningRepository;
    private final UploadRepository    uploadRepository;

    public ReportService(ScreeningRepository screeningRepository, UploadRepository uploadRepository) {
        this.screeningRepository = screeningRepository;
        this.uploadRepository    = uploadRepository;
    }

    /**
     * Сохраняет файл и загружает данные в базу.
<<<<<<< HEAD
     * Если за эту дату уже был файл — данные перезаписываются.
     *
     * @param file загруженный файл
     * @param date дата в формате "2026-05-16"
     */
    @Transactional
    public void saveUploadedFile(MultipartFile file, String date) throws IOException {
=======
     * Файл читается ОДИН РАЗ (валидация + парсинг за один проход).
     * Данные сохраняются в БД порциями, чтобы не перегружать память.
     */
    @Transactional
    public void saveUploadedFile(MultipartFile file, String date) throws IOException {
        uploadInProgress = true;
        progressCurrent  = 0;
        progressTotal    = 0;
        try {
            doSaveUploadedFile(file, date);
        } finally {
            uploadInProgress = false;
        }
    }

    private void doSaveUploadedFile(MultipartFile file, String date) throws IOException {
        String originalName = file.getOriginalFilename();
        long fileSize = file.getSize();
        log.info("=== Начало загрузки файла '{}', дата отчёта: {}, размер: {} байт ===",
                originalName, date, fileSize);

>>>>>>> dev
        // Шаг 1: сохраняем файл на диск как архив
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path destination = dir.resolve("report_" + date + ".xlsx");
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
<<<<<<< HEAD

        // Шаг 2: парсим Excel
        List<PatientRecord> records = parseFile(destination.toString());

        // Шаг 3: удаляем старые данные за эту дату (если были)
        LocalDate reportDate = LocalDate.parse(date);
        screeningRepository.deleteByReportDate(reportDate);
        uploadRepository.deleteByReportDate(reportDate);

        // Шаг 4: сохраняем все строки в базу
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

        // Шаг 5: сохраняем запись о загрузке
        uploadRepository.save(new Upload(reportDate, records.size()));
=======
        log.info("Файл сохранён на диск: {}", destination.toAbsolutePath());

        // Шаг 2: валидация + парсинг за один проход
        // (бросает FileValidationException если найдены ошибки)
        List<PatientRecord> records = validateAndParseFile(destination.toString());
        progressTotal = records.size(); // теперь клиент знает сколько всего

        // Шаг 3: удаляем старые данные за эту дату (если были)
        LocalDate reportDate = LocalDate.parse(date);
        long existingCount = screeningRepository.countByReportDate(reportDate);
        if (existingCount > 0) {
            log.info("Удаляю {} старых записей за {} (перезагрузка)", existingCount, reportDate);
        }
        screeningRepository.deleteByReportDate(reportDate);
        uploadRepository.deleteByReportDate(reportDate);

        // Шаг 4: сохраняем порциями по BATCH_SIZE, flush+clear между порциями
        List<Screening> batch = new ArrayList<>(BATCH_SIZE);
        int savedTotal = 0;
        for (PatientRecord record : records) {
            batch.add(toScreening(record, reportDate));
            if (batch.size() == BATCH_SIZE) {
                screeningRepository.saveAll(batch);
                entityManager.flush();
                entityManager.clear();
                savedTotal += batch.size();
                progressCurrent = savedTotal; // обновляем прогресс для клиента
                log.info("Сохранено {}/{} записей...", savedTotal, records.size());
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            screeningRepository.saveAll(batch);
            entityManager.flush();
            entityManager.clear();
            savedTotal += batch.size();
            progressCurrent = savedTotal;
        }
        log.info("Итого сохранено {} записей в базу за {}", savedTotal, reportDate);

        // Шаг 5: сохраняем запись о загрузке
        uploadRepository.save(new Upload(reportDate, records.size(), originalName));
        log.info("=== Загрузка завершена успешно: {} записей за {} ===", records.size(), reportDate);
    }

    /**
     * Возвращает все записи о загрузках, от новой к старой.
     */
    public List<Upload> getUploads() {
        return uploadRepository.findAllByOrderByReportDateDesc();
    }

    /**
     * Удаляет все данные скрининга за указанную дату и файл с диска.
     */
    @Transactional
    public void deleteByDate(String date) throws IOException {
        LocalDate reportDate = LocalDate.parse(date);
        screeningRepository.deleteByReportDate(reportDate);
        uploadRepository.deleteByReportDate(reportDate);

        Path file = Paths.get(uploadDir).resolve("report_" + date + ".xlsx");
        Files.deleteIfExists(file);

        log.info("Данные и файл за {} удалены", reportDate);
>>>>>>> dev
    }

    /**
     * Возвращает список дат всех загруженных файлов, от новой к старой.
<<<<<<< HEAD
     * Например: ["2026-05-16", "2026-05-09"]
=======
>>>>>>> dev
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
<<<<<<< HEAD
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
=======
     * Читает из базы все записи за все месяцы.
     */
    public List<PatientRecord> readAllRecords() {
        List<Screening> screenings = screeningRepository.findAll();
        List<PatientRecord> records = new ArrayList<>();
        for (Screening s : screenings) {
            records.add(toPatientRecord(s));
        }
        return records;
    }

    /**
     * Читает из базы все записи за указанную дату.
     */
    public List<PatientRecord> readRecords(String date) {
        LocalDate reportDate = LocalDate.parse(date);
        List<PatientRecord> records = new ArrayList<>();
        for (Screening s : screeningRepository.findByReportDate(reportDate)) {
            records.add(toPatientRecord(s));
        }
        return records;
    }

    /**
     * Читает все записи за текущий календарный месяц (с 1-го числа по selectedDate включительно).
     */
    public List<PatientRecord> readRecordsForMonth(String date) {
        LocalDate selectedDate = LocalDate.parse(date);
        LocalDate monthStart = selectedDate.withDayOfMonth(1);
        List<PatientRecord> records = new ArrayList<>();
        for (Screening s : screeningRepository.findByReportDateBetween(monthStart, selectedDate)) {
            records.add(toPatientRecord(s));
>>>>>>> dev
        }
        return records;
    }

    /**
     * Читает самый свежий файл.
     */
    public List<PatientRecord> readLatestRecords() {
        List<String> dates = getUploadedDates();
<<<<<<< HEAD
        if (dates.isEmpty()) {
            return new ArrayList<>();
        }
        return readRecords(dates.get(0));
    }

    // Читает Excel-файл по пути и возвращает список записей
    private List<PatientRecord> parseFile(String filePath) throws IOException {
        List<PatientRecord> records = new ArrayList<>();

        FileInputStream fis = new FileInputStream(filePath);
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);

        // Строка 0 — заголовок, начинаем с 1
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);

            if (row == null) {
                continue;
            }

            PatientRecord record = new PatientRecord(
                    getCellValue(row, 0),   // Номер МКАБ
                    getCellValue(row, 1),   // Фамилия
                    getCellValue(row, 2),   // Имя
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
        }

        workbook.close();
        fis.close();

        return records;
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
=======
        if (dates.isEmpty()) return new ArrayList<>();
        return readRecords(dates.get(0));
    }

    // ─── Приватные методы ────────────────────────────────────────────────────

    /**
     * Читает Excel потоковым SAX-парсером — одновременно проверяет ошибки и парсит записи.
     * В отличие от XSSFWorkbook, держит в памяти только текущую строку (не весь файл).
     * Бросает FileValidationException если найдены ошибки формата.
     */
    private List<PatientRecord> validateAndParseFile(String filePath) throws IOException {
        List<String> errors = new ArrayList<>();
        List<PatientRecord> records = new ArrayList<>();
        log.info("Открываю Excel-файл (потоковое чтение): {}", filePath);

        try (OPCPackage pkg = OPCPackage.open(new java.io.File(filePath))) {
            XSSFReader xssfReader    = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles       = xssfReader.getStylesTable();
            // Переопределяем DataFormatter: числовые ячейки с датовым форматом
            // всегда возвращаем в виде dd.MM.yyyy, независимо от формата в Excel
            DataFormatter formatter = new DataFormatter() {
                @Override
                public String formatRawCellContents(double value, int formatIndex, String formatString) {
                    if (DateUtil.isADateFormat(formatIndex, formatString)) {
                        return DateUtil.getLocalDateTime(value, false).toLocalDate().format(DATE_FMT);
                    }
                    return super.formatRawCellContents(value, formatIndex, formatString);
                }
            };

            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (!iter.hasNext()) throw new IOException("Excel-файл не содержит листов");

            try (InputStream sheetStream = iter.next()) {
                SheetContentsHandler rowHandler = new SheetContentsHandler() {
                    private final String[] row = new String[30];

                    @Override
                    public void startRow(int rowNum) {
                        Arrays.fill(row, "");
                    }

                    @Override
                    public void endRow(int rowNum) {
                        if (rowNum == 0) return; // заголовок
                        String mkab = row[0], lastName = row[1], firstName = row[2];
                        if (mkab.isBlank() && lastName.isBlank() && firstName.isBlank()) return;

                        if (errors.size() < MAX_ERRORS) {
                            if (mkab.isBlank()) {
                                errors.add("Строка " + (rowNum + 1) + ": не заполнен МКАБ");
                            }
                            for (int d = 0; d < DATE_COL_INDICES.length && errors.size() < MAX_ERRORS; d++) {
                                String val = row[DATE_COL_INDICES[d]];
                                if (!val.isBlank()) {
                                    try { LocalDate.parse(val, DATE_FMT); }
                                    catch (Exception e) {
                                        errors.add("Строка " + (rowNum + 1) + ", \"" + DATE_COL_NAMES[d]
                                                + "\": неверный формат даты \"" + val + "\"");
                                    }
                                }
                            }
                            if (errors.size() >= MAX_ERRORS) {
                                errors.add("Показаны первые " + MAX_ERRORS
                                        + " ошибок. Исправьте их и загрузите файл повторно.");
                            }
                        }

                        try {
                            records.add(new PatientRecord(
                                    row[0], row[1], row[2], row[3], row[4], row[5], row[6],
                                    "", row[7], row[9], row[10], row[11], row[12], row[13],
                                    row[14], row[15], row[16], row[17], row[18], row[19], row[20],
                                    row[21], row[22], row[23], row[24], row[25], row[26], row[27],
                                    row[28], row[29]
                            ));
                        } catch (Exception e) {
                            log.warn("Строка {}: ошибка парсинга, пропускаю: {}", rowNum + 1, e.getMessage());
                        }
                    }

                    @Override
                    public void cell(String cellRef, String formattedValue, XSSFComment comment) {
                        if (cellRef == null || formattedValue == null) return;
                        int col = CellReference.convertColStringToIndex(cellRef.replaceAll("[0-9]", ""));
                        if (col >= 0 && col < row.length) row[col] = formattedValue.trim();
                    }
                };

                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XMLReader xmlReader = factory.newSAXParser().getXMLReader();
                xmlReader.setContentHandler(
                        new XSSFSheetXMLHandler(styles, null, strings, rowHandler, formatter, false));
                xmlReader.parse(new InputSource(sheetStream));
            }
        } catch (org.xml.sax.SAXException | javax.xml.parsers.ParserConfigurationException |
                 org.apache.poi.openxml4j.exceptions.OpenXML4JException e) {
            throw new IOException("Ошибка чтения Excel: " + e.getMessage(), e);
        }

        log.info("Парсинг завершён, прочитано записей: {}", records.size());

        if (!errors.isEmpty()) {
            log.error("Файл не прошёл валидацию: {} ошибок", errors.size());
            errors.forEach(e -> log.error("  {}", e));
            throw new FileValidationException(errors);
        }
        return records;
    }

    /** Конвертирует Screening entity → PatientRecord */
    private PatientRecord toPatientRecord(Screening s) {
        return new PatientRecord(
                s.getMkabNumber(), s.getLastName(), s.getFirstName(), s.getMiddleName(),
                s.getVisitType(), s.getSnils(), s.getOmsPolicy(),
                s.getDispensarizationDate(), s.getResearchDate(), s.getCardClosingDate(),
                s.getBirthDate(), s.getTfomsServiceCode(), s.getValueText(),
                s.getReferralNumber(), s.getRefusal(), s.getResearchResult(),
                s.getServiceCode(), s.getResearchStatus(), s.getDoctorName(),
                s.getOgrnFrom(), s.getFacilityFrom(), s.getOgrnTo(), s.getFacilityTo(),
                s.getPcrResult(), s.getPcrDone(), s.getAgeAtExport(), s.getAgeAtResearch(),
                s.getBiomaterialDate(), s.getDeliveryDate(), s.getResearchConductedDate()
        );
    }

    /** Конвертирует PatientRecord → Screening entity */
    private Screening toScreening(PatientRecord record, LocalDate reportDate) {
        Screening s = new Screening();
        s.setReportDate(reportDate);
        s.setMkabNumber(record.getMkabNumber());
        s.setLastName(record.getLastName());
        s.setFirstName(record.getFirstName());
        s.setMiddleName(record.getMiddleName());
        s.setVisitType(record.getVisitType());
        s.setSnils(record.getSnils());
        s.setOmsPolicy(record.getOmsPolicy());
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
        return s;
    }

>>>>>>> dev
}
