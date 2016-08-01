package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Error {

    @JsonProperty(value="Error")
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
