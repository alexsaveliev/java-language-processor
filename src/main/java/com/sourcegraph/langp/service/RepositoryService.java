package com.sourcegraph.langp.service;

import com.sourcegraph.langp.configuration.TaskExecutorConfiguration;
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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Service
public class RepositoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryService.class);

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private TaskExecutorConfiguration taskExecutorConfiguration;

    @Value("${workspace:${SGPATH:${user.home}/.sourcegraph}/workspace/java}")
    private String workspace;

    private File root;

    private Map<File, Future<File>> jobs = new ConcurrentHashMap<>();

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

    private class PrepareRepository implements Callable<File> {

        private File workspace;
        private String repo;
        private String commit;

        private PrepareRepository(File workspace, String repo, String commit) {
            this.workspace = workspace;
            this.repo = repo;
            this.commit = commit;
        }

        @Override
        public File call() throws Exception {
                if (workspace.isDirectory()) {
                    return workspace;
                }
                if (workspace.isFile()) {
                    throw new WorkspaceException(workspace + " does not denote a directory");
                }
                if (!workspace.getParentFile().exists() && !workspace.getParentFile().mkdirs()) {
                    throw new WorkspaceException("Unable to create parent directory " + workspace.getParent());
                }
                boolean success = true;
                LOGGER.info("Cloning {}/{} into {}", repo, commit, workspace);
                if (!clone("https://" + repo, workspace) || !reset(workspace, commit)) {
                    LOGGER.warn("Unable to clone {}/{} into {}", repo, commit, workspace);
                    success = false;
                }
                LOGGER.info("Cloned {}/{} into {}", repo, commit, workspace);
                if (success) {
                    try {
                        return configurationService.configure(workspace).get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.warn("Failed to configure workspace {}", workspace, e);
                    }
                }
                return workspace;
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
}
