package com.cinema.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupInfoLogger {

    private static final String SAMPLE_PASSWORD = "123456";

    private final Environment environment;
    private final PasswordEncoder passwordEncoder;

    public StartupInfoLogger(Environment environment, PasswordEncoder passwordEncoder) {
        this.environment = environment;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logLocalUrls() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String base = "http://localhost:" + port + contextPath;
        String samplePasswordHash = passwordEncoder.encode(SAMPLE_PASSWORD);

        log.info("""

                ----------------------------------------------------------
                  {} is running!
                  Local:   {}
                  Health:  {}/actuator/health
                  WS info: {}/ws/cinema/info
                  BCrypt("{}"): {}
                ----------------------------------------------------------""",
                environment.getProperty("spring.application.name", "Application"),
                base, base, base, SAMPLE_PASSWORD, samplePasswordHash);
    }
}
