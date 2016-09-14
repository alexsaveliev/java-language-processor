package com.sourcegraph.lsp;

import com.sourcegraph.common.model.Position;
import com.sourcegraph.common.model.Range;
import com.sourcegraph.common.model.RefLocations;
import com.sourcegraph.common.service.*;
import com.sourcegraph.common.util.PathUtil;
import io.typefox.lsapi.*;
import io.typefox.lsapi.impl.*;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
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

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SymbolService symbolService;

    private File workspace;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {

        LOGGER.info("Initialize {}", params.getRootPath());

        if (params.getRootPath() != null) {
            try {
                File root = new File(params.getRootPath());
                if (configurationService.isConfigured(root)) {
                    workspace = root;
                } else {
                    workspace = configurationService.configure(root).get();
                }
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Unable to initialize workspace {}", params.getRootPath());
            }
        }

        InitializeResultImpl result = new InitializeResultImpl();
        ServerCapabilitiesImpl c = new ServerCapabilitiesImpl();
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

                try {
                    Position pos = getPosition(params);
                    com.sourcegraph.common.model.Hover hover = symbolService.hover(workspace.toPath(), pos);
                    HoverImpl ret = new HoverImpl();
                    List<MarkedStringImpl> contents = new LinkedList<>();
                    contents.add(new MarkedStringImpl("java", hover.getTitle()));
                    if (!StringUtils.isEmpty(hover.getDocHtml())) {
                        contents.add(new MarkedStringImpl("text/html", hover.getDocHtml()));
                    }
                    ret.setContents(contents);
                    return CompletableFuture.completedFuture(ret);
                } catch (NoDefinitionFoundException | SymbolException | WorkspaceBeingPreparedException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams textDocumentPositionParams) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams params) {
                try {
                    Position pos = getPosition(params);
                    Range range = symbolService.definition(workspace.toPath(), pos).getRange();
                    LocationImpl ret = new LocationImpl();
                    ret.setUri(toUri(range.getFile()));
                    RangeImpl r = new RangeImpl();
                    r.setStart(new PositionImpl(range.getStartLine(), range.getStartCharacter()));
                    r.setEnd(new PositionImpl(range.getEndLine(), range.getEndCharacter()));
                    ret.setRange(r);
                    return CompletableFuture.completedFuture(Collections.singletonList(ret));
                } catch (SymbolException | NoDefinitionFoundException | WorkspaceBeingPreparedException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
                RefLocations ret;
                try {
                    Position pos = getPosition(params);
                    ret = symbolService.localRefs(workspace.toPath(), pos);
                } catch (SymbolException | NoDefinitionFoundException | URISyntaxException e) {
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

            /**
             * @param params LSP text document position parameters
             * @return parameters converted to internal structure
             */
            private Position getPosition(TextDocumentPositionParams params) throws URISyntaxException {
                Position pos = new Position();
                String uri = params.getTextDocument().getUri();
                URI u = new URI(uri);
                if (u.isAbsolute()) {
                    if (!"file".equals(u.getScheme())) {
                        throw new RuntimeException(uri + " scheme is not supported");
                    }
                    if (u.getHost() != null) {
                        throw new RuntimeException(uri + " with not-empty host is not supported");
                    }
                    Path file = new File(u.getPath()).toPath().toAbsolutePath();
                    pos.setFile(PathUtil.normalize(workspace.toPath().relativize(file).toString()));
                } else {
                    pos.setFile(params.getTextDocument().getUri());
                }
                pos.setLine(params.getPosition().getLine());
                pos.setCharacter(params.getPosition().getCharacter());
                return pos;
            }

            private Location getLocation(Range range) {
                LocationImpl ret = new LocationImpl();
                ret.setUri(toUri(range.getFile()));
                RangeImpl r = new RangeImpl();
                r.setStart(new PositionImpl(range.getStartLine(), range.getStartCharacter()));
                r.setEnd(new PositionImpl(range.getEndLine(), range.getEndCharacter()));
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

    private String toUri(String file) {
        StringBuilder ret = new StringBuilder();
        ret.append("file://");
        if (SystemUtils.IS_OS_WINDOWS) {
            ret.append('/');
        }
        ret.append(PathUtil.normalize(new File(workspace, file).getAbsolutePath()));
        return ret.toString();
    }
}
