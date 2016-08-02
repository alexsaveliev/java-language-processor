package com.sourcegraph.langp.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sourcegraph.langp.config.builder.DefaultBuilder;
import com.sourcegraph.langp.config.builder.GradleBuilder;
import com.sourcegraph.langp.config.builder.MavenBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Service
public class ConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);

    @Value("${configuration.threads:10}")
    private int numThreads;

    private Set<File> beingConfigured = ConcurrentHashMap.newKeySet();

    private ExecutorService executorService;

    @PostConstruct
    private void init() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("configurator-%d")
                .setDaemon(true)
                .build();
        this.executorService = Executors.newFixedThreadPool(numThreads,
                threadFactory);
    }

    public boolean isBeingConfigured(File workspace) {
        return beingConfigured.contains(workspace);
    }

    public void schedule(File workspace) {
        beingConfigured.add(workspace);
        this.executorService.submit(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Configuring {}", workspace);
                try {
                    if (!MavenBuilder.prepare(workspace.toPath()) &&
                            !GradleBuilder.prepare(workspace.toPath())) {
                        DefaultBuilder.prepare(workspace.toPath());
                    }
                } catch (Exception ex) {
                    LOGGER.warn("Unable to configure {}", workspace, ex);
                } finally {
                    beingConfigured.remove(workspace);
                }
            }
        });
    }
}
