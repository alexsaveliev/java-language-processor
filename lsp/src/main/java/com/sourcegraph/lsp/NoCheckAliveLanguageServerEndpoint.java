package com.sourcegraph.lsp;

import io.typefox.lsapi.services.transport.server.LanguageServerEndpoint;

import java.util.concurrent.ExecutorService;

/**
 * Wraps standard LanguageServerEndpoint to disable "check alive" functionality
 */
public class NoCheckAliveLanguageServerEndpoint extends LanguageServerEndpoint {

    public NoCheckAliveLanguageServerEndpoint(io.typefox.lsapi.services.LanguageServer delegate,
                                              ExecutorService executorService) {
        super(delegate, executorService);
    }

    @Override
    protected void checkAlive(Integer processId) {
        // NOOP
    }
}
