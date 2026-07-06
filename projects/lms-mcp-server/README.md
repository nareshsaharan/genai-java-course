# LMS MCP Server

A Learning Management System MCP (Model Context Protocol) Server built with Java 21 and Spring Boot. This server is designed to run over STDIO and integrate with Claude Desktop.

## Project Structure

```
lms-mcp-server/
├── src/
│   ├── main/
│   │   ├── java/com/example/lmsmcp/
│   │   │   ├── LmsMcpServerApplication.java    # Main Spring Boot application class
│   │   │   ├── config/
│   │   │   │   └── McpServerConfiguration.java # MCP server configuration
│   │   │   ├── service/
│   │   │   │   └── CourseService.java          # Business logic for course management
│   │   │   ├── tool/
│   │   │   │   └── CourseMcpTool.java          # MCP tools exposed to Claude
│   │   │   ├── model/
│   │   │   │   └── Course.java                 # Data models
│   │   │   └── util/
│   │   │       └── JsonUtil.java               # Utility classes
│   │   └── resources/
│   │       └── application.properties          # Spring Boot configuration
│   └── test/
│       └── java/com/example/lmsmcp/           # Test classes
├── pom.xml                                     # Maven configuration
└── README.md                                   # This file
```

## Requirements

- **Java**: 21 or higher
- **Maven**: 3.6.0 or higher
- **Spring Boot**: 3.3.0

## Building the Project

```bash
# Build the project
mvn clean package

# Build without running tests
mvn clean package -DskipTests
```

## Running the Project

```bash
# Run the application
mvn spring-boot:run

# Or run the packaged JAR
java -jar target/lms-mcp-server-1.0.0.jar
```

## STDIO Communication

The server communicates via Standard Input/Output (STDIO) for integration with Claude Desktop. This allows the MCP server to:
- Receive requests on stdin
- Send responses on stdout
- Work seamlessly with Claude Desktop's MCP client

## In-Memory Data Storage

The application uses in-memory storage (Map-based collections) for all data:
- No database required
- Data persists during the application lifecycle
- Sample course data is pre-loaded on startup
- All logs are written to files, not console

## Key Features

- ✅ Java 21 with latest Spring Boot 3.3.0
- ✅ MCP Server support via Spring AI
- ✅ STDIO transport for Claude Desktop integration
- ✅ In-memory data storage (no database)
- ✅ Structured logging (using SLF4J, no System.out.println)
- ✅ Course management functionality
- ✅ MCP tools for Claude integration

## Package Organization

### com.example.lmsmcp
Main application package

### com.example.lmsmcp.config
Configuration classes for Spring Boot and MCP server setup

### com.example.lmsmcp.service
Business logic and service classes
- `CourseService`: Manages course CRUD operations with in-memory storage

### com.example.lmsmcp.tool
MCP tool implementations exposed to Claude
- `CourseMcpTool`: Tools for course operations

### com.example.lmsmcp.model
Data model classes
- `Course`: Course entity

### com.example.lmsmcp.util
Utility classes
- `JsonUtil`: JSON serialization/deserialization helper

## Configuration

The application is configured via `src/main/resources/application.properties`:

- **Logging**: Configured to write to both console and files (in `logs/` directory)
- **Spring Boot**: Disabled unnecessary auto-configurations for a lightweight MCP server
- **MCP Server**: Basic server metadata (name, version)

## Development Notes

1. **No System.out.println**: All output is done through SLF4J logging
2. **In-Memory Storage**: Data is stored in LinkedHashMap for simple ordering
3. **Lombok**: Used to reduce boilerplate code (@Data, @Slf4j, etc.)
4. **Jackson**: Used for JSON processing
5. **Spring AI**: Provides MCP server framework and STDIO transport

## Next Steps

1. Implement MCP tool handlers in `tool/` package
2. Add more service classes for additional LMS functionality
3. Implement MCP endpoint mappings
4. Add comprehensive tests
5. Configure Claude Desktop integration file

## License

MIT License
