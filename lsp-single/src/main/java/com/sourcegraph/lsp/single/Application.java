package com.sourcegraph.lsp.single;

import com.sourcegraph.lsp.single.service.Server;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * This Spring Boot application connects to remote [address]:port and handles incoming
 * LSP requests while communication channel is alive
 */
@SpringBootApplication(scanBasePackages = {
        "com.sourcegraph.common",
        "com.sourcegraph.lsp.common",
        "com.sourcegraph.lsp.single"})
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
        Server server = new SpringApplicationBuilder(com.sourcegraph.lsp.single.Application.class).
                web(false).
                run(args).
                getBean(Server.class);
        server.open();
    }
}
