package com.sourcegraph.langp.javac;

import com.sourcegraph.langp.model.DefSpec;
import com.sourcegraph.langp.model.JavacConfig;
import com.sourcegraph.langp.model.Position;
import com.sourcegraph.langp.model.Range;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Name;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tracks definitions and referencs
 */
public class SymbolIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolIndex.class);

    private static class SourceFileIndex {
        private final EnumMap<ElementKind, Map<String, com.sourcegraph.langp.model.Symbol>> declarations =
                new EnumMap<>(ElementKind.class);
        private final EnumMap<ElementKind, Map<String, Set<Range>>> references = new EnumMap<>(ElementKind.class);
    }

    /**
     * Source path files, for which we support methods and classes
     */
    private Map<URI, SourceFileIndex> sourcePath = new ConcurrentHashMap<>();

    /**
     * Active files, for which we index locals
     */
    private Map<URI, Future<JCTree.JCCompilationUnit>> activeDocuments = new ConcurrentHashMap<>();

    private Path root;

    private Workspace workspace;

    private JavacConfig config;

    private ExecutorService executorService;

    private Trees trees;

    public SymbolIndex(JavacConfig config,
                       Path root,
                       Workspace workspace,
                       ExecutorService executorService) {

        this.config = config;
        this.root = root;
        this.workspace = workspace;
        this.executorService = executorService;
    }

    /**
     * @param symbol local symbol
     * @return references to a local symbol
     */
    public Collection<Range> references(Symbol symbol) {
        // For indexed symbols, just look up the precomputed references

        Collection<Range> references = new LinkedList<>();
        if (shouldIndex(symbol)) {
            String key = uniqueName(symbol);
            for (SourceFileIndex index : sourcePath.values()) {
                Map<String, Set<Range>> withKind = index.references.getOrDefault(symbol.getKind(),
                        Collections.emptyMap());
                Collection<Range> results = withKind.get(key);
                if (results != null) {
                    references.addAll(results);
                }
            }
        }
        // For non-indexed symbols, scan the active set
        else {
            for (Future<JCTree.JCCompilationUnit> documentFuture : activeDocuments.values()) {
                final JCTree.JCCompilationUnit document;
                try {
                    document = documentFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.error("An error occurred while retrieving index", e);
                    continue;
                }
                document.accept(new TreeScanner() {
                    @Override
                    public void visitSelect(JCTree.JCFieldAccess tree) {
                        super.visitSelect(tree);

                        if (tree.sym != null && tree.sym.equals(symbol))
                            references.add(range(tree, document));
                    }

                    @Override
                    public void visitReference(JCTree.JCMemberReference tree) {
                        super.visitReference(tree);

                        if (tree.sym != null && tree.sym.equals(symbol))
                            references.add(range(tree, document));
                    }

                    @Override
                    public void visitIdent(JCTree.JCIdent tree) {
                        super.visitIdent(tree);

                        if (tree.sym != null && tree.sym.equals(symbol))
                            references.add(range(tree, document));
                    }
                });
            }
        }
        return references;
    }

    /**
     * @return all local definitions
     */
    public Collection<com.sourcegraph.langp.model.Symbol> definitions() {
        Collection<com.sourcegraph.langp.model.Symbol> ret = new LinkedList<>();
        for (SourceFileIndex index : sourcePath.values()) {
            for (Map<String, com.sourcegraph.langp.model.Symbol> withKind : index.declarations.values()) {
                ret.addAll(withKind.values());
            }
        }
        return ret;
    }

    /**
     * @param symbol symbol to search for
     * @return symbol's information
     */
    public com.sourcegraph.langp.model.Symbol findSymbol(Symbol symbol) {
        ElementKind kind = symbol.getKind();
        String key = uniqueName(symbol);

        for (SourceFileIndex f : sourcePath.values()) {
            Map<String, com.sourcegraph.langp.model.Symbol> withKind = f.declarations.getOrDefault(kind,
                    Collections.emptyMap());
            com.sourcegraph.langp.model.Symbol sym = withKind.get(key);
            if (sym != null) {
                return sym;
            }
        }

        for (Future<JCTree.JCCompilationUnit> future : activeDocuments.values()) {
            JCTree.JCCompilationUnit compilationUnit;
            try {
                compilationUnit = future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                return null;
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("An error occurred", e);
                return null;
            }
            JCTree symbolTree = TreeInfo.declarationFor(symbol, compilationUnit);
            if (symbolTree != null) {
                Range range = range(symbolTree, compilationUnit);
                com.sourcegraph.langp.model.Symbol s = new com.sourcegraph.langp.model.Symbol();
                s.setRange(range);
                s.setName(symbol.getQualifiedName().toString());
                s.setPath(key);
                s.setKind(symbol.getKind().name().toLowerCase());
                s.setDocHtml(compilationUnit.docComments.getCommentText(symbolTree));
                s.setFile(root.toUri().relativize(compilationUnit.getSourceFile().toUri()).toString());
                return s;

            }
        }

        return null;
    }

    /**
     * Traverses AST trees and collects references and definitions
     */
    private class Indexer extends BaseScanner {

        private SourceFileIndex index;

        private JCTree.JCCompilationUnit tree;

        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            URI uri = tree.getSourceFile().toUri();
            this.tree = tree;
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

        /**
         * Adds new definition
         *
         * @param tree
         * @param symbol
         */
        private void addDeclaration(JCTree tree, Symbol symbol) {
            if (symbol != null && shouldIndex(symbol)) {
                String key = uniqueName(symbol);
                Range range = range(tree, compilationUnit);
                Map<String, com.sourcegraph.langp.model.Symbol> withKind = index.declarations.computeIfAbsent(
                        symbol.getKind(),
                        newKind -> new HashMap<>());
                com.sourcegraph.langp.model.Symbol s = new com.sourcegraph.langp.model.Symbol();
                s.setRange(range);
                s.setName(symbol.getQualifiedName().toString());
                s.setPath(key);
                s.setKind(symbol.getKind().name().toLowerCase());
                s.setDocHtml(this.tree.docComments.getCommentText(tree));
                s.setFile(root.toUri().relativize(this.tree.getSourceFile().toUri()).toString());
                withKind.put(key, s);
            }
        }

        /**
         * Adds new reference
         *
         * @param tree
         * @param symbol
         */
        private void addReference(JCTree tree, Symbol symbol) {
            if (symbol != null && shouldIndex(symbol)) {
                String key = uniqueName(symbol);
                JavaFileObject externalOrigin = getExternalOrigin(symbol);
                if (externalOrigin == null) {
                    Map<String, Set<Range>> withKind = index.references.computeIfAbsent(symbol.getKind(),
                            newKind -> new HashMap<>());
                    Set<Range> ranges = withKind.computeIfAbsent(key, newName -> new HashSet<>());
                    Range range = range(tree, compilationUnit);
                    ranges.add(range);
                } else {
                    String repository = Origin.getRepository(externalOrigin, config);
                    if (repository != null) {
                        DefSpec def = new DefSpec();
                        def.setPath(key);
                        def.setRepo(repository);
                        workspace.getExternalDefs().add(def);
                    }
                }
            }
        }
    }

    /**
     * @param symbol
     * @return true if we should keep index for a given symbol's kind
     */
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

    /**
     * @param tree
     * @param compilationUnit
     * @return range object for a given node
     */
    private Range range(JCTree tree, JCTree.JCCompilationUnit compilationUnit) {
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
            } else if (tree instanceof JCTree.JCMethodDecl) {
                Symbol.MethodSymbol symbol = ((JCTree.JCMethodDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            } else if (tree instanceof JCTree.JCVariableDecl) {
                Symbol.VarSymbol symbol = ((JCTree.JCVariableDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            }

            Range range = findRange(compilationUnit.getSourceFile(), offset, end);
            URI full = compilationUnit.getSourceFile().toUri();
            String uri = root.toUri().relativize(full).toString();
            range.setFile(uri);
            return range;
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

    /**
     * @param symbol symbol
     * @return file object if symbol refers to external artifact (comes from classpath, not sourcepath) or null
     */
    private static JavaFileObject getExternalOrigin(Symbol symbol) {
        Symbol.ClassSymbol classSymbol = forElement(symbol);
        if (classSymbol == null) {
            return null;
        }
        if (classSymbol.sourcefile == null) {
            return classSymbol.classfile;
        }
        if (classSymbol.sourcefile.toUri().isAbsolute()) {
            return null;
        }
        return classSymbol.classfile;
    }

    /**
     * @param e
     * @return class symbol for a given element (climbs up if needed)
     */
    private static Symbol.ClassSymbol forElement(Element e) {
        if (e == null) {
            return null;
        }
        switch (e.getKind()) {
            case CLASS:
            case INTERFACE:
            case ENUM:
            case ANNOTATION_TYPE:
                return (Symbol.ClassSymbol) e;
            default:
                return forElement(e.getEnclosingElement());
        }
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

    public Future<JCTree.JCCompilationUnit> get(URI sourceFile) {
        return activeDocuments.get(sourceFile);
    }

    private static Range findRange(JavaFileObject file, long startOffset, long endOffset) throws IOException {
        try (Reader in = file.openReader(true)) {
            long offset = 0;
            int line = 0;
            int character = 0;

            // Find the start position
            while (offset < startOffset) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n') {
                        line++;
                        character = 0;
                    }
                }
            }

            Range ret = new Range();
            ret.setStartLine(line);
            ret.setStartCharacter(character);

            // Find the end position
            while (offset < endOffset) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n') {
                        line++;
                        character = 0;
                    }
                }
            }

            ret.setEndLine(line);
            ret.setEndCharacter(character);

            return ret;
        }
    }

    /**
     * Schedules building of all indexes
     */
    private class IndexBuilder implements Callable<SymbolIndex> {

        private Path root;
        private JavacConfig config;

        IndexBuilder(Path root, JavacConfig config) {
            this.root = root;
            this.config = config;
        }

        @Override
        public SymbolIndex call() throws Exception {
            LOGGER.info("Building indexes for [{}]", StringUtils.join(config.sources, ' '));
            JavacHolder javacHolder = new JavacHolder(config);
            Iterable<? extends JavaFileObject> sources;
            sources = getSourceFiles(javacHolder.fileManager);
            Iterable<? extends CompilationUnitTree> units;
            units = javacHolder.compile(sources);
            trees = javacHolder.trees;
            CompletionService<JCTree.JCCompilationUnit> completionService =
                    new ExecutorCompletionService<>(executorService);

            int total = 0;
            for (CompilationUnitTree unit : units) {
                JCTree.JCCompilationUnit jcCompilationUnit = (JCTree.JCCompilationUnit) unit;
                total++;
                activeDocuments.put(unit.getSourceFile().toUri(), completionService.submit(() -> {
                    LOGGER.info("Indexing {}", unit.getSourceFile().getName());
                    jcCompilationUnit.accept(new Indexer());
                    return jcCompilationUnit;
                }));
            }
            boolean errors = false;
            while (total > 0 && !errors) {
                try {
                    Future<JCTree.JCCompilationUnit> future = completionService.take();
                    future.get();
                } catch (Exception ex) {
                    LOGGER.error("An error occurred while indexing source files", ex);
                    errors = true;
                }
                total--;
            }
            return SymbolIndex.this;
        }

        private Iterable<? extends JavaFileObject> getSourceFiles(StandardJavaFileManager fileManager)
                throws IOException {
            Collection<String> sources = new LinkedList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".java")) {
                        sources.add(file.toAbsolutePath().normalize().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

            });
            return fileManager.getJavaFileObjectsFromStrings(sources);
        }
    }

    public SymbolUnderCursorVisitor getSymbolUnderCursorVisitor(Path sourceFile, long cursor) {
        return new SymbolUnderCursorVisitor(cursor, trees);
    }

    /**
     * Starts indexing
     *
     * @return
     */
    public Future<SymbolIndex> index() {
        return this.executorService.submit(new IndexBuilder(root, config));
    }

    /**
     * @param defSpec
     * @return position of symbol defined by a given path if found
     */
    Position defSpecToPosition(DefSpec defSpec) {
        for (SourceFileIndex index : sourcePath.values()) {
            for (Map<String, com.sourcegraph.langp.model.Symbol> withKind : index.declarations.values()) {
                for (com.sourcegraph.langp.model.Symbol symbol : withKind.values()) {
                    if (symbol.getPath().equals(defSpec.getPath())) {
                        Position ret = new Position();
                        ret.setFile(symbol.getFile());
                        ret.setLine(symbol.getRange().getStartLine());
                        ret.setCharacter(symbol.getRange().getStartCharacter());
                        return ret;
                    }
                }
            }
        }
        return null;
    }

}