package com.example.day1rag.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A "health check" endpoint tells you (or a monitoring tool) that the
 * application started successfully and is able to respond to requests.
 * It doesn't do anything fancy — it's just a friendly "I'm alive!" ping.
 *
 * Try it: GET http://localhost:8080/api/health
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        // LinkedHashMap keeps insertion order, so the JSON output looks tidy:
        // { "status": "UP", "message": "Day 1 RAG demo is running" }
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("message", "Day 1 RAG demo is running");
        return response;
    }
}
