# GenAI with Java — Course Repository

Teaching materials, source code, docs, and slides for the **GenAI with Java** course.

## Structure

```
genai-java-course/
├── projects/
│   └── ai-chat-bot/   ← Spring Boot 3 + OpenAI Responses API streaming chat
├── docs/              ← Reference docs, guides, API notes
└── slides/            ← Presentation decks (PPT / PDF)
```

## Projects

| Project | Description |
|---------|-------------|
| [ai-chat-bot](projects/ai-chat-bot/) | Streaming chat using OpenAI Responses API, Spring WebFlux, mock mode, CostGuard, local cache |

## Quick Start — ai-chat-bot

```bash
cd projects/ai-chat-bot

# Mock mode (no API key needed)
mvn spring-boot:run

# Test streaming
curl -N -G "http://localhost:8080/chat" --data-urlencode "message=what is kafka"
```
