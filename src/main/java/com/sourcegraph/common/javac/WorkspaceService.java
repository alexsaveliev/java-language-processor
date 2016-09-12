package com.sourcegraph.common.javac;

import com.sourcegraph.common.configuration.TaskExecutorConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Maintains workspaces
 */
@Service
public class WorkspaceService {

    @Autowired
    private TaskExecutorConfiguration taskExecutorConfiguration;

    /**
     * @param path workspace root
     * @return workspace with the specified root
     */
    @Cacheable("workspaces")
    public Workspace getWorkspace(Path path) {
        return new Workspace(path, taskExecutorConfiguration.taskExecutor());
    }

    /**
     * Cleanups cache
     */
    @CacheEvict(value = "workspaces", allEntries = true)
    public void purge() {
    }

}