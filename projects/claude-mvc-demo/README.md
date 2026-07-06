# claude-mvc-demo

A Spring Boot web app that exposes Claude over HTTP, structured with a
classic MVC layering: **Controller → Service → DTO**.

## Layers

```
controller/  ChatController      - handles HTTP only (routes, request/response)
service/     ClaudeService(Impl) - business logic: talks to Claude via the Anthropic SDK
dto/         ChatRequest/Response - plain data objects exchanged with clients
config/      AnthropicConfig     - builds the shared AnthropicClient bean
```

The controller never touches the Anthropic SDK directly — it only knows
about `ClaudeService`. The service never touches HTTP — it only knows how
to turn a `String` question into a `String` answer.

## Requirements

- Java 21 or newer
- Maven 3.8+
- An Anthropic API key from [console.anthropic.com](https://console.anthropic.com/)

## Setup

```bash
export ANTHROPIC_API_KEY=your-api-key-here
```

## Run

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

## Try it

```bash
curl -s http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Explain Java inheritance in simple words with one code example."}'
```

Response:

```json
{"reply": "..."}
```

Sending a blank message returns `400 Bad Request` (validated by `@NotBlank`
on `ChatRequest`).

### Statelessness demo

`POST /api/demo/stateless` makes two **independent** calls to Claude using
the prompts you send — CALL 1 uses `call1Message`, then CALL 2 uses
`call2Message` as a brand new request that never includes CALL 1. This
shows that Claude has no memory of CALL 1 when answering CALL 2.

```bash
curl -s http://localhost:8080/api/demo/stateless \
  -H "Content-Type: application/json" \
  -d '{
    "call1Message": "My favorite color is blue.",
    "call2Message": "What is my favorite color?"
  }' | jq
```

Response:

```json
{
  "call1Reply": "Got it, blue is your favorite color!",
  "call2WithoutHistoryReply": "I don't have any information about your favorite color...",
  "explanation": "Claude could not reliably answer CALL 2 because every API request is independent - there is no server-side memory. To keep context across turns, the caller must resend prior messages itself (see ChatController + a client-maintained history, or Main.java in claude-console-demo)."
}
```

### Conversation history demo

`POST /api/demo/conversation-history` shows the opposite side of the
statelessness demo above: our own service resends the full conversation on
the second call, so Claude *can* use context from the first message.

```bash
curl -s http://localhost:8080/api/demo/conversation-history \
  -H "Content-Type: application/json" \
  -d '{
    "firstMessage": "My name is Ankur.",
    "secondMessage": "What is my name?"
  }' | jq
```

Response:

```json
{
  "firstReply": "Nice to meet you, Ankur!",
  "secondReply": "Your name is Ankur.",
  "explanation": "Claude did not remember anything by itself. Our Spring service kept a List<MessageParam> and resent every earlier message - including Claude's own first reply - on the second request. That resending is the entire mechanism behind a Claude-powered chat conversation (see ConversationHistoryDemo.java in claude-console-demo for the same idea as a plain Java program)."
}
```

### Streaming demo

`GET /api/demo/streaming` shows Claude's STREAMING API over plain HTTP: the
response is sent back in small chunks as Claude generates them (via chunked
transfer encoding), instead of one big JSON blob at the end. Use
`curl --no-buffer` so curl prints each chunk as it arrives rather than
waiting for the whole response:

```bash
curl --no-buffer http://localhost:8080/api/demo/streaming
```

You should see the explanation of Spring Boot REST controllers appear
gradually, word by word, instead of all at once.

## Notes

- The model is set in `ClaudeServiceImpl` via the `MODEL` constant
  (`Model.CLAUDE_HAIKU_4_5`). Change it there if you want a different model.
- Each request is stateless — there is no conversation history between calls.
