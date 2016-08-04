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

import java.io.File;
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

@Service
public class SymbolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolService.class);

    @Value("${workspace.get.timeout:250}")
    private long timeout;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private WorkspaceService workspaceService;

    private Map<String, Long> offsets = new HashMap<>();

    public Hover hover(Position position)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException {

        LOGGER.info("Hover {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Path root = getWorkspace(position.getRepo(), position.getCommit()).toPath();
        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }


        Hover result = new Hover();

        try {
            JCTree.JCCompilationUnit tree = getTree(workspace, sourceFile);
            if (tree == null) {
                throw new SymbolException("File does not exist");
            }
            long cursor = findOffset(sourceFile, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = workspace.getSymbolUnderCursorVisitor(sourceFile, cursor);
            tree.accept(visitor);

            if (visitor.found.isPresent()) {
                Symbol symbol = visitor.found.get();
                Collection<HoverContent> contents = new LinkedList<>();

                String text = tree.docComments.getCommentText(visitor.foundTree);
                if (text != null) {
                    contents.add(new HoverContent(text));
                } else {

                    switch (symbol.getKind()) {
                        case PACKAGE:
                            contents.add(new HoverContent("package " + symbol.getQualifiedName()));

                            break;
                        case ENUM:
                            contents.add(new HoverContent("enum " + symbol.getQualifiedName()));

                            break;
                        case CLASS:
                            contents.add(new HoverContent("class " + symbol.getQualifiedName()));

                            break;
                        case ANNOTATION_TYPE:
                            contents.add(new HoverContent("@interface " + symbol.getQualifiedName()));

                            break;
                        case INTERFACE:
                            contents.add(new HoverContent("interface " + symbol.getQualifiedName()));

                            break;
                        case METHOD:
                        case CONSTRUCTOR:
                        case STATIC_INIT:
                        case INSTANCE_INIT:
                            Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                            String signature = ShortTypePrinter.methodSignature(method);
                            String returnType = ShortTypePrinter.print(method.getReturnType());

                            contents.add(new HoverContent(returnType + " " + signature));

                            break;
                        case PARAMETER:
                        case LOCAL_VARIABLE:
                        case EXCEPTION_PARAMETER:
                        case ENUM_CONSTANT:
                        case FIELD:
                            contents.add(new HoverContent(ShortTypePrinter.print(symbol.type)));

                            break;
                        case TYPE_PARAMETER:
                        case OTHER:
                        case RESOURCE_VARIABLE:
                            break;
                    }
                }
                result.setContents(contents);
            }
        } catch (Exception e) {
            LOGGER.info("An error occurred while looking for hover {}:{}/{} {}:{}",
                    position.getRepo(),
                    position.getCommit(),
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
        return result;
    }

    public Range definition(Position position) throws
            WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {

        LOGGER.info("Definition {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Path root = getWorkspace(position.getRepo(), position.getCommit()).toPath();
        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }

        try {
            JCTree.JCCompilationUnit tree = getTree(workspace, sourceFile);
            if (tree == null) {
                throw new SymbolException("File does not exist");
            }
            long cursor = findOffset(sourceFile, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = workspace.getSymbolUnderCursorVisitor(sourceFile, cursor);
            tree.accept(visitor);

            if (visitor.found.isPresent()) {
                SymbolIndex index = workspace.findIndex(sourceFile);
                if (index == null) {
                    throw new NoDefinitionFoundException();
                }
                Range range = index.findSymbol(visitor.found.get());
                if (range == null) {
                    throw new NoDefinitionFoundException();
                }
                return range;
            } else {
                throw new NoDefinitionFoundException();
            }
        } catch (NoDefinitionFoundException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.info("An error occurred while looking for definition {}:{}/{} {}:{}",
                    position.getRepo(),
                    position.getCommit(),
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    public LocalRefs localRefs(Position position) throws
            WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {

        LOGGER.info("Local refs {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Path root = getWorkspace(position.getRepo(), position.getCommit()).toPath();
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
            JCTree.JCCompilationUnit tree = getTree(workspace, sourceFile);
            if (tree == null) {
                throw new SymbolException("File does not exist");
            }

            long cursor = findOffset(sourceFile, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = workspace.getSymbolUnderCursorVisitor(sourceFile, cursor);
            tree.accept(visitor);

            if (visitor.found.isPresent()) {
                SymbolIndex index = workspace.findIndex(sourceFile);
                if (index == null) {
                    throw new NoDefinitionFoundException();
                }
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
        } catch (NoDefinitionFoundException e) {
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

    private File getWorkspace(String repo, String commit)
            throws WorkspaceBeingPreparedException, WorkspaceException {
        Future<File> workspaceRoot = repositoryService.getRepository(repo, commit);
        try {
            return workspaceRoot.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("An error occurred", e);
            throw new WorkspaceException(e);
        } catch (TimeoutException e) {
            throw new WorkspaceBeingPreparedException();
        }
    }

    private JCTree.JCCompilationUnit getTree(Workspace workspace, Path path)
            throws WorkspaceBeingPreparedException, WorkspaceException {
        Future<JCTree.JCCompilationUnit> future = workspace.getTree(path);
        if (future == null) {
            throw new WorkspaceBeingPreparedException();}
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("An error occurred", e);
            throw new WorkspaceException(e);
        } catch (TimeoutException e) {
            throw new WorkspaceBeingPreparedException();
        }
    }
}