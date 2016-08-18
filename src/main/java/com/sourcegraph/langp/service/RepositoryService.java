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
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.*;

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
     * Wait no more than X milliseconds to acquire object
     */
    @Value("${workspace.get.timeout:250}")
    private long timeout;

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
        return getRepository(repo, commit, false);
    }

    /**
     * @param repo   repository name (github.com/user/repo)
     * @param commit revision
     * @param update indicates if we should force update
     * @return directory that contains cloned repository, cloning and configuring happen asynchronously
     */
    @Async
    public Future<File> getRepository(String repo, String commit, boolean update) {
        File workspace = Paths.get(this.workspace, repo, commit, "workspace").toFile();
        Future<File> current = !update ? jobs.get(workspace) : null;
        if (current != null) {
            return current;
        }
        Future<File> future = taskExecutorConfiguration.taskExecutor().
                submit(new PrepareRepository(workspace, repo, commit, update));
        jobs.put(workspace, future);
        return future;
    }

    /**
     * Initializes root directory
     */
    @PostConstruct
    private void init() {
        File root = new File(workspace);
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
     * @throws Exception
     */
    public void purge() throws Exception {
        // park all
        for (Future<?> future : jobs.values()) {
            future.get();
        }
        jobs.clear();
        FileUtils.cleanDirectory(new File(workspace));
        configurationService.purge();
    }

    /**
     * Waits for N milliseconds to acquire workspace object
     *
     * @param repo   repository
     * @param commit revision
     * @return workspace object if ready
     * @throws WorkspaceBeingPreparedException if workspace object is being prepared
     * @throws WorkspaceException              if workspace configuration error occurred
     */
    public File getWorkspace(String repo, String commit)
            throws WorkspaceBeingPreparedException, WorkspaceException {
        Future<File> workspaceRoot = getRepository(repo, commit);
        try {
            return workspaceRoot.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("An error occurred while fetching workspace for {}@{}", repo, commit, e);
            throw new WorkspaceException(e);
        } catch (TimeoutException e) {
            throw new WorkspaceBeingPreparedException();
        }
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

        private boolean update;

        private PrepareRepository(File workspace, String repo, String commit, boolean update) {
            this.workspace = workspace;
            this.repo = repo;
            this.commit = commit;
            this.update = update;
        }

        @Override
        public File call() throws Exception {
            // if workspace already exists and is directory - it's supposed to be ready
            if (workspace.isDirectory()) {
                if (update) {
                    try {
                        return configurationService.configure(workspace, true).get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.warn("Failed to configure workspace {}", workspace, e);
                    }
                }
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
         *
         * @param uri         clone URL
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
         *
         * @param repoDir local repo directory
         * @param commit  revision to reset to
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
