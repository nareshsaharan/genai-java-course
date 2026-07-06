package com.example.claudemvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot application.
 *
 * <p>Layering used in this project:
 * <ul>
 *   <li>{@code controller} - handles HTTP requests/responses only</li>
 *   <li>{@code service}    - contains the business logic (talking to Claude)</li>
 *   <li>{@code dto}        - plain request/response objects exchanged with clients</li>
 *   <li>{@code config}     - Spring configuration (e.g. building the Anthropic client)</li>
 * </ul>
 */
@SpringBootApplication
public class ClaudeMvcDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaudeMvcDemoApplication.class, args);
    }
}
