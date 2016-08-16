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
            LOGGER.info("An error occurred while looking for hover {}/{} {}:{}",
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
                Range range = index.findSymbol(visitor.found.get());
                if (range == null) {
                    throw new NoDefinitionFoundException();
                }
                return range;
            } else {
                throw new NoDefinitionFoundException();
            }
        } catch (NoDefinitionFoundException | SymbolException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.info("An error occurred while looking for definition {}/{} {}:{}",
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
    public LocalRefs localRefs(Position position) throws
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

        LocalRefs ret = new LocalRefs();
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
                Range range = index.findSymbol(visitor.found.get());
                if (range == null) {
                    throw new NoDefinitionFoundException();
                }
                ret.getRefs().add(range);
                index.references(visitor.found.get()).forEach(ret.getRefs()::add);
                return ret;
            } else {
                throw new NoDefinitionFoundException();
            }
        } catch (NoDefinitionFoundException | SymbolException | WorkspaceException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.info("An error occurred while looking for local refs {}:{}/{} {}:{}",
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
            LOGGER.info("An error occurred while looking for external refs {}:{}",
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
            ret.setSymbols(workspace.getExportedSymbols());
            return ret;
        } catch (Exception e) {
            LOGGER.info("An error occurred while looking for exported symbols {}:{}",
                    repoRev.getRepo(),
                    repoRev.getCommit(),
                    e);
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