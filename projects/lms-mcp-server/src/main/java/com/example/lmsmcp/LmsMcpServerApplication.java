package com.example.lmsmcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LmsMcpServerApplication {

    private static final Logger log = LoggerFactory.getLogger(LmsMcpServerApplication.class);

    public static void main(String[] args) {
        // Logging before SpringApplication.run() would use logback's
        // un-configured bootstrap (which writes to stdout) instead of
        // logback-spring.xml, corrupting the STDIO JSON-RPC stream.
        SpringApplication.run(LmsMcpServerApplication.class, args);
        log.info("LMS MCP Server application started successfully");
    }
}
