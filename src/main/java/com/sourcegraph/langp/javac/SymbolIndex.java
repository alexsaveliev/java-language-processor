package com.sourcegraph.langp.javac;

import com.sourcegraph.langp.model.JavacConfig;
import com.sourcegraph.langp.model.Position;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.lang.model.element.ElementKind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SymbolIndex {
    private static final Logger LOG = Logger.getLogger("main");

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete = new CompletableFuture<>();

    private static class SourceFileIndex {
        private final EnumMap<ElementKind, Map<String, Position>> declarations = new EnumMap<>(ElementKind.class);
        //private final EnumMap<ElementKind, Map<String, Set<Location>>> references = new EnumMap<>(ElementKind.class);
    }

    /**
     * Source path files, for which we support methods and classes
     */
    private Map<URI, SourceFileIndex> sourcePath = new ConcurrentHashMap<>();

    /**
     * Active files, for which we index locals
     */
    private Map<URI, JCTree.JCCompilationUnit> activeDocuments = new ConcurrentHashMap<>();

    private URI root;

    @FunctionalInterface
    public interface ReportDiagnostics {
        void report(Collection<Path> paths, DiagnosticCollector<JavaFileObject> diagnostics);
    }

    public SymbolIndex(JavacConfig config,
                       Path root) {

        if (root != null) {
            this.root = root.toUri();
        }

        JavacHolder compiler = new JavacHolder(config);
        Indexer indexer = new Indexer(compiler.context);

        Thread worker = new Thread("InitialIndex") {
            List<JCTree.JCCompilationUnit> parsed = new ArrayList<>();
            List<Path> paths = new ArrayList<>();

            @Override
            public void run() {
                // Parse each file
                config.sources.forEach(s -> parseAll(Paths.get(s), parsed, paths));

                // Compile all parsed files
                compiler.compile(parsed);

                parsed.forEach(p -> p.accept(indexer));

                // TODO minimize memory use during this process
                // Instead of doing parse-all / compile-all, 
                // queue all files, then do parse / compile on each
                // If invoked correctly, javac should avoid reparsing the same file twice
                // Then, use the same mechanism as the desugar / generate phases to remove method bodies, 
                // to reclaim memory as we go
                initialIndexComplete.complete(null);

                // TODO verify that compiler and all its resources get destroyed
            }

            /**
             * Look for .java files and invalidate them
             */
            private void parseAll(Path path, List<JCTree.JCCompilationUnit> trees, List<Path> paths) {
                if (Files.isDirectory(path)) try {
                    Files.list(path).forEach(p -> parseAll(p, trees, paths));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                else if (path.getFileName().toString().endsWith(".java")) {
                    LOG.info("Index " + path);

                    JavaFileObject file = compiler.fileManager.getRegularFile(path.toFile());

                    trees.add(compiler.parse(file));
                    paths.add(path);
                }
            }
        };

        worker.start();
    }

    // TODO
    /*
    public Stream<? extends Location> references(Symbol symbol) {
        // For indexed symbols, just look up the precomputed references
        if (shouldIndex(symbol)) {
            String key = uniqueName(symbol);

            return sourcePath.values().stream().flatMap(f -> {
                Map<String, Set<Location>> bySymbol = f.references.getOrDefault(symbol.getKind(), Collections.emptyMap());
                Set<Location> locations = bySymbol.getOrDefault(key, Collections.emptySet());

                return locations.stream();
            });
        }
        // For non-indexed symbols, scan the active set
        else {
            return activeDocuments.values().stream().flatMap(compilationUnit -> {
                List<LocationImpl> references = new ArrayList<>();

                compilationUnit.accept(new TreeScanner() {
                    @Override
                    public void visitSelect(JCTree.JCFieldAccess tree) {
                        super.visitSelect(tree);

                        if (tree.sym != null && tree.sym.equals(symbol))
                            references.add(location(tree, compilationUnit));
                    }

                    @Override
                    public void visitReference(JCTree.JCMemberReference tree) {
                        super.visitReference(tree);

                        if (tree.sym != null && tree.sym.equals(symbol))
                            references.add(location(tree, compilationUnit));
                    }

                    @Override
                    public void visitIdent(JCTree.JCIdent tree) {
                        super.visitIdent(tree);

                        if (tree.sym != null && tree.sym.equals(symbol))
                            references.add(location(tree, compilationUnit));
                    }
                });


                return references.stream();
            });
        }
    }
    */

    public Position findSymbol(Symbol symbol) {
        ElementKind kind = symbol.getKind();
        String key = uniqueName(symbol);

        for (SourceFileIndex f : sourcePath.values()) {
            Map<String, Position> withKind = f.declarations.getOrDefault(kind, Collections.emptyMap());
            Position p = withKind.get(key);
            if (p != null) {
                return p;
            }
        }

        for (JCTree.JCCompilationUnit compilationUnit : activeDocuments.values()) {
            JCTree symbolTree = TreeInfo.declarationFor(symbol, compilationUnit);

            if (symbolTree != null) {
                return position(symbolTree, compilationUnit);
            }
        }

        return null;
    }

    private class Indexer extends BaseScanner {
        private SourceFileIndex index;

        public Indexer(Context context) {
            super(context);
        }

        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            URI uri = tree.getSourceFile().toUri();

            index = new SourceFileIndex();
            sourcePath.put(uri, index);

            super.visitTopLevel(tree);

        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            super.visitClassDef(tree);

            addDeclaration(tree, tree.sym);
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            super.visitMethodDef(tree);

            addDeclaration(tree, tree.sym);
        }

        @Override
        public void visitVarDef(JCTree.JCVariableDecl tree) {
            super.visitVarDef(tree);

            addDeclaration(tree, tree.sym);
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            super.visitSelect(tree);

            addReference(tree, tree.sym);
        }

        @Override
        public void visitReference(JCTree.JCMemberReference tree) {
            super.visitReference(tree);

            addReference(tree, tree.sym);
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            addReference(tree, tree.sym);
        }

        @Override
        public void visitNewClass(JCTree.JCNewClass tree) {
            super.visitNewClass(tree);

            addReference(tree, tree.constructor);
        }

        private void addDeclaration(JCTree tree, Symbol symbol) {
            if (symbol != null && onSourcePath(symbol) && shouldIndex(symbol)) {
                String key = uniqueName(symbol);
                Position position = position(tree, compilationUnit);
                Map<String, Position> withKind = index.declarations.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());
                withKind.put(key, position);
            }
        }

        private void addReference(JCTree tree, Symbol symbol) {
            // TODO
            /*
            if (symbol != null && onSourcePath(symbol) && shouldIndex(symbol)) {
                String key = uniqueName(symbol);
                Map<String, Set<Location>> withKind = index.references.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());
                Set<Location> locations = withKind.computeIfAbsent(key, newName -> new HashSet<>());
                LocationImpl location = location(tree, compilationUnit);

                locations.add(location);
            }
            */
        }
    }

    private static boolean shouldIndex(Symbol symbol) {
        ElementKind kind = symbol.getKind();

        switch (kind) {
            case ENUM:
            case ANNOTATION_TYPE:
            case INTERFACE:
            case ENUM_CONSTANT:
            case FIELD:
            case METHOD:
                return true;
            case CLASS:
                return !symbol.isAnonymous();
            case CONSTRUCTOR:
                // TODO also skip generated constructors
                return !symbol.getEnclosingElement().isAnonymous();
            default:
                return false;
        }
    }

    private Position position(JCTree tree, JCTree.JCCompilationUnit compilationUnit) {
        try {
            // Declaration should include offset
            int offset = tree.pos;
            int end = tree.getEndPosition(null);

            // If symbol is a class, offset points to 'class' keyword, not name
            // Find the name by searching the text of the source, starting at the 'class' keyword
            if (tree instanceof JCTree.JCClassDecl) {
                Symbol.ClassSymbol symbol = ((JCTree.JCClassDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            }
            else if (tree instanceof JCTree.JCMethodDecl) {
                Symbol.MethodSymbol symbol = ((JCTree.JCMethodDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            }
            else if (tree instanceof JCTree.JCVariableDecl) {
                Symbol.VarSymbol symbol = ((JCTree.JCVariableDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            }
            // TODO
            /*
            RangeImpl position = JavaLanguageServer.findPosition(compilationUnit.getSourceFile(),
                                                                 offset,
                                                                 end);
            LocationImpl location = new LocationImpl();

            URI full = compilationUnit.getSourceFile().toUri();
            String uri = root.relativize(full).toString();
            location.setUri(uri);
            location.setRange(position);

            return location;
            */
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int offset(JCTree.JCCompilationUnit compilationUnit,
                              Symbol symbol,
                              int estimate) throws IOException {
        CharSequence content = compilationUnit.sourcefile.getCharContent(false);
        Name name = symbol.getSimpleName();

        estimate = indexOf(content, name, estimate);
        return estimate;
    }

    /**
     * Adapted from java.util.String.
     * <p>
     * The source is the character array being searched, and the target
     * is the string being searched for.
     *
     * @param source    the characters being searched.
     * @param target    the characters being searched for.
     * @param fromIndex the index to begin searching from.
     */
    private static int indexOf(CharSequence source, CharSequence target, int fromIndex) {
        int sourceOffset = 0, sourceCount = source.length(), targetOffset = 0, targetCount = target.length();

        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            return fromIndex;
        }

        char first = target.charAt(targetOffset);
        int max = sourceOffset + (sourceCount - targetCount);

        for (int i = sourceOffset + fromIndex; i <= max; i++) {
            /* Look for first character. */
            if (source.charAt(i) != first) {
                while (++i <= max && source.charAt(i) != first) ;
            }

            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + targetCount - 1;
                for (int k = targetOffset + 1; j < end && source.charAt(j) == target.charAt(k); j++, k++) ;

                if (j == end) {
                    /* Found whole string. */
                    return i - sourceOffset;
                }
            }
        }
        return -1;
    }

    private static boolean onSourcePath(Symbol symbol) {
        return true; // TODO
    }

    private static String uniqueName(Symbol s) {
        StringJoiner acc = new StringJoiner(".");

        createUniqueName(s, acc);

        return acc.toString();
    }

    private static void createUniqueName(Symbol s, StringJoiner acc) {
        if (s != null) {
            createUniqueName(s.owner, acc);

            if (!s.getSimpleName().isEmpty())
                acc.add(s.getSimpleName().toString());
        }
    }

    // TODO
    /*
    private static int symbolInformationKind(ElementKind kind) {
        switch (kind) {
            case PACKAGE:
                return SymbolInformation.KIND_PACKAGE;
            case ENUM:
            case ENUM_CONSTANT:
                return SymbolInformation.KIND_ENUM;
            case CLASS:
                return SymbolInformation.KIND_CLASS;
            case ANNOTATION_TYPE:
            case INTERFACE:
                return SymbolInformation.KIND_INTERFACE;
            case FIELD:
                return SymbolInformation.KIND_PROPERTY;
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case TYPE_PARAMETER:
                return SymbolInformation.KIND_VARIABLE;
            case METHOD:
            case STATIC_INIT:
            case INSTANCE_INIT:
                return SymbolInformation.KIND_METHOD;
            case CONSTRUCTOR:
                return SymbolInformation.KIND_CONSTRUCTOR;
            case OTHER:
            case RESOURCE_VARIABLE:
            default:
                return SymbolInformation.KIND_STRING;
        }
    }
    */

    public void update(JCTree.JCCompilationUnit tree, Context context) {
        Indexer indexer = new Indexer(context);

        tree.accept(indexer);

        activeDocuments.put(tree.getSourceFile().toUri(), tree);
    }

    public JCTree.JCCompilationUnit get(URI sourceFile) {
        return activeDocuments.get(sourceFile);
    }
}