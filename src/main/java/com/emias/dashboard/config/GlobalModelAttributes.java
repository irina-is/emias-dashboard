package com.emias.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    @Value("${app.base-path:}")
    private String basePath;

    @ModelAttribute
    public void addBasePath(Model model) {
        model.addAttribute("basePath", basePath);
    }
}
