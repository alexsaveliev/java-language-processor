package com.sourcegraph.common.service;

/**
 * Indicates that there is no definition at the given position
 */
public class NoDefinitionFoundException extends Exception {

    public NoDefinitionFoundException() {
        super("No definition found");
    }

}
