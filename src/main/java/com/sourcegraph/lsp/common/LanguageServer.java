package com.sourcegraph.lsp.common;

import com.sourcegraph.common.model.Position;
import com.sourcegraph.common.model.Range;
import com.sourcegraph.common.model.RefLocations;
import com.sourcegraph.common.service.*;
import io.typefox.lsapi.*;
import io.typefox.lsapi.impl.*;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * LanguageServer handles subset of LSP requests (textDocument/hover, textDocument/definition, textDocument/references)
 */
@Component
public class LanguageServer implements io.typefox.lsapi.services.LanguageServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServer.class);

    /**
     * Root directory where all repositories are located
     */
    @Value("${workspace:${SGPATH:${user.home}/.sourcegraph}/workspace/java}")
    private String root;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SymbolService symbolService;

    private File workspace;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {

        LOGGER.info("Initialize {}", params.getRootPath());

        if (params.getRootPath() == null) {
            throw new IllegalArgumentException("Missing root path");
        }

        try {
            File root = new File(this.root, params.getRootPath());
            workspace = configurationService.configure(root).get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Unable to initialize workspace {}", params.getRootPath());
        }

        InitializeResultImpl result = new InitializeResultImpl();
        ServerCapabilitiesImpl c = new ServerCapabilitiesImpl();
        c.setTextDocumentSync(TextDocumentSyncKind.None);
        c.setHoverProvider(true);
        c.setDefinitionProvider(true);
        c.setReferencesProvider(true);
        c.setWorkspaceSymbolProvider(true);
        result.setCapabilities(c);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void exit() {
    }

    @Override
    public void onTelemetryEvent(Consumer<Object> consumer) {
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return new TextDocumentService() {
            @Override
            public CompletableFuture<CompletionList> completion(TextDocumentPositionParams textDocumentPositionParams) {
                return null;
            }

            @Override
            public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem completionItem) {
                return null;
            }

            @Override
            public CompletableFuture<Hover> hover(TextDocumentPositionParams params) {

                Position pos = getPosition(params);
                try {
                    com.sourcegraph.common.model.Hover hover = symbolService.hover(workspace.toPath(), pos);
                    HoverImpl ret = new HoverImpl();
                    List<MarkedStringImpl> contents = new LinkedList<>();
                    contents.add(new MarkedStringImpl("java", hover.getTitle()));
                    if (!StringUtils.isEmpty(hover.getDocHtml())) {
                        contents.add(new MarkedStringImpl("text/html", hover.getDocHtml()));
                    }
                    ret.setContents(contents);
                    return CompletableFuture.completedFuture(ret);
                } catch (NoDefinitionFoundException | SymbolException | WorkspaceBeingPreparedException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams textDocumentPositionParams) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams params) {
                Position pos = getPosition(params);
                try {
                    Range range = symbolService.definition(workspace.toPath(), pos);
                    LocationImpl ret = new LocationImpl();
                    ret.setUri(range.getFile());
                    RangeImpl r = new RangeImpl();
                    r.setStart(new PositionImpl(range.getStartLine() + 1, range.getStartCharacter() + 1));
                    r.setEnd(new PositionImpl(range.getEndLine() + 1, range.getEndCharacter() + 1));
                    ret.setRange(r);
                    return CompletableFuture.completedFuture(Collections.singletonList(ret));
                } catch (SymbolException | NoDefinitionFoundException | WorkspaceBeingPreparedException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
                Position pos = getPosition(params);
                RefLocations ret;
                try {
                    ret = symbolService.localRefs(pos);
                } catch (WorkspaceException | SymbolException | NoDefinitionFoundException e) {
                    throw new RuntimeException(e);
                }

                List<Location> refs = new LinkedList<>();
                for (Range range : ret.getRefs()) {
                    refs.add(getLocation(range));
                }
                return CompletableFuture.completedFuture(refs);
            }

            @Override
            public CompletableFuture<DocumentHighlight> documentHighlight(TextDocumentPositionParams textDocumentPositionParams) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams documentSymbolParams) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams codeActionParams) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams codeLensParams) {
                return null;
            }

            @Override
            public CompletableFuture<CodeLens> resolveCodeLens(CodeLens codeLens) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams documentFormattingParams) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams documentRangeFormattingParams) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams documentOnTypeFormattingParams) {
                return null;
            }

            @Override
            public CompletableFuture<WorkspaceEdit> rename(RenameParams renameParams) {
                return null;
            }

            @Override
            public void didOpen(DidOpenTextDocumentParams didOpenTextDocumentParams) {

            }

            @Override
            public void didChange(DidChangeTextDocumentParams didChangeTextDocumentParams) {

            }

            @Override
            public void didClose(DidCloseTextDocumentParams didCloseTextDocumentParams) {

            }

            @Override
            public void didSave(DidSaveTextDocumentParams didSaveTextDocumentParams) {

            }

            @Override
            public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> consumer) {

            }

            private Position getPosition(TextDocumentPositionParams params) {
                Position pos = new Position();
                pos.setFile(params.getTextDocument().getUri());
                pos.setLine(params.getPosition().getLine() - 1);
                pos.setCharacter(params.getPosition().getCharacter() - 1);
                return pos;
            }

            private Location getLocation(Range range) {
                LocationImpl ret = new LocationImpl();
                ret.setUri(range.getFile());
                RangeImpl r = new RangeImpl();
                r.setStart(new PositionImpl(range.getStartLine() + 1, range.getStartCharacter() + 1));
                r.setEnd(new PositionImpl(range.getEndLine() + 1, range.getEndCharacter() + 1));
                ret.setRange(r);
                return ret;
            }
        };
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
                String parts[] = StringUtils.split(params.getQuery(), " ", 2);
                // TODO
                return null;
            }

            @Override
            public void didChangeConfiguraton(DidChangeConfigurationParams didChangeConfigurationParams) {

            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams didChangeWatchedFilesParams) {

            }
        };
    }

    @Override
    public WindowService getWindowService() {
        return new WindowService() {
            @Override
            public void onShowMessage(Consumer<MessageParams> consumer) {

            }

            @Override
            public void onShowMessageRequest(Consumer<ShowMessageRequestParams> consumer) {

            }

            @Override
            public void onLogMessage(Consumer<MessageParams> consumer) {

            }
        };
    }
}
