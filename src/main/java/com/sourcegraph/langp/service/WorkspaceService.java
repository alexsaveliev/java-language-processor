package com.sourcegraph.langp.service;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;

@Service
public class WorkspaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceService.class);

    @Autowired
    private ConfigurationService configurationService;

    @Value("${workspace:${SGPATH:${user.home}/.sourcegraph}/workspace/java}")
    private String workspace;

    private File root;

    public File getWorkspace(String repo, String commit) throws WorkspaceException {
        File workspace = new File(new File(root, repo), commit);
        if (workspace.isDirectory()) {
            return workspace;
        }
        if (workspace.exists()) {
            throw new WorkspaceException(workspace + " does not denote a directory");
        }
        if (!workspace.getParentFile().mkdirs()) {
            throw new WorkspaceException("Unable to create parent directory " + workspace.getParent());
        }
        LOGGER.info("Cloning {} into {}", repo, workspace);
        if (!exec(workspace.getParentFile(), "git", "clone", "https://" + repo, commit)) {
            throw new WorkspaceException("Unable to clone " + repo);
        }
        if (!exec(workspace, "git", "reset", "--hard", commit)) {
            throw new WorkspaceException("Unable to fetch commit " + commit);
        }

        configurationService.configure(workspace);
        return workspace;
    }

    @PostConstruct
    private void init() {
        this.root = new File(workspace);
        if (!root.exists()) {
            if (!root.mkdirs()) {
                throw new RuntimeException("Unable to create workpace directory " + workspace);
            }
        } else if (!root.isDirectory()) {
            throw new RuntimeException(workspace + " does not denote a directory");
        }
        LOGGER.info("Using workspace {}", root.getAbsolutePath());
    }

    private boolean exec(File cwd, String... cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd, null, cwd);
            return p.waitFor() == 0;
        } catch (Exception ex) {
            LOGGER.error("An error occurred while running [{}] in {}", StringUtils.join(cmd, ' '), cwd, ex);
            return false;
        }
    }


}
