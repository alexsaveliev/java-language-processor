package com.sourcegraph.langp.service;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
        return getWorkspace(repo, commit, false);
    }

    public File getWorkspace(String repo, String commit, boolean force) throws WorkspaceException {
        File workspace = new File(new File(root, repo), commit);
        boolean cloneNeeded = true;
        if (workspace.isDirectory()) {
            if (force) {
                cloneNeeded = false;
            } else {
                return workspace;
            }
        }
        if (workspace.isFile()) {
            throw new WorkspaceException(workspace + " does not denote a directory");
        }
        if (!workspace.getParentFile().exists() && !workspace.getParentFile().mkdirs()) {
            throw new WorkspaceException("Unable to create parent directory " + workspace.getParent());
        }
        if (cloneNeeded) {
            LOGGER.info("Cloning {} into {}", repo, workspace);
            if (!clone("https://" + repo, workspace)) {
                throw new WorkspaceException("Unable to clone " + repo);
            }
        }

        if (!reset(workspace, commit)) {
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

    private boolean clone(String uri, File destination) {
        CloneCommand clone = Git.cloneRepository();
        try {
            clone.setURI(uri).setDirectory(destination);
            clone.call().getRepository().close();
            return true;
        } catch (Exception e) {
            LOGGER.error("An error occurred while cloning {} into {}", uri, destination, e);
            return false;
        }
    }

    private boolean reset(File repoDir, String commit) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(repoDir, ".git")).
                readEnvironment(). // scan environment GIT_* variables
                findGitDir(). // scan up the file system tree
                build()) {
            Git git = new Git(repository);
            ResetCommand reset = git.reset();
            reset.setRef(commit).setMode(ResetCommand.ResetType.HARD).call();
            return true;
        } catch (Exception e) {
            LOGGER.error("An error occurred while resetting {} to {}", repoDir, commit, e);
            return false;
        }
    }

}
