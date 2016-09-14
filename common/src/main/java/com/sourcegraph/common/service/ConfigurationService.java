package com.sourcegraph.common.service;

import com.sourcegraph.common.config.builder.DefaultBuilder;
import com.sourcegraph.common.config.builder.GradleBuilder;
import com.sourcegraph.common.config.builder.MavenBuilder;
import com.sourcegraph.common.configuration.TaskExecutorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Service
public class ConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);

    /**
     * File that indicates completion of configuration process
     */
    private static final String STAMP_FILE = ".sourcegraph";

    @Autowired
    private TaskExecutorConfiguration taskExecutorConfiguration;

    private Map<File, Future<File>> jobs = new ConcurrentHashMap<>();

    @Async
    public Future<File> configure(File workspace) {
        return configure(workspace, false);
    }

    @Async
    public Future<File> configure(File workspace, boolean update) {
        Future<File> current = update ? null : jobs.get(workspace);
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
            new FileOutputStream(new File(workspace, STAMP_FILE)).close();
            LOGGER.info("Configured {}", workspace);
            return workspace;
        });
        jobs.put(workspace, ret);
        return ret;
    }

    /**
     * @param workspace workspace root
     * @return true if workspace is already configured
     */
    public boolean isConfigured(File workspace) {
        return new File(workspace, STAMP_FILE).exists();
    }

    public void purge() throws Exception {
        // park all
        for (Future<?> future : jobs.values()) {
            future.get();
        }
        jobs.clear();
    }
}
