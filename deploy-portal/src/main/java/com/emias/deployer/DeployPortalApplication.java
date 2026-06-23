package com.emias.deployer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class DeployPortalApplication {

    @Value("${server.port:8082}")
    private int port;

    public static void main(String[] args) {
        SpringApplication.run(DeployPortalApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        System.out.println("Деплой-портал запущен: http://localhost:" + port);
    }
}
