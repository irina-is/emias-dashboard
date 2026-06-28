package com.emias.deployer;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class DeployController {

    private static final Logger log = LoggerFactory.getLogger(DeployController.class);

    private final HistoryService historyService;
    private final BruteForceService bruteForce;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${deploy.password}")
    private String deployPasswordHash;

    @Value("${deploy.jar-path}")
    private String jarPath;

    @Value("${deploy.service-name}")
    private String serviceName;

    public DeployController(HistoryService historyService,
                            BruteForceService bruteForce,
                            BCryptPasswordEncoder passwordEncoder) {
        this.historyService  = historyService;
        this.bruteForce      = bruteForce;
        this.passwordEncoder = passwordEncoder;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].strip() : req.getRemoteAddr();
    }

    private boolean checkPassword(String password, String ip, RedirectAttributes ra) {
        if (bruteForce.isBlocked(ip)) {
            ra.addFlashAttribute("blockedError", bruteForce.blockedUntilText(ip));
            return false;
        }
        if (!passwordEncoder.matches(password, deployPasswordHash)) {
            int remaining = bruteForce.registerFailure(ip);
            if (remaining == 0) {
                ra.addFlashAttribute("blockedError", bruteForce.blockedUntilText(ip));
            } else {
                ra.addFlashAttribute("accessError", remaining);
            }
            return false;
        }
        bruteForce.registerSuccess(ip);
        return true;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("history", historyService.getReversed());
        model.addAttribute("hasBackup", backupPath().toFile().exists() && historyService.hasSuccessfulBackup());
        model.addAttribute("serviceName", serviceName);
        model.addAttribute("jarPath", jarPath);
        model.addAttribute("appLogs", readAppLogs());
        model.addAttribute("nginxConfig", readNginxConfig());
        return "index";
    }

    @PostMapping("/restart")
    public String restart(@RequestParam("password") String password,
                          RedirectAttributes redirectAttrs,
                          HttpServletRequest request) {
        if (!checkPassword(password, clientIp(request), redirectAttrs)) return "redirect:/";
        List<String> logs = new ArrayList<>();
        try {
            runCommand(logs, "sudo", "systemctl", "restart", serviceName);
            redirectAttrs.addFlashAttribute("restartSuccess", true);
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("deployError", "Перезапуск не удался: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/api/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status() {
        try {
            Process p = new ProcessBuilder("systemctl", "is-active", serviceName)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).strip();
            p.waitFor();
            return ResponseEntity.ok(Map.of("status", out, "active", "active".equals(out)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "unknown", "active", false));
        }
    }

    @PostMapping("/deploy")
    public String deploy(@RequestParam("jar") MultipartFile file,
                         @RequestParam("password") String password,
                         RedirectAttributes redirectAttrs,
                         HttpServletRequest request) {

        if (!checkPassword(password, clientIp(request), redirectAttrs)) return "redirect:/";
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".jar")) {
            redirectAttrs.addFlashAttribute("formError", "Нужно загрузить .jar файл");
            return "redirect:/";
        }

        long start = System.currentTimeMillis();
        LocalDateTime deployTime = LocalDateTime.now();
        String filename = file.getOriginalFilename();
        long size = file.getSize();
        List<String> logs = new ArrayList<>();

        try {
            Path target = Paths.get(jarPath);
            Path tmp    = target.resolveSibling(target.getFileName() + ".new");

            logs.add("Копирование JAR → " + tmp);
            Files.copy(file.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
            logs.add("Файл скопирован ✓");

            logs.add("Остановка сервиса " + serviceName + "...");
            runCommand(logs, "sudo", "systemctl", "stop", serviceName);

            logs.add("Замена JAR...");
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            logs.add("JAR заменён ✓");

            logs.add("Запуск сервиса " + serviceName + "...");
            runCommand(logs, "sudo", "systemctl", "start", serviceName);
            logs.add("Готово ✓");

            // Сохраняем бэкап успешного JAR
            logs.add("Сохранение бэкапа...");
            Files.copy(target, backupPath(), StandardCopyOption.REPLACE_EXISTING);
            logs.add("Бэкап сохранён ✓");

            long duration = (System.currentTimeMillis() - start) / 1000;
            historyService.add(new DeployRecord(deployTime, filename, size,
                    DeployRecord.Status.SUCCESS, DeployRecord.Type.DEPLOY, null, duration, logs));

            redirectAttrs.addFlashAttribute("deploySuccess", true);

        } catch (Exception e) {
            log.error("Deploy failed", e);
            logs.add("ОШИБКА: " + e.getMessage());
            long duration = (System.currentTimeMillis() - start) / 1000;
            historyService.add(new DeployRecord(deployTime, filename, size,
                    DeployRecord.Status.FAILED, DeployRecord.Type.DEPLOY, e.getMessage(), duration, logs));

            redirectAttrs.addFlashAttribute("deployError", e.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/rollback")
    public String rollback(@RequestParam("password") String password,
                           RedirectAttributes redirectAttrs,
                           HttpServletRequest request) {

        if (!checkPassword(password, clientIp(request), redirectAttrs)) return "redirect:/";

        Path backup = backupPath();
        if (!backup.toFile().exists()) {
            redirectAttrs.addFlashAttribute("formError", "Бэкап не найден");
            return "redirect:/";
        }

        long start = System.currentTimeMillis();
        LocalDateTime rollbackTime = LocalDateTime.now();
        List<String> logs = new ArrayList<>();

        try {
            Path target = Paths.get(jarPath);
            long backupSize = Files.size(backup);

            logs.add("Остановка сервиса " + serviceName + "...");
            runCommand(logs, "sudo", "systemctl", "stop", serviceName);

            logs.add("Восстановление бэкапа → " + target);
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            logs.add("JAR восстановлен ✓");

            logs.add("Запуск сервиса " + serviceName + "...");
            runCommand(logs, "sudo", "systemctl", "start", serviceName);
            logs.add("Готово ✓");

            long duration = (System.currentTimeMillis() - start) / 1000;
            historyService.add(new DeployRecord(rollbackTime, "↩ откат на предыдущую версию",
                    backupSize, DeployRecord.Status.SUCCESS, DeployRecord.Type.ROLLBACK,
                    null, duration, logs));

            redirectAttrs.addFlashAttribute("rollbackSuccess", true);

        } catch (Exception e) {
            log.error("Rollback failed", e);
            logs.add("ОШИБКА: " + e.getMessage());
            long duration = (System.currentTimeMillis() - start) / 1000;
            historyService.add(new DeployRecord(rollbackTime, "↩ откат (ошибка)",
                    0, DeployRecord.Status.FAILED, DeployRecord.Type.ROLLBACK,
                    e.getMessage(), duration, logs));

            redirectAttrs.addFlashAttribute("deployError", e.getMessage());
        }

        return "redirect:/";
    }

    private Path backupPath() {
        Path target = Paths.get(jarPath);
        return target.resolveSibling(target.getFileName() + ".backup");
    }

    private List<Map<String, String>> readNginxConfig() {
        List<Map<String, String>> result = new ArrayList<>();
        List<String> candidates = List.of(
                "/etc/nginx/nginx.conf",
                "/etc/nginx/sites-enabled",
                "/etc/nginx/conf.d"
        );
        for (String path : candidates) {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) continue;
            if (f.isFile()) {
                result.add(Map.of("path", path, "content", readFile(f)));
            } else if (f.isDirectory()) {
                java.io.File[] files = f.listFiles();
                if (files != null) {
                    java.util.Arrays.sort(files);
                    for (java.io.File child : files) {
                        if (child.isFile())
                            result.add(Map.of("path", child.getAbsolutePath(), "content", readFile(child)));
                    }
                }
            }
        }
        if (result.isEmpty())
            result.add(Map.of("path", "", "content", "Файлы конфигурации nginx не найдены"));
        return result;
    }

    private String readFile(java.io.File f) {
        try {
            return java.nio.file.Files.readString(f.toPath());
        } catch (Exception e) {
            return "Не удалось прочитать файл: " + e.getMessage();
        }
    }

    private List<String> readAppLogs() {
        try {
            Process p = new ProcessBuilder(
                    "journalctl", "-u", serviceName, "-n", "300", "--no-pager", "--output=short")
                    .redirectErrorStream(true).start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
            }
            p.waitFor();
            return lines;
        } catch (Exception e) {
            return List.of("Не удалось прочитать логи: " + e.getMessage());
        }
    }

    private void runCommand(List<String> logs, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) logs.add("  " + line);
        }
        int exit = p.waitFor();
        if (exit != 0)
            throw new RuntimeException("Команда " + String.join(" ", cmd) + " завершилась с кодом " + exit);
    }
}
