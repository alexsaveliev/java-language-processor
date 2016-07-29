package com.sourcegraph.langp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;

public class WorkspaceException extends Exception {

    public WorkspaceException(String message) {
        super(message);
    }

    public WorkspaceException(String message, Throwable cause) {
        super(message, cause);
    }

}
