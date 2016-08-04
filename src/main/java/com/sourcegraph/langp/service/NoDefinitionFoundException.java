package com.sourcegraph.langp.service;

public class NoDefinitionFoundException extends Exception {

    public NoDefinitionFoundException() {
        super("No definition found");
    }

}
