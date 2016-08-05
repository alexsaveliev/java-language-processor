package com.sourcegraph.langp.service;

import com.sourcegraph.langp.configuration.TaskExecutorConfiguration;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * This service manages repositories (clones, configures them)
 */
@Service
public class RepositoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryService.class);

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private TaskExecutorConfiguration taskExecutorConfiguration;

    /**
     * Root directory where all repositories are located
     */
    @Value("${workspace:${SGPATH:${user.home}/.sourcegraph}/workspace/java}")
    private String workspace;

    /**
     * Root directory where all repositories are located
     */
    private File root;

    /**
     * Tracks all completed and pending jobs to clone and configure repository
     */
    private Map<File, Future<File>> jobs = new ConcurrentHashMap<>();

    /**
     * @param repo   repository name (github.com/user/repo)
     * @param commit revision
     * @return directory that contains cloned repository, cloning and configuring happen asynchronously
     */
    @Async
    public Future<File> getRepository(String repo, String commit) {
        File workspace = new File(new File(root, repo), commit);
        Future<File> current = jobs.get(workspace);
        if (current != null) {
            return current;
        }
        Future<File> future = taskExecutorConfiguration.taskExecutor().
                submit(new PrepareRepository(workspace, repo, commit));
        jobs.put(workspace, future);
        return future;
    }

    /**
     * Initializes root directory
     */
    @PostConstruct
    private void init() {
        this.root = new File(workspace);
        if (!root.exists()) {
            if (!root.mkdirs()) {
                throw new RuntimeException("Unable to create workspace directory " + workspace);
            }
        } else if (!root.isDirectory()) {
            throw new RuntimeException(workspace + " does not denote a directory");
        }
        LOGGER.info("Using workspace {}", root.getAbsolutePath());
    }

    /**
     * Cleanups all repositories
     *
     * @throws IOException
     */
    public void purge() throws IOException {
        jobs.clear();
        FileUtils.cleanDirectory(root);
        configurationService.purge();
    }

    /**
     * Repository preparation task (clone + configure)
     */
    private class PrepareRepository implements Callable<File> {

        /**
         * Root directory to clone to
         */
        private File workspace;

        /**
         * Repository name (github.com/user/repo)
         */
        private String repo;

        /**
         * Revision
         */
        private String commit;

        private PrepareRepository(File workspace, String repo, String commit) {
            this.workspace = workspace;
            this.repo = repo;
            this.commit = commit;
        }

        @Override
        public File call() throws Exception {
            // if workspace already exists and is directory - it's supposed to be ready
            if (workspace.isDirectory()) {
                return workspace;
            }
            if (workspace.isFile()) {
                throw new WorkspaceException(workspace + " does not denote a directory");
            }
            // making parent directories
            if (!workspace.getParentFile().exists() && !workspace.getParentFile().mkdirs()) {
                throw new WorkspaceException("Unable to create parent directory " + workspace.getParent());
            }
            boolean success = true;
            LOGGER.info("Cloning {}@{} into {}", repo, commit, workspace);
            if (!clone("https://" + repo, workspace) || !reset(workspace, commit)) {
                LOGGER.warn("Unable to clone {}@{} into {}", repo, commit, workspace);
                success = false;
            }
            LOGGER.info("Cloned {}@{} into {}", repo, commit, workspace);
            if (success) {
                try {
                    return configurationService.configure(workspace).get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.warn("Failed to configure workspace {}", workspace, e);
                }
            }
            return workspace;
        }

        /**
         * Clones repository
         * @param uri clone URL
         * @param destination destination directory
         * @return operation status
         */
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

        /**
         * Resets local copy to specific revision
         * @param repoDir local repo directory
         * @param commit revision to reset to
         * @return operation status
         */
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
}
