package com.sourcegraph.langp.service;

/**
 * Indicates that workspace is being prepared (cloned, configured, indexed)
 */
public class WorkspaceBeingPreparedException extends Exception {

    public WorkspaceBeingPreparedException() {
        super();
    }
}
