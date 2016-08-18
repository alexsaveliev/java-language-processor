package com.sourcegraph.langp.service;

import com.sourcegraph.langp.javac.*;
import com.sourcegraph.langp.model.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provides foundSymbol resolution methods
 */
@Service
public class SymbolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolService.class);

    private static final String UNIT_TYPE = "JavaArtifact";
    private static final String UNIT = "Unit";

    /**
     * Wait no more than X milliseconds to acquire object
     */
    @Value("${workspace.get.timeout:250}")
    private long timeout;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private WorkspaceService workspaceService;

    private Map<String, Long> offsets = new HashMap<>();

    /**
     * @param root     workspace root
     * @param position symbol position
     * @return hover information for a given symbol
     * @throws SymbolException            if no foundSymbol is found
     * @throws NoDefinitionFoundException if there is no foundSymbol at specific position
     */
    public Hover hover(Path root,
                       Position position)
            throws NoDefinitionFoundException,
            SymbolException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Hover {}/{} {}:{}",
                root,
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }


        try {
            SymbolIndex index = getIndex(workspace, sourceFile);
            JCTree.JCCompilationUnit tree = getTree(index, sourceFile);
            if (tree == null) {
                throw new SymbolException("File does not exist");
            }
            long cursor = findOffset(sourceFile, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = index.getSymbolUnderCursorVisitor(sourceFile, cursor);
            tree.accept(visitor);

            if (visitor.found.isPresent()) {
                Symbol symbol = visitor.found.get();
                return getHover(tree, visitor.foundTree, symbol);
            } else {
                throw new NoDefinitionFoundException();
            }
        } catch (SymbolException | NoDefinitionFoundException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for hover {}/{} {}:{}",
                    root,
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root     workspace root
     * @param position symbol position
     * @return symbol's local definition
     * @throws SymbolException            if no foundSymbol is found
     * @throws NoDefinitionFoundException if there is no foundSymbol at specific position
     */
    public Range definition(Path root,
                            Position position) throws
            SymbolException,
            NoDefinitionFoundException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Definition {}/{} {}:{}",
                root,
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }

        try {
            SymbolIndex index = getIndex(workspace, sourceFile);
            JCTree.JCCompilationUnit tree = getTree(index, sourceFile);
            if (tree == null) {
                throw new SymbolException("File does not exist");
            }
            long cursor = findOffset(sourceFile, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = index.getSymbolUnderCursorVisitor(sourceFile, cursor);
            tree.accept(visitor);

            if (visitor.found.isPresent()) {
                com.sourcegraph.langp.model.Symbol symbol = index.findSymbol(visitor.found.get());
                if (symbol == null) {
                    throw new NoDefinitionFoundException();
                }
                return symbol.getRange();
            } else {
                throw new NoDefinitionFoundException();
            }
        } catch (NoDefinitionFoundException | SymbolException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for definition {}/{} {}:{}",
                    root,
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param position foundSymbol position
     * @return local references to specific foundSymbol
     * @throws WorkspaceException         if there was error configuring workspace
     * @throws SymbolException            if no foundSymbol is found
     * @throws NoDefinitionFoundException if there is no foundSymbol at specific position
     */
    public RefLocations localRefs(Position position) throws
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {

        LOGGER.info("Local refs {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Path root;
        try {
            root = repositoryService.getRepository(position.getRepo(), position.getCommit()).get().toPath();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("An error occurred while fetching repository {}@{}",
                    position.getRepo(), position.getCommit(), e);
            throw new WorkspaceException(e);
        }
        Workspace workspace = workspaceService.getWorkspace(root);
        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }

        RefLocations ret = new RefLocations();
        ret.setRefs(new LinkedList<>());
        try {
            workspace.computeIndexes();

            SymbolIndex index = getIndex(workspace, sourceFile);
            JCTree.JCCompilationUnit tree = getTree(index, sourceFile);

            if (tree == null) {
                throw new SymbolException("File does not exist");
            }

            long cursor = findOffset(sourceFile, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = index.getSymbolUnderCursorVisitor(sourceFile, cursor);
            tree.accept(visitor);

            if (visitor.found.isPresent()) {
                com.sourcegraph.langp.model.Symbol symbol = index.findSymbol(visitor.found.get());
                if (symbol == null) {
                    throw new NoDefinitionFoundException();
                }
                ret.getRefs().add(symbol.getRange());
                index.references(visitor.found.get()).forEach(ret.getRefs()::add);
                return ret;
            } else {
                throw new NoDefinitionFoundException();
            }
        } catch (NoDefinitionFoundException | SymbolException | WorkspaceException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for local refs {}:{}/{} {}:{}",
                    position.getRepo(),
                    position.getCommit(),
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param repoRev repository and revision
     * @return all external references from given repository
     * @throws WorkspaceException if there was error configuring workspace
     * @throws SymbolException    if no foundSymbol is found
     */
    public ExternalRefs externalRefs(RepoRev repoRev)
            throws WorkspaceException,
            SymbolException {

        LOGGER.info("External refs {}:{}",
                repoRev.getRepo(),
                repoRev.getCommit());

        Path root;
        try {
            root = repositoryService.getRepository(repoRev.getRepo(), repoRev.getCommit()).get().toPath();
        } catch (InterruptedException | ExecutionException e) {
            throw new WorkspaceException(e);
        }

        try {
            Workspace workspace = workspaceService.getWorkspace(root);
            workspace.computeIndexes();
            ExternalRefs ret = new ExternalRefs();
            ret.setDefs(workspace.getExternalDefs());
            return ret;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for external refs {}:{}",
                    repoRev.getRepo(),
                    repoRev.getCommit(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param repoRev repository and revision
     * @return all exported symbols from given repository
     * @throws WorkspaceException if there was error configuring workspace
     * @throws SymbolException    if no foundSymbol is found
     */
    public ExportedSymbols exportedSymbols(RepoRev repoRev)
            throws WorkspaceException,
            SymbolException {

        LOGGER.info("Exported symbols {}:{}",
                repoRev.getRepo(),
                repoRev.getCommit());

        Path root;
        try {
            root = repositoryService.getRepository(repoRev.getRepo(), repoRev.getCommit()).get().toPath();
        } catch (InterruptedException | ExecutionException e) {
            throw new WorkspaceException(e);
        }

        try {
            Workspace workspace = workspaceService.getWorkspace(root);
            workspace.computeIndexes();
            ExportedSymbols ret = new ExportedSymbols();
            Collection<com.sourcegraph.langp.model.Symbol> symbols = new LinkedList<>();
            for (com.sourcegraph.langp.model.Symbol symbol : workspace.getExportedSymbols()) {
                symbol.setRepo(repoRev.getRepo());
                symbol.setCommit(repoRev.getCommit());
                symbol.setUnit(UNIT);
                symbol.setUnitType(UNIT_TYPE);
                symbols.add(symbol);
            }
            ret.setSymbols(symbols);
            return ret;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for exported symbols {}:{}",
                    repoRev.getRepo(),
                    repoRev.getCommit(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root    workspace root
     * @param defSpec def spec
     * @return position of symbol denoted by a given spec
     * @throws SymbolException            if no symbol is found
     * @throws NoDefinitionFoundException if there is no symbol with specified spec found
     */
    public Position defSpecToPosition(Path root,
                                      DefSpec defSpec) throws SymbolException,
            NoDefinitionFoundException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Defspec to position {}:{}",
                root,
                defSpec.getPath());

        Workspace workspace = workspaceService.getWorkspace(root);

        try {
            return workspace.defSpecToPosition(defSpec);
        } catch (NoDefinitionFoundException | SymbolException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for defspec to path {}:{}",
                    root,
                    defSpec.getPath(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root workspace root
     * @param position  symbols' position
     * @return def spec of symbol at the given position
     * @throws SymbolException            if no symbol is found
     * @throws NoDefinitionFoundException if there is no symbol at the given position
     */
    public DefSpec positionToDefSpec(Path root,
                                     Position position) throws SymbolException,
            NoDefinitionFoundException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Position to def spec {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }

        try {

            SymbolIndex index = getIndex(workspace, sourceFile);
            JCTree.JCCompilationUnit tree = getTree(index, sourceFile);
            if (tree == null) {
                throw new SymbolException("File does not exist");
            }
            long cursor = findOffset(sourceFile, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = index.getSymbolUnderCursorVisitor(sourceFile, cursor);
            tree.accept(visitor);

            if (visitor.found.isPresent()) {
                com.sourcegraph.langp.model.Symbol symbol = index.findSymbol(visitor.found.get());
                if (symbol == null) {
                    throw new NoDefinitionFoundException();
                }
                symbol.setRepo(position.getRepo());
                symbol.setCommit(position.getCommit());
                symbol.setUnit(UNIT);
                symbol.setUnitType(UNIT_TYPE);
                return symbol;
            } else {
                throw new NoDefinitionFoundException();
            }
        } catch (NoDefinitionFoundException | SymbolException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for position to def spec {}:{}/{} {}:{}",
                    position.getRepo(),
                    position.getCommit(),
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(), e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param file            source file
     * @param targetLine      line
     * @param targetCharacter character
     * @return line:character mapped to character-based offset. Lookup is performed in the cache first
     */
    private long findOffset(Path file, int targetLine, int targetCharacter) {
        String id = file.toString() + ':' + targetLine + ':' + targetCharacter;
        Long offset = offsets.get(id);
        if (offset == null) {
            try {
                offsets.put(id, offset = computeOffset(file, targetLine, targetCharacter));
            } catch (IOException ex) {
                LOGGER.warn("Cannot compute offset for {}", file, ex);
                offsets.put(id, offset = 0L);
            }
        }
        return offset;
    }

    /**
     * Computes character-based offset for specific line and column
     *
     * @param file            source file
     * @param targetLine      line
     * @param targetCharacter character
     * @return line:character mapped to character-based offset
     * @throws IOException
     */
    private long computeOffset(Path file,
                               int targetLine,
                               int targetCharacter) throws IOException {
        try (Reader in = new FileReader(file.toFile())) {
            long offset = 0;
            int line = 0;
            int character = 0;

            while (line < targetLine) {
                int next = in.read();

                if (next < 0)
                    return offset;
                else {
                    offset++;

                    if (next == '\n')
                        line++;
                }
            }

            while (character < targetCharacter) {
                int next = in.read();

                if (next < 0)
                    return offset;
                else {
                    offset++;
                    character++;
                }
            }

            return offset;
        }
    }

    /**
     * Waits for N milliseconds to acquire tree object
     *
     * @param index foundSymbol index
     * @param path  file path
     * @return tree object if ready
     * @throws WorkspaceBeingPreparedException if tree object is being prepared
     * @throws WorkspaceException              if workspace configuration error occurred
     */
    private JCTree.JCCompilationUnit getTree(SymbolIndex index, Path path)
            throws WorkspaceBeingPreparedException, WorkspaceException {
        Future<JCTree.JCCompilationUnit> future = index.get(path.toUri());
        if (future == null) {
            throw new WorkspaceBeingPreparedException();
        }
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("An error occurred while fetching tree for {}", path, e);
            throw new WorkspaceException(e);
        } catch (TimeoutException e) {
            throw new WorkspaceBeingPreparedException();
        }
    }

    /**
     * Waits for N milliseconds to acquire index object
     *
     * @param workspace workspace
     * @param path      file path
     * @return index object if ready
     * @throws WorkspaceBeingPreparedException if index object is being prepared
     * @throws WorkspaceException              if workspace configuration error occurred
     */
    private SymbolIndex getIndex(Workspace workspace, Path path)
            throws WorkspaceBeingPreparedException, WorkspaceException {
        Future<SymbolIndex> future = workspace.findIndex(path);
        if (future == null) {
            throw new WorkspaceBeingPreparedException();
        }
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("An error occurred while fetching index for {}", path, e);
            throw new WorkspaceException(e);
        } catch (TimeoutException e) {
            throw new WorkspaceBeingPreparedException();
        }
    }

    /**
     * @param tree        compilation unit
     * @param foundTree   found AST node
     * @param foundSymbol found symbol
     * @return hover content for specific symbol
     */
    private Hover getHover(JCTree.JCCompilationUnit tree,
                           JCTree foundTree,
                           Symbol foundSymbol) {
        Hover ret = new Hover();

        String text = tree.docComments.getCommentText(foundTree);
        if (text != null) {
            ret.setDocHtml(text);
        }

        switch (foundSymbol.getKind()) {
            case PACKAGE:
                ret.setTitle("package " + foundSymbol.getQualifiedName());

                break;
            case ENUM:
                ret.setTitle("enum " + foundSymbol.getQualifiedName());

                break;
            case CLASS:
                ret.setTitle("class " + foundSymbol.getQualifiedName());

                break;
            case ANNOTATION_TYPE:
                ret.setTitle("@interface " + foundSymbol.getQualifiedName());

                break;
            case INTERFACE:
                ret.setTitle("interface " + foundSymbol.getQualifiedName());

                break;
            case METHOD:
            case CONSTRUCTOR:
            case STATIC_INIT:
            case INSTANCE_INIT:
                Symbol.MethodSymbol method = (Symbol.MethodSymbol) foundSymbol;
                String signature = ShortTypePrinter.methodSignature(method);
                String returnType = ShortTypePrinter.print(method.getReturnType());

                ret.setTitle(returnType + " " + signature);

                break;
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case ENUM_CONSTANT:
            case FIELD:
                ret.setTitle(ShortTypePrinter.print(foundSymbol.type));

                break;
            case TYPE_PARAMETER:
            case OTHER:
            case RESOURCE_VARIABLE:
                break;
        }

        return ret;
    }


}