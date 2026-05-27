package com.emias.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class DashboardApplication {

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(DashboardApplication.class, args);
        Environment env = ctx.getEnvironment();
        String port = env.getProperty("server.port", "8081");
        String basePath = env.getProperty("app.base-path", "");
        System.out.println("Дашборд запущен: http://localhost:" + port + basePath + "/login");
    }
}
