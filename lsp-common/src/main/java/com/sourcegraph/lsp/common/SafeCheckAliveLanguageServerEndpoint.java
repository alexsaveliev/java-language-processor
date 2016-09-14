package com.sourcegraph.lsp.common;

import io.typefox.lsapi.services.transport.server.LanguageServerEndpoint;
import org.apache.commons.lang3.SystemUtils;

import java.util.concurrent.ExecutorService;

/**
 * Wraps standard LanguageServerEndpoint to disable "check alive" functionality on Windows
 * where there is no "kill" command
 */
public class SafeCheckAliveLanguageServerEndpoint extends LanguageServerEndpoint {

    public SafeCheckAliveLanguageServerEndpoint(io.typefox.lsapi.services.LanguageServer delegate,
                                                ExecutorService executorService) {
        super(delegate, executorService);
    }

    @Override
    protected void checkAlive(Integer processId) {
        // implementation uses "kill" which is not available on Windows
        if (SystemUtils.IS_OS_WINDOWS) {
            return;
        }
        super.checkAlive(processId);
    }
}
