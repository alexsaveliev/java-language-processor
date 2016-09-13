package com.sourcegraph.common.javac;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Maintains workspaces
 */
@Service
public class WorkspaceService {

    /**
     * @param path workspace root
     * @return workspace with the specified root
     */
    @Cacheable("workspaces")
    public Workspace getWorkspace(Path path) {
        return new Workspace(path);
    }

    /**
     * Cleanups cache
     */
    @CacheEvict(value = "workspaces", allEntries = true)
    public void purge() {
    }

}