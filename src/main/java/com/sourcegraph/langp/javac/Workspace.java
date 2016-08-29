package com.sourcegraph.langp.javac;

import com.sourcegraph.langp.config.builder.ScanUtil;
import com.sourcegraph.langp.model.DefSpec;
import com.sourcegraph.langp.model.JavacConfig;
import com.sourcegraph.langp.model.Position;
import com.sourcegraph.langp.model.Symbol;
import com.sourcegraph.langp.service.NoDefinitionFoundException;
import com.sourcegraph.langp.service.SymbolException;
import com.sourcegraph.langp.service.WorkspaceBeingPreparedException;
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
     * source file -> index mapping
     */
    private Map<Path, Future<SymbolIndex>> indexCache = new ConcurrentHashMap<>();

    /**
     * config file -> config mapping
     */
    private Map<Path, JavacConfig> configCache = new ConcurrentHashMap<>();

    /**
     * All the external defs used in workspace
     */
    private Collection<DefSpec> externalDefs = ConcurrentHashMap.newKeySet();

    private ExecutorService executorService;

    Workspace(Path root, ExecutorService executorService) {
        this.root = root;
        this.executorService = executorService;
    }

    /**
     * @param path source file path
     * @return index associated that includes given path
     */
    public Future<SymbolIndex> findIndex(Path path) throws WorkspaceBeingPreparedException {
        Path dir = path.getParent();
        JavacConfig config = findConfig(dir);
        if (config == null) {
            throw new WorkspaceBeingPreparedException();
        }
        return indexCache.computeIfAbsent(config.getFile(),
                configFile -> new SymbolIndex(config, root, this, executorService).index());
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

    /**
     * @return all the external defs
     */
    public Collection<DefSpec> getExternalDefs() {
        return externalDefs;
    }

    /**
     * @return all the exported symbols
     */
    public Collection<Symbol> getExportedSymbols() {
        Collection<Symbol> ret = new LinkedList<>();
        for (Future<SymbolIndex> futureIndex : indexCache.values()) {
            try {
                SymbolIndex index = futureIndex.get();
                ret.addAll(index.definitions());
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("An error occurred while computing index", e);
            }
        }
        return ret;
    }

    /**
     * Ensures that all indexes are computed, blocks execution
     * @throws IOException
     */
    public void computeIndexes() throws IOException, WorkspaceBeingPreparedException {
        Collection<Path> configs = ScanUtil.findMatchingFiles(root, JavacConfig.CONFIG_FILE_NAME);
        BlockingQueue<Future<SymbolIndex>> queue = new LinkedBlockingQueue<>();
        for (Path config : configs) {
            try {
                queue.put(findIndex(config));
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while computing indexes", e);
            }
        }
        CompletionService<SymbolIndex> completionService =
                new ExecutorCompletionService<>(executorService, queue);
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
     * @return position of symbol defined by the given spec
     */
    public Position defSpecToPosition(DefSpec defSpec)
            throws WorkspaceBeingPreparedException, SymbolException, NoDefinitionFoundException {
        for (Future<SymbolIndex> futureIndex : indexCache.values()) {
            if (!futureIndex.isDone()) {
                throw new WorkspaceBeingPreparedException();
            }
            try {
                SymbolIndex index = futureIndex.get();
                Position pos = index.defSpecToPosition(defSpec);
                if (pos != null) {
                    return pos;
                }
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("An error occurred while computing index", e);
                throw new SymbolException("Failed to compute index");
            }
        }
        throw new NoDefinitionFoundException();
    }

}