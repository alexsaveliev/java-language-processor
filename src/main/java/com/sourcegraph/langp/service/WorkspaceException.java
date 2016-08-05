package com.sourcegraph.langp.service;

/**
 * Indicates errors happened while configuring workspace
 */
public class WorkspaceException extends Exception {

    public WorkspaceException(String message) {
        super(message);
    }

    public WorkspaceException(Throwable cause) {
        super(cause);
    }

    public WorkspaceException(String message, Throwable cause) {
        super(message, cause);
    }

}
