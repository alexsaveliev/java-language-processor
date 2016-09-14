package com.sourcegraph.lsp;

import com.sourcegraph.lsp.service.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * This Spring Boot application listens for incoming connections at specified [address]:port and handles incoming
 * LSP requests
 */
@SpringBootApplication(scanBasePackages = {
        "com.sourcegraph.common",
        "com.sourcegraph.lsp"})
@EnableCaching
public class Application {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter crlf = new CommonsRequestLoggingFilter();
        crlf.setIncludeHeaders(true);
        crlf.setIncludePayload(true);
        crlf.setMaxPayloadLength(256);
        return crlf;
    }

    public static void main(String[] args) throws Exception {
        Server server = new SpringApplicationBuilder(Application.class).
                web(false).
                run(args).
                getBean(Server.class);
        server.open();
    }
}
