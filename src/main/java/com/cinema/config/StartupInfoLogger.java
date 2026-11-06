package com.cinema.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupInfoLogger {

    private final Environment env;

    public StartupInfoLogger(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logLocalUrls() {
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String base = "http://localhost:" + port + contextPath;

        log.info("""

                ----------------------------------------------------------
                  {} is running!
                  Local:   {}
                  Health:  {}/actuator/health
                  WS info: {}/ws/info
                ----------------------------------------------------------""",
                env.getProperty("spring.application.name", "Application"), base, base, base);
    }
}
