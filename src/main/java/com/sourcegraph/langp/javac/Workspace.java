package com.sourcegraph.langp.javac;

import com.sourcegraph.langp.config.builder.ScanUtil;
import com.sourcegraph.langp.model.DefSpec;
import com.sourcegraph.langp.model.JavacConfig;
import com.sun.tools.javac.tree.JCTree;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Workspace {

    private Path root;

    private Map<JavacConfig, SymbolIndex> indexCache = new ConcurrentHashMap<>();

    private Map<Path, JavacConfig> configCache = new ConcurrentHashMap<>();

    private Map<JavacConfig, JavacHolder> compilerCache = new ConcurrentHashMap<>();

    private Collection<DefSpec> externalDefs = ConcurrentHashMap.newKeySet();

    /**
     * Instead of looking for javaconfig.json and creating a JavacHolder, just use this.
     * For testing.
     */
    private final JavacHolder testJavac;

    private static Map<Path, Workspace> workspaces = new HashMap<>();

    public static synchronized Workspace getInstance(Path path) {
        Workspace ret = workspaces.get(path);
        if (ret == null) {
            ret = new Workspace(path);
        }
        return ret;
    }

    private Workspace(Path root) {
        this.root = root;
        this.testJavac = null;
        workspaces.put(root, this);
    }

    /**
     * Look for a configuration in a parent directory of uri
     */
    public JavacHolder findCompiler(Path path) {
        if (testJavac != null) {
            return testJavac;
        }

        Path dir = path.getParent();
        JavacConfig config = findConfig(dir);
        if (config == null) {
            throw new NoJavaConfigException("No configuration for " + path);
        }

        if (!config.containsSource(path)) {
            throw new NoJavaConfigException(path + " is not on the source path");
        }
        return compilerCache.computeIfAbsent(config, this::newJavac);
    }

    private JavacHolder newJavac(JavacConfig c) {
        return new JavacHolder(c);
    }


    public SymbolIndex findIndex(Path path) {
        Path dir = path.getParent();
        JavacConfig config = findConfig(dir);
        if (config == null) {
            throw new NoJavaConfigException(path);
        }
        return indexCache.computeIfAbsent(config, this::newIndex);
    }

    private SymbolIndex newIndex(JavacConfig c) {
        return new SymbolIndex(c, root, this);
    }


    public JavacConfig findConfig(Path dir) {
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

    private JavaFileObject findFile(JavacHolder compiler, Path path) {
        return compiler.fileManager.getRegularFile(path.toFile());
    }

    public URI getURI(String uri) {
        return this.root.toUri().resolve(uri);
    }

    public synchronized JCTree.JCCompilationUnit getTree(Path path) {
        JavacHolder compiler = findCompiler(path);
        JavaFileObject file = findFile(compiler, path);
        SymbolIndex index = findIndex(path);

        JCTree.JCCompilationUnit tree = index.get(path.toUri());
        if (tree == null) {
            tree = compiler.parse(file);
            compiler.compile(tree);
            index.update(tree, compiler.context);
        }
        return tree;
    }

    public JavaFileObject getFile(Path path) {
        return findCompiler(path).fileManager.getRegularFile(path.toFile());
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
}