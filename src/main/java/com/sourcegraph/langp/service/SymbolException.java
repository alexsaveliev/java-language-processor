package com.sourcegraph.langp.service;

public class SymbolException extends Exception {

    public SymbolException(String message) {
        super(message);
    }

    public SymbolException(String message, Throwable cause) {
        super(message, cause);
    }

}
