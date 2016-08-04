package com.sourcegraph.langp.javac;

import com.sourcegraph.langp.config.builder.ScanUtil;
import com.sourcegraph.langp.model.DefSpec;
import com.sourcegraph.langp.model.JavacConfig;
import com.sun.tools.javac.tree.JCTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class Workspace {

    private Path root;

    private Map<JavacConfig, SymbolIndex> indexCache = new ConcurrentHashMap<>();

    private Map<Path, JavacConfig> configCache = new ConcurrentHashMap<>();

    private Collection<DefSpec> externalDefs = ConcurrentHashMap.newKeySet();

    private Executor executor;

    Workspace(Path root, Executor executor) {
        this.root = root;
        this.executor = executor;
    }

    public SymbolIndex findIndex(Path path) {
        Path dir = path.getParent();
        JavacConfig config = findConfig(dir);
        if (config == null) {
            throw new NoJavaConfigException(path);
        }
        return indexCache.computeIfAbsent(config,
                javacConfig -> new SymbolIndex(javacConfig, root, this, executor));
    }

    private JavacConfig findConfig(Path dir) {
        return configCache.computeIfAbsent(dir, this::doFindConfig);
    }

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

    public Future<JCTree.JCCompilationUnit> getTree(Path path) {
        return findIndex(path).get(path.toUri());
    }

    public Collection<DefSpec> getExternalDefs() {
        return externalDefs;
    }

    public void computeIndexes() throws IOException {
        Collection<Path> configs = ScanUtil.findMatchingFiles(root, JavacConfig.CONFIG_FILE_NAME);
        for (Path config : configs) {
            findIndex(config);
        }
    }

    public SymbolUnderCursorVisitor getSymbolUnderCursorVisitor(Path sourceFile, long cursor) {
        SymbolIndex index = findIndex(sourceFile);
        return index.getSymbolUnderCursorVisitor(sourceFile, cursor);
    }
}