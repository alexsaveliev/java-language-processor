package com.sourcegraph.langp.javac;

import com.sourcegraph.langp.configuration.TaskExecutorConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class WorkspaceService {

    @Autowired
    private TaskExecutorConfiguration taskExecutorConfiguration;

    private Map<Path, Workspace> workspaces = new HashMap<>();

    public synchronized Workspace getWorkspace(Path path) {
        Workspace ret = workspaces.get(path);
        if (ret == null) {
            ret = new Workspace(path, taskExecutorConfiguration.taskExecutor());
            workspaces.put(path, ret);
        }
        return ret;
    }

}