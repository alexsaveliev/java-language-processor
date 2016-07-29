package com.sourcegraph.langp.model;

public class Error {

    private String error;

    public Error(String error) {
        setError(error);
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
