package com.sourcegraph.langp.javac;

import com.sourcegraph.langp.model.JavacConfig;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.*;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintains a reference to a Java compiler, 
 * and several of its internal data structures,
 * which we need to fiddle with to get incremental compilation 
 * and extract the diagnostic information we want.
 */
public class JavacHolder {
    private static final Logger LOG = Logger.getLogger("main");
    // javac places all of its internal state into this Context object,
    // which is basically a Map<String, Object>
    public final Context context = new Context();

    private final DiagnosticListener<JavaFileObject> errors = diagnostic -> {
        LOG.warning(diagnostic.toString());
    };
    
    {
        context.put(DiagnosticListener.class, errors);
    }
    
    // Sets command-line options
    private final Options options = Options.instance(context);
    
    {
        // You would think we could do -Xlint:all, 
        // but some lints trigger fatal errors in the presence of parse errors
        options.put("-Xlint:cast", "");
        options.put("-Xlint:deprecation", "");
        options.put("-Xlint:empty", "");
        options.put("-Xlint:fallthrough", "");
        options.put("-Xlint:finally", "");
        options.put("-Xlint:path", "");
        options.put("-Xlint:unchecked", "");
        options.put("-Xlint:varargs", "");
        options.put("-Xlint:static", "");
    }
    
    // IncrementalLog registers itself in context and pre-empts the normal Log from being created
    private final Log log = Log.instance(context);

    {
        log.multipleErrors = true;
    }

    public final JavacFileManager fileManager = new JavacFileManager(context, true, null);
    private final Check check = Check.instance(context);
    // FuzzyParserFactory registers itself in context and pre-empts the normal ParserFactory from being created
    public final JavaCompiler compiler = JavaCompiler.instance(context);

    {
        // We're going to use the javadoc comments
        compiler.keepComments = true;
    }

    private final Todo todo = Todo.instance(context);
    private final Types types = Types.instance(context);

    public JavacHolder(JavacConfig config) {

        if (config.classPath != null) {
            options.put("-classpath",
                    org.apache.commons.lang3.StringUtils.join(config.classPath, File.pathSeparatorChar));
        }
        if (config.sources != null) {
            options.put("-sourcepath",
                    org.apache.commons.lang3.StringUtils.join(config.sources, File.pathSeparatorChar));
        }
        if (config.outputDirectory != null) {
            options.put("-d", config.outputDirectory);
            Path p = Paths.get(config.outputDirectory);
            ensureOutputDirectory(p);
            clearOutputDirectory(p);
        }
    }

    private void ensureOutputDirectory(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new RuntimeException("Error created output directory " + dir, e);
            }
        }
        else if (!Files.isDirectory(dir))
            throw new RuntimeException("Output directory " + dir + " is not a directory");
    }

    private static void clearOutputDirectory(Path file) {
        try {
            if (file.getFileName().toString().endsWith(".class")) {
                LOG.info("Invalidate " + file);

                Files.setLastModifiedTime(file, FileTime.from(Instant.EPOCH));
            }
            else if (Files.isDirectory(file))
                Files.list(file).forEach(JavacHolder::clearOutputDirectory);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Compile the indicated source file, and its dependencies if they have been modified.
     */
    public JCTree.JCCompilationUnit parse(JavaFileObject source) {
        clear(source);
        return compiler.parse(source);
    }

    public void compile(Collection<JCTree.JCCompilationUnit> parsed) {
        compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.from(parsed)));

        while (!todo.isEmpty()) {
            // We don't do the desugar or generate phases, because they remove method bodies and methods
            Env<AttrContext> next = todo.remove();
            Env<AttrContext> attributedTree = compiler.attribute(next);
            compiler.flow(attributedTree);
        }
    }
    
    /**
     * Compile a source tree produced by this.parse
     */
    // TODO inline
    public void compile(JCTree.JCCompilationUnit source) {
        compile(Collections.singleton(source));
    }

    /**
     * Remove source file from caches in the parse stage
     */
    public void clear(JavaFileObject source) {
        // TODO clear dependencies as well (dependencies should get stored in SymbolIndex)

        // Forget about this file
        Consumer<JavaFileObject> removeFromLog = logRemover(log);

        removeFromLog.accept(source);

        // javac's flow stage will stop early if there are errors
        log.nerrors = 0;
        log.nwarnings = 0;

        // Remove all cached classes that came from this files
        List<Name> remove = new ArrayList<>();

        Consumer<Type> removeFromClosureCache = closureCacheRemover(types);

        check.compiled.forEach((name, symbol) -> {
            if (symbol.sourcefile.getName().equals(source.getName()))
                remove.add(name);

            removeFromClosureCache.accept(symbol.type);
        });

        remove.forEach(check.compiled::remove);

    }

    private static Consumer<Type> closureCacheRemover(Types types) {
        try {
            Field closureCache = Types.class.getDeclaredField("closureCache");

            closureCache.setAccessible(true);

            Map<Type, com.sun.tools.javac.util.List<Type>> value = (Map<Type, com.sun.tools.javac.util.List<Type>>) closureCache.get(types);

            return value::remove;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static Consumer<JavaFileObject> logRemover(Log log) {
        try {
            Field sourceMap = AbstractLog.class.getDeclaredField("sourceMap");

            sourceMap.setAccessible(true);

            Map<JavaFileObject, DiagnosticSource> value = (Map<JavaFileObject, DiagnosticSource>) sourceMap.get(log);

            return value::remove;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
