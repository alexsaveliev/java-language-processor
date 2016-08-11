package com.sourcegraph.langp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@SpringBootApplication
public class Application {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter crlf = new CommonsRequestLoggingFilter();
        crlf.setIncludeHeaders(true);
        crlf.setIncludePayload(true);
        crlf.setMaxPayloadLength(256);
        return crlf;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
