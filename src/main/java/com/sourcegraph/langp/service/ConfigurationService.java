package com.sourcegraph.langp.service;

import com.sourcegraph.langp.config.builder.DefaultBuilder;
import com.sourcegraph.langp.config.builder.GradleBuilder;
import com.sourcegraph.langp.config.builder.MavenBuilder;
import com.sourcegraph.langp.configuration.TaskExecutorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Service
public class ConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);

    @Autowired
    private TaskExecutorConfiguration taskExecutorConfiguration;

    private Map<File, Future<File>> jobs = new ConcurrentHashMap<>();

    @Async
    public Future<File> configure(File workspace) {
        Future<File> current = jobs.get(workspace);
        if (current != null) {
            return current;
        }
        Future<File> ret = taskExecutorConfiguration.taskExecutor().submit(() -> {
            LOGGER.info("Configuring {}", workspace);
            try {
                if (!MavenBuilder.prepare(workspace.toPath()) &&
                        !GradleBuilder.prepare(workspace.toPath())) {
                    DefaultBuilder.prepare(workspace.toPath());
                }
            } catch (Exception ex) {
                LOGGER.warn("Unable to configure {}", workspace, ex);
            }
            LOGGER.info("Configured {}", workspace);
            return workspace;
        });
        jobs.put(workspace, ret);
        return ret;
    }

    public void purge() {
        jobs.clear();
    }
}
