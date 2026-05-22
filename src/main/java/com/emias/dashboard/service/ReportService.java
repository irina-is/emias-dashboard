package com.emias.dashboard.service;

import com.emias.dashboard.entity.Screening;
import com.emias.dashboard.entity.Upload;
import com.emias.dashboard.model.PatientRecord;
import com.emias.dashboard.repository.ScreeningRepository;
import com.emias.dashboard.repository.UploadRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Отвечает за загрузку файлов и чтение данных из базы.
 *
 * Когда пользователь загружает Excel:
 *   1. Файл сохраняется на диск как архив
 *   2. Все строки парсятся за ОДИН проход (валидация + парсинг вместе)
 *   3. Записи сохраняются в БД порциями по BATCH_SIZE
 *   4. В таблицу uploads добавляется запись о загрузке
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

    private final ScreeningRepository screeningRepository;
    private final UploadRepository    uploadRepository;

    public ReportService(ScreeningRepository screeningRepository, UploadRepository uploadRepository) {
        this.screeningRepository = screeningRepository;
        this.uploadRepository    = uploadRepository;
    }

    /**
     * Сохраняет файл и загружает данные в базу.
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

        // Шаг 1: сохраняем файл на диск как архив
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path destination = dir.resolve("report_" + date + ".xlsx");
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
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
        uploadRepository.save(new Upload(reportDate, records.size()));
        log.info("=== Загрузка завершена успешно: {} записей за {} ===", records.size(), reportDate);
    }

    /**
     * Возвращает список дат всех загруженных файлов, от новой к старой.
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
     * Читает из базы все записи за указанную дату.
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
                                    row[7], row[8], row[9], row[10], row[11], row[12], row[13],
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
        return s;
    }

}
