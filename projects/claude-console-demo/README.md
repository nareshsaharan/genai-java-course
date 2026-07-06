# claude-console-demo

A collection of minimal Java 21 console programs that call Claude using the
official [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) -
each one focused on a single teaching point.

## Requirements

- Java 21 or newer (`java -version`)
- Maven 3.8+ (`mvn -version`)
- An Anthropic API key from [console.anthropic.com](https://console.anthropic.com/)

## Setup

```bash
export ANTHROPIC_API_KEY=your-api-key-here
mvn compile
```

## Classes

| Class | Teaching point | Run it |
|---|---|---|
| `FirstClaudeCall` | The simplest possible request: one prompt, one response. | `mvn compile exec:java -Dexec.mainClass=com.example.claude.demo.FirstClaudeCall` |
| `Main` | An interactive chat loop that keeps conversation history in a `List<MessageParam>`. | `mvn compile exec:java` |
| `StatelessDemo` | The Messages API is stateless - a second, independent call has no memory of the first. | `mvn compile exec:java -Dexec.mainClass=com.example.claude.demo.StatelessDemo` |
| `ConversationHistoryDemo` | The opposite of `StatelessDemo`: resending the full history yourself is what makes a "conversation" work. | `mvn compile exec:java -Dexec.mainClass=com.example.claude.demo.ConversationHistoryDemo` |
| `StreamingDemo` | The streaming API prints text chunk by chunk as Claude generates it, instead of waiting for the full reply. | `mvn compile exec:java -Dexec.mainClass=com.example.claude.demo.StreamingDemo` |

`Main` is the default when no `-Dexec.mainClass` is given.

## Alternative: build a runnable jar

```bash
mvn package
java -jar target/claude-console-demo-0.0.1-SNAPSHOT-shaded.jar
```

(The packaged jar always runs `Main` - use `mvn compile exec:java` to run one
of the other classes.)

## Project layout

```
pom.xml
src/main/java/com/example/claude/demo/
  Main.java                     # Interactive chat loop
  FirstClaudeCall.java          # Simplest single request/response
  StatelessDemo.java            # Proves the API has no memory between calls
  ConversationHistoryDemo.java  # Shows how to fake memory by resending history
  StreamingDemo.java            # Streaming API, chunk-by-chunk output
```

## Notes

- Every class defaults to `Model.CLAUDE_HAIKU_4_5` - fast and inexpensive,
  good for repeated runs while learning. Change the `MODEL` constant in a
  given class if you want a different model.
- `Main`'s conversation history is kept in memory only - it resets every
  time you restart the program.
