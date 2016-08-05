package com.sourcegraph.langp.javac;

import java.nio.file.Path;

/**
 * Indicates that there is no configuration for a given file
 */
class NoJavaConfigException extends RuntimeException {
    public NoJavaConfigException(Path forFile) {
        this("Can't find configuration file for " + forFile);
    }
    
    public NoJavaConfigException(String message) {
        super(message);
    }
}