package com.sourcegraph.langp.service;

import com.sourcegraph.langp.config.builder.DefaultBuilder;
import com.sourcegraph.langp.config.builder.GradleBuilder;
import com.sourcegraph.langp.config.builder.MavenBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);

    public void configure(File workspace) {
        LOGGER.info("Configuring {}", workspace);
        try {
            if (!MavenBuilder.prepare(workspace.toPath()) &&
                    !GradleBuilder.prepare(workspace.toPath())) {
                DefaultBuilder.prepare(workspace.toPath());
            }
        } catch (Exception ex) {
            LOGGER.warn("Unable to configure {}", workspace, ex);
        }

    }
}
