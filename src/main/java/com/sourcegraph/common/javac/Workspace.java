package com.sourcegraph.common.javac;

import com.sourcegraph.common.config.builder.ScanUtil;
import com.sourcegraph.common.model.JavacConfig;
import com.sourcegraph.common.service.WorkspaceBeingPreparedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Workspace is a collection of indexes (sub-projects)
 */
public class Workspace {

    private static final Logger LOGGER = LoggerFactory.getLogger(Workspace.class);

    private Path root;

    /**
     * config file -> config mapping
     */
    private Map<Path, JavacConfig> configCache = new ConcurrentHashMap<>();

    Workspace(Path root) {
        this.root = root;
    }

    /**
     * @param path source file path
     * @return index that contains symbols defined/used in the given source file
     */
    public SymbolIndex findIndex(Path path) throws WorkspaceBeingPreparedException {
        Path dir = path.getParent();
        JavacConfig config = findConfig(dir);
        if (config == null) {
            throw new WorkspaceBeingPreparedException();
        }
        return new SymbolIndex(config, root);
    }

    /**
     * Ensures that all indexes are computed, blocks execution
     *
     * @throws IOException
     */
    public void computeIndexes(ExecutorService executorService) throws IOException, WorkspaceBeingPreparedException {
        Collection<SymbolIndex> indexes = getIndexes();

        BlockingQueue<Future<SymbolIndex>> queue = new LinkedBlockingQueue<>();
        CompletionService<SymbolIndex> completionService = new ExecutorCompletionService<>(executorService, queue);

        for (SymbolIndex index : indexes) {
            if (index.isBeingIndexed()) {
                queue.add(index.getIndexTask());
            }
            if (index.isIndexed()) {
                continue;
            }
            executorService.submit(() -> index.index(executorService).get());

        }
        int total = queue.size();
        boolean errors = false;
        while (total > 0 && !errors) {
            try {
                completionService.take().get();
            } catch (Exception ex) {
                LOGGER.error("An error occurred while indexing source files", ex);
                errors = true;
            }
            total--;
        }
    }

    /**
     * @return all indexes in given workspace
     * @throws IOException                     if there was an I/O error while searching for index files
     * @throws WorkspaceBeingPreparedException if workspace is not configured (yet)
     */
    public Collection<SymbolIndex> getIndexes() throws IOException, WorkspaceBeingPreparedException {
        Collection<Path> configs = ScanUtil.findMatchingFiles(root, JavacConfig.CONFIG_FILE_NAME);
        Collection<SymbolIndex> indexes = new LinkedList<>();
        for (Path p : configs) {
            indexes.add(findIndex(p));
        }
        return indexes;
    }

    /**
     * @param dir directory to search in
     * @return configuration for specific directory (or any parent)
     */
    private JavacConfig findConfig(Path dir) {
        return configCache.computeIfAbsent(dir, this::doFindConfig);
    }

    /**
     * Searches for configuration in the given directory, climbs up until workspace root is reached
     *
     * @param dir directory to search in
     * @return configuration object to be used for specific directory
     */
    private JavacConfig doFindConfig(Path dir) {
        while (true) {
            JavacConfig found = JavacConfig.read(dir);
            if (found != null) {
                return found;
            } else if (root.startsWith(dir)) {
                return null;
            } else
                dir = dir.getParent();
        }
    }

}