package com.emias.dashboard.service;

import java.util.List;

public class FileValidationException extends RuntimeException {

    private final List<String> errors;

    public FileValidationException(List<String> errors) {
        super("Файл содержит ошибки валидации");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
