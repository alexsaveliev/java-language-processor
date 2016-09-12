package com.sourcegraph.common.javac;

import com.sourcegraph.common.model.JavacConfig;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * Creates compiler objects and parses/compiles source code
 */
public class JavacHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavacHolder.class);

    /**
     * Compiler to use
     */
    private JavaCompiler compiler;

    /**
     * File manager to use
     */
    StandardJavaFileManager fileManager;

    /**
     * Compiler options (javac options)
     */
    private Collection<String> javacOpts;

    /**
     * Trees object (to fetch source positions), produced after compilation
     */
    Trees trees;

    public JavacHolder(JavacConfig config) {

        compiler = ToolProvider.getSystemJavaCompiler();
        fileManager = compiler.getStandardFileManager(new DiagnosticCollector<>(), null, null);
        javacOpts = getJavacOptions(config, fileManager);
    }

    /**
     * Compiles all the given sources, produces trees object
     * @param sources sources to compile
     * @return AST trees
     * @throws IOException
     */
    public Iterable<? extends CompilationUnitTree> compile(Iterable<? extends JavaFileObject> sources)
            throws IOException {
        JavacTask task = (JavacTask) compiler.getTask(null,
                fileManager,
                diagnostic -> {
                    LOGGER.warn(diagnostic.toString());
                },
                javacOpts,
                null,
                sources);
        Iterable<? extends CompilationUnitTree> units = task.parse();
        task.analyze();
        this.trees = Trees.instance(task);
        return units;
    }

    /**
     * Assembles javac options
     * @param config javac configuration (sources, classpath, output)
     * @param fileManager file manager to use
     * @return
     */
    private Collection<String> getJavacOptions(JavacConfig config,
                                               StandardJavaFileManager fileManager) {
        Collection<String> javacOpts = new LinkedList<>();
        if (!CollectionUtils.isEmpty(config.classPath)) {
            javacOpts.add("-classpath");
            javacOpts.add(StringUtils.join(config.classPath, File.pathSeparatorChar));
            try {
                fileManager.setLocation(StandardLocation.CLASS_PATH,
                        config.classPath.stream().map(File::new)
                                .collect(Collectors.toList()));
            } catch (IOException e) {
                LOGGER.warn("Unable to build classpath location from [{}]",
                        StringUtils.join(config.classPath, File.pathSeparatorChar),
                        e);
            }
        }

        if (!CollectionUtils.isEmpty(config.sources)) {
            javacOpts.add("-sourcepath");
            javacOpts.add(StringUtils.join(config.sources, File.pathSeparatorChar));
            try {
                fileManager.setLocation(StandardLocation.SOURCE_PATH,
                        config.sources.stream().map(File::new)
                                .collect(Collectors.toList()));
            } catch (IOException e) {
                LOGGER.warn("Unable to build sourcepath location from [{}]",
                        StringUtils.join(config.sources, File.pathSeparatorChar),
                        e);
            }
        }

        // not doing dataflow, code gen, etc.
        javacOpts.add("-XDcompilePolicy=attr");
        javacOpts.add("-XDshouldStopPolicyIfError=ATTR");
        javacOpts.add("-XDshouldStopPolicyIfNoError=ATTR");

        javacOpts.add("-implicit:none");

        // turn off warnings
        javacOpts.add("-Xlint:none");

        // turn off annotation processing
        javacOpts.add("-proc:none");

        return javacOpts;

    }
}
