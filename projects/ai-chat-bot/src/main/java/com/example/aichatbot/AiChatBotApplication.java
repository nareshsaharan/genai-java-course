package com.example.aichatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point.
 *
 * Run with:  mvn spring-boot:run
 * Then open: http://localhost:8080/chat?message=Hello
 */
@SpringBootApplication
public class AiChatBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatBotApplication.class, args);
    }
}
