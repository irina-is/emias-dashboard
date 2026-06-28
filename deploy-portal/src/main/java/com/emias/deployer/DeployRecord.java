package com.emias.deployer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployRecord {

    public enum Status { SUCCESS, FAILED }
    public enum Type   { DEPLOY, ROLLBACK }

    public String id;
    public LocalDateTime time;
    public String filename;
    public long sizeBytes;
    public Status status;
    public Type type;
    public String errorMessage;
    public long durationSeconds;
    public List<String> logs;

    public DeployRecord() {}

    public DeployRecord(LocalDateTime time, String filename, long sizeBytes,
                        Status status, Type type, String errorMessage,
                        long durationSeconds, List<String> logs) {
        this.id              = UUID.randomUUID().toString();
        this.time            = time;
        this.filename        = filename;
        this.sizeBytes       = sizeBytes;
        this.status          = status;
        this.type            = type;
        this.errorMessage    = errorMessage;
        this.durationSeconds = durationSeconds;
        this.logs            = logs;
    }

    public String getSizeMb() {
        if (sizeBytes == 0) return "—";
        return String.format("%.1f МБ", sizeBytes / 1024.0 / 1024.0);
    }
}
