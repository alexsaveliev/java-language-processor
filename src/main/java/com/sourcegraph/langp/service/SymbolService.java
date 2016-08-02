package com.sourcegraph.langp.service;

import com.sourcegraph.langp.javac.ShortTypePrinter;
import com.sourcegraph.langp.javac.SymbolIndex;
import com.sourcegraph.langp.javac.SymbolUnderCursorVisitor;
import com.sourcegraph.langp.javac.Workspace;
import com.sourcegraph.langp.model.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

@Service
public class SymbolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolService.class);

    @Autowired
    private WorkspaceService workspaceService;

    private Map<String, Long> offsets = new HashMap<>();

    public Hover hover(Position position)
            throws WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException {

        LOGGER.info("Hover {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Path root = workspaceService.getWorkspace(position.getRepo(), position.getCommit()).toPath();
        Workspace workspace = Workspace.getInstance(root);

        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }


        Hover result = new Hover();

        try {
            JCTree.JCCompilationUnit tree = workspace.getTree(sourceFile);
            JavaFileObject file = workspace.getFile(sourceFile);
            long cursor = findOffset(file, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file,
                    cursor,
                    workspace.findCompiler(sourceFile).context);
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
            WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {

        LOGGER.info("Definition {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Path root = workspaceService.getWorkspace(position.getRepo(), position.getCommit()).toPath();
        Workspace workspace = Workspace.getInstance(root);

        Path sourceFile = root.resolve(position.getFile());
        if (!sourceFile.startsWith(root)) {
            throw new SymbolException("File is outside of workspace");
        }
        if (!sourceFile.toFile().isFile()) {
            throw new SymbolException("File does not exist");
        }

        try {
            JCTree.JCCompilationUnit tree = workspace.getTree(sourceFile);
            JavaFileObject file = workspace.getFile(sourceFile);
            long cursor = findOffset(file, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file,
                    cursor,
                    workspace.findCompiler(sourceFile).context);
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
        } catch (NoDefinitionFoundException e) {
            throw e;
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
    }

    public LocalRefs localRefs(Position position) throws
            WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {

        LOGGER.info("Local refs {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Path root = workspaceService.getWorkspace(position.getRepo(), position.getCommit()).toPath();
        Workspace workspace = Workspace.getInstance(root);

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
            JCTree.JCCompilationUnit tree = workspace.getTree(sourceFile);
            JavaFileObject file = workspace.getFile(sourceFile);
            long cursor = findOffset(file, position.getLine(), position.getCharacter());
            SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file,
                    cursor,
                    workspace.findCompiler(sourceFile).context);
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
            throws WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException {

        LOGGER.info("External refs {}:{}",
                repoRev.getRepo(),
                repoRev.getCommit());

        Path root = workspaceService.getWorkspace(repoRev.getRepo(), repoRev.getCommit()).toPath();

        try {
            Workspace workspace = Workspace.getInstance(root);
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

    private long findOffset(JavaFileObject file, int targetLine, int targetCharacter) {
        String id = file.getName() + ':' + targetLine + ':' + targetCharacter;
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

    private long computeOffset(JavaFileObject file,
                               int targetLine,
                               int targetCharacter) throws IOException {
        try (Reader in = file.openReader(true)) {
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
}