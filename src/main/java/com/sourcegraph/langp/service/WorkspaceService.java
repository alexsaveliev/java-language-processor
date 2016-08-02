package com.sourcegraph.langp.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Service
public class WorkspaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceService.class);

    @Autowired
    private ConfigurationService configurationService;

    @Value("${workspace:${SGPATH:${user.home}/.sourcegraph}/workspace/java}")
    private String workspace;

    private File root;

    @Value("${clone.threads:10}")
    private int numThreads;

    private Set<File> beingCloned = ConcurrentHashMap.newKeySet();

    private ExecutorService executorService;

    public File getWorkspace(String repo, String commit)
            throws WorkspaceBeingClonedException, WorkspaceBeingConfiguredException, WorkspaceException {
        File workspace = new File(new File(root, repo), commit);
        if (beingCloned.contains(workspace)) {
            throw new WorkspaceBeingClonedException();
        }
        if (configurationService.isBeingConfigured(workspace)) {
            throw new WorkspaceBeingConfiguredException();
        }
        if (workspace.isDirectory()) {
            return workspace;
        }
        if (workspace.isFile()) {
            throw new WorkspaceException(workspace + " does not denote a directory");
        }
        if (!workspace.getParentFile().exists() && !workspace.getParentFile().mkdirs()) {
            throw new WorkspaceException("Unable to create parent directory " + workspace.getParent());
        }
        schedule(workspace, repo, commit);
        throw new WorkspaceBeingClonedException();
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

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("clone-%d")
                .setDaemon(true)
                .build();
        this.executorService = Executors.newFixedThreadPool(numThreads,
                threadFactory);

    }

    private void schedule(File workspace, String repo, String commit) {
        beingCloned.add(workspace);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                boolean success = true;
                try {
                    LOGGER.info("Cloning {}/{} into {}", repo, commit, workspace);
                    if (!clone("https://" + repo, workspace) || !reset(workspace, commit)) {
                        LOGGER.warn("Unable to clone {}/{} into {}", repo, commit, workspace);
                        success = false;
                    }
                    LOGGER.info("Cloned {}/{} into {}", repo, commit, workspace);
                } finally {
                    beingCloned.remove(workspace);
                }
                if (success) {
                    configurationService.schedule(workspace);
                }
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
        });
    }
}
