package com.emias.deployer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);
    private static final int MAX_SIZE = 20;

    private final List<DeployRecord> history = Collections.synchronizedList(new ArrayList<>());
    private final ObjectMapper mapper;

    @Value("${deploy.history-file}")
    private String historyFile;

    public HistoryService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void load() {
        File file = new File(historyFile);
        if (!file.exists()) return;
        try {
            List<DeployRecord> loaded = mapper.readValue(file, new TypeReference<>() {});
            history.addAll(loaded);
            log.info("Загружено {} записей истории из {}", loaded.size(), historyFile);
        } catch (Exception e) {
            log.warn("Не удалось загрузить историю из {}: {}", historyFile, e.getMessage());
        }
    }

    public void add(DeployRecord record) {
        history.add(record);
        while (history.size() > MAX_SIZE) history.remove(0);
        save();
    }

    public List<DeployRecord> getReversed() {
        List<DeployRecord> copy = new ArrayList<>(history);
        Collections.reverse(copy);
        return copy;
    }

    public boolean hasSuccessfulBackup() {
        return history.stream().anyMatch(r -> r.status == DeployRecord.Status.SUCCESS
                && r.type == DeployRecord.Type.DEPLOY);
    }

    private void save() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(historyFile), history);
        } catch (Exception e) {
            log.error("Не удалось сохранить историю: {}", e.getMessage());
        }
    }
}
