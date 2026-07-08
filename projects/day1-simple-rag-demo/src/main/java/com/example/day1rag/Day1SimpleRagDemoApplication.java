package com.example.day1rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Day 1 RAG demo.
 *
 * This project is intentionally minimal today: it only boots a Spring web
 * server and exposes a health check. Over the next lessons we will add,
 * step by step, the pieces of a manual RAG (Retrieval-Augmented Generation)
 * pipeline into the empty packages already created for you:
 *
 *   - model    -> plain Java classes for a Document and a Chunk
 *   - service  -> chunking logic, embedding logic, and the RAG orchestration
 *   - vector   -> a simple in-memory vector store + top-k similarity search
 *   - config   -> Spring beans (e.g. the mock LLM client, fake embedder)
 *
 * Beginners: think of this class as the "power switch" for the app.
 * Running main() starts an embedded web server so the REST APIs below
 * become reachable, e.g. http://localhost:8080/api/health
 */
@SpringBootApplication
public class Day1SimpleRagDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(Day1SimpleRagDemoApplication.class, args);
    }
}
