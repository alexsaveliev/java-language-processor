package com.sourcegraph.common.javac;

import com.sourcegraph.common.model.Hover;
import com.sourcegraph.common.model.JavacConfig;
import com.sourcegraph.common.model.Range;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Name;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tracks definitions and referencs
 */
public class SymbolIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolIndex.class);

    /**
     * Def's marker in CSV
     */
    public static final String DEF = "def";

    /**
     * Ref's marker in CSV
     */
    public static final String REF = "ref";

    /**
     * Marks that reference points to external repository but we weren't able to extract it
     */
    private static final String UNKNOWN_REPOSITORY = "?";

    public  static final String UNIT_TYPE = "JavaArtifact";

    private Path root;

    private JavacConfig config;

    private Future<SymbolIndex> future;

    SymbolIndex(JavacConfig config,
                Path root) {

        this.config = config;
        this.root = root;
    }

    /**
     * @return associated configuration object
     */
    public JavacConfig getConfig() {
        return config;
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

        private JavacConfig config;

        private ExecutorService executorService;

        IndexBuilder(JavacConfig config, ExecutorService executorService) {
            this.config = config;
            this.executorService = executorService;
        }

        @Override
        public SymbolIndex call() throws Exception {
            LOGGER.info("Building indexes for [{}]", StringUtils.join(config.sources, ' '));
            JavacHolder javacHolder = new JavacHolder(config);
            Iterable<? extends JavaFileObject> sources;
            sources = getSourceFiles(javacHolder.fileManager);
            Iterable<? extends CompilationUnitTree> units = javacHolder.compile(sources);
            CompletionService<JCTree.JCCompilationUnit> completionService =
                    new ExecutorCompletionService<>(executorService);

            File indexFile = getIndexWriteFile();

            CSVPrinter printer = CSVFormat.DEFAULT.print(new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(indexFile),
                    "utf-8")));

            ThreadSafeCSVPrinter threadSafeCSVPrinter = new ThreadSafeCSVPrinter(printer);

            int total = 0;
            for (CompilationUnitTree unit : units) {
                JCTree.JCCompilationUnit jcCompilationUnit = (JCTree.JCCompilationUnit) unit;
                total++;
                completionService.submit(() -> {
                    LOGGER.info("Indexing {}", unit.getSourceFile().getName());
                    jcCompilationUnit.accept(new Indexer(javacHolder.trees, threadSafeCSVPrinter));
                    return jcCompilationUnit;
                });
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
            threadSafeCSVPrinter.flush();
            threadSafeCSVPrinter.close();

            // atomic rename to destination
            indexFile.renameTo(getIndexFile());

            future = null;
            LOGGER.info("Built indexes for [{}]", StringUtils.join(config.sources, ' '));
            return SymbolIndex.this;
        }

        private Iterable<? extends JavaFileObject> getSourceFiles(StandardJavaFileManager fileManager)
                throws IOException {
            return fileManager.getJavaFileObjectsFromStrings(config.files);
        }
    }

    /**
     * Starts indexing
     *
     * @return
     */
    public Future<SymbolIndex> index(ExecutorService executorService) {
        future = executorService.submit(new IndexBuilder(config, executorService));
        return future;
    }

    /**
     * @return true if symbols are being indexed
     */
    public boolean isBeingIndexed() {
        return future != null;
    }

    /**
     * @return pending index task
     */
    public Future<SymbolIndex> getIndexTask() {
        return future;
    }

    /**
     * @return true if symbols are indexed
     */
    public boolean isIndexed() {
        return getIndexFile().exists();
    }

    /**
     * @param acceptor function that identifies records' acceptance
     * @return index records matching given function
     */
    public SymbolResultSet getRecords(Acceptor acceptor) throws IOException {
        return new SymbolResultSet(getIndex(), acceptor);
    }

    /**
     * Transforms CSV record to Symbol
     *
     * @param record CSV record to transform
     * @return symbol object
     */
    public static com.sourcegraph.common.model.Symbol toSymbol(CSVRecord record) {
        com.sourcegraph.common.model.Symbol s = new com.sourcegraph.common.model.Symbol();
        s.setPath(record.get(1));
        s.setFile(record.get(2));
        Range r = toRange(record);
        s.setRange(r);
        s.setName(record.get(7));
        s.setKind(record.get(8));
        s.setUnit(record.get(9));
        s.setTitle(record.get(10));
        s.setDocHtml(record.get(11));
        return s;
    }

    /**
     * Transforms CSV record to range
     *
     * @param record record to transform
     * @return range object
     */
    public static Range toRange(CSVRecord record) {
        Range r = new Range();
        r.setFile(record.get(2));
        r.setStartLine(Integer.parseInt(record.get(3)));
        r.setStartCharacter(Integer.parseInt(record.get(4)));
        r.setEndLine(Integer.parseInt(record.get(5)));
        r.setEndCharacter(Integer.parseInt(record.get(6)));
        return r;
    }

    /**
     * @return CSV parser to parse index file
     */
    private CSVParser getIndex() throws IOException {

        return CSVFormat.DEFAULT.parse(new BufferedReader(new InputStreamReader(
                new FileInputStream(getIndexFile()),
                "utf-8")));
    }

    /**
     * @return file containing CSV index
     */
    private File getIndexFile() {
        File directory = config.getFile().toFile().getParentFile();
        return new File(directory, ".index");
    }

    /**
     * @return file containing CSV index (to write)
     */
    private File getIndexWriteFile() throws IOException {
        return Files.createTempFile(config.getFile().getParent(), "index", "idx").toFile();
    }

    /**
     * Thread safe CSV printer allows multiple threads to print records to output file (we don't care about records order)
     */
    private static class ThreadSafeCSVPrinter {

        private CSVPrinter delegate;

        ThreadSafeCSVPrinter(CSVPrinter delegate) {
            this.delegate = delegate;
        }

        synchronized void printRecord(Iterable<?> values) throws IOException {
            delegate.printRecord(values);
        }

        synchronized void close() throws IOException {
            delegate.close();
        }

        synchronized void flush() throws IOException {
            delegate.flush();
        }
    }

    /**
     * Traverses AST trees and collects references and definitions
     */
    private class Indexer extends TreeScanner {

        private JCTree.JCCompilationUnit tree;

        private ThreadSafeCSVPrinter printer;

        private Trees trees;

        Indexer(Trees trees, ThreadSafeCSVPrinter printer) {
            this.trees = trees;
            this.printer = printer;
        }

        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            this.tree = tree;
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
                Range range = range(tree, this.tree);
                com.sourcegraph.common.model.Symbol s = new com.sourcegraph.common.model.Symbol();
                s.setRange(range);
                s.setName(symbol.getQualifiedName().toString());
                s.setPath(key);
                s.setKind(symbol.getKind().name().toLowerCase());
                s.setDocHtml(this.tree.docComments.getCommentText(tree));
                s.setFile(root.toUri().relativize(this.tree.getSourceFile().toUri()).toString());
                s.setUnitType(UNIT_TYPE);
                s.setUnit(config.unit);

                Collection<Object> record = new LinkedList<>();
                record.add(DEF);
                record.add(s.getPath());
                record.add(s.getFile());
                record.add(s.getRange().getStartLine());
                record.add(s.getRange().getStartCharacter());
                record.add(s.getRange().getEndLine());
                record.add(s.getRange().getEndCharacter());
                record.add(s.getName());
                record.add(s.getKind());
                record.add(config.unit);
                record.add(getTitle(this.tree, tree, symbol));
                record.add(s.getDocHtml());
                record.add(isExported(symbol));

                try {
                    printer.printRecord(record);
                } catch (IOException e) {
                    LOGGER.warn("Cannot record declaration", e);
                }
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

                Collection<Object> record = new LinkedList<>();
                record.add(REF);
                record.add(key);

                Range range = range(tree, this.tree);

                record.add(range.getFile());
                record.add(range.getStartLine());
                record.add(range.getStartCharacter());
                record.add(range.getEndLine());
                record.add(range.getEndCharacter());

                if (externalOrigin == null) {
                    record.add(StringUtils.EMPTY);
                } else {
                    OriginEntry originEntry = Origin.getRepository(externalOrigin, config);
                    if (originEntry!= null) {
                        record.add(originEntry.repo);
                        record.add(originEntry.unit);
                    } else {
                        record.add(UNKNOWN_REPOSITORY);
                        record.add(UNKNOWN_REPOSITORY);
                    }
                }

                try {
                    printer.printRecord(record);
                } catch (IOException e) {
                    LOGGER.warn("Cannot record definition", e);
                }

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
                int start = (int) trees.getSourcePositions().getStartPosition(compilationUnit, tree);
                int end = (int) trees.getSourcePositions().getEndPosition(compilationUnit, tree);

                // If symbol is a class, offset points to 'class' keyword, not name
                // Find the name by searching the text of the source, starting at the 'class' keyword
                if (tree instanceof JCTree.JCClassDecl) {
                    Symbol.ClassSymbol symbol = ((JCTree.JCClassDecl) tree).sym;
                    start = offset(compilationUnit, symbol, start);
                    end = start + symbol.name.length();
                } else if (tree instanceof JCTree.JCMethodDecl) {
                    Symbol.MethodSymbol symbol = ((JCTree.JCMethodDecl) tree).sym;
                    start = offset(compilationUnit, symbol, start);
                    end = start + symbol.name.length();
                } else if (tree instanceof JCTree.JCVariableDecl) {
                    Symbol.VarSymbol symbol = ((JCTree.JCVariableDecl) tree).sym;
                    start = offset(compilationUnit, symbol, start);
                    end = start + symbol.name.length();
                }

                Range range = findRange(compilationUnit.getSourceFile(), start, end);
                URI full = compilationUnit.getSourceFile().toUri();
                String uri = root.toUri().relativize(full).toString();
                range.setFile(uri);
                return range;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @param symbol
         * @return true if we should keep index for a given symbol's kind
         */
        private boolean shouldIndex(Symbol symbol) {
            ElementKind kind = symbol.getKind();

            switch (kind) {
                case ENUM:
                case ANNOTATION_TYPE:
                case INTERFACE:
                case ENUM_CONSTANT:
                case FIELD:
                case METHOD:
                case PARAMETER:
                case EXCEPTION_PARAMETER:
                case LOCAL_VARIABLE:
                case TYPE_PARAMETER:
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

        private boolean isExported(Symbol symbol) {
            ElementKind kind = symbol.getKind();

            switch (kind) {
                case PARAMETER:
                case EXCEPTION_PARAMETER:
                case LOCAL_VARIABLE:
                case TYPE_PARAMETER:
                    return false;
                default:
                    return true;
            }
        }

        private int offset(JCTree.JCCompilationUnit compilationUnit,
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
        private int indexOf(CharSequence source, CharSequence target, int fromIndex) {
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
         * @param tree        compilation unit
         * @param foundTree   found AST node
         * @param foundSymbol found symbol
         * @return hover content for specific symbol
         */
        private String getTitle(JCTree.JCCompilationUnit tree,
                               JCTree foundTree,
                               Symbol foundSymbol) {

            switch (foundSymbol.getKind()) {
                case PACKAGE:
                    return "package " + foundSymbol.getQualifiedName();
                case ENUM:
                    return "enum " + foundSymbol.getQualifiedName();
                case CLASS:
                    return "class " + foundSymbol.getQualifiedName();
                case ANNOTATION_TYPE:
                    return "@interface " + foundSymbol.getQualifiedName();
                case INTERFACE:
                    return "interface " + foundSymbol.getQualifiedName();
                case METHOD:
                case CONSTRUCTOR:
                case STATIC_INIT:
                case INSTANCE_INIT:
                    Symbol.MethodSymbol method = (Symbol.MethodSymbol) foundSymbol;
                    String signature = ShortTypePrinter.methodSignature(method);
                    String returnType = ShortTypePrinter.print(method.getReturnType());

                    return returnType + " " + signature;
                case PARAMETER:
                case LOCAL_VARIABLE:
                case EXCEPTION_PARAMETER:
                case ENUM_CONSTANT:
                case FIELD:
                    return ShortTypePrinter.print(foundSymbol.type);
                case TYPE_PARAMETER:
                case OTHER:
                case RESOURCE_VARIABLE:
                    return StringUtils.EMPTY;
            }

            return StringUtils.EMPTY;
        }

    }

}