package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Range extends RepoRev {

    @JsonProperty(value="File")
    private String file;

    @JsonProperty(value="StartLine")
    private int startLine;

    @JsonProperty(value="StartCharacter")
    private int startCharacter;

    @JsonProperty(value="EndLine")
    private int endLine;

    @JsonProperty(value="EndCharacter")
    private int endCharacter;

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getStartCharacter() {
        return startCharacter;
    }

    public void setStartCharacter(int startCharacter) {
        this.startCharacter = startCharacter;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndCharacter() {
        return endCharacter;
    }

    public void setEndCharacter(int endCharacter) {
        this.endCharacter = endCharacter;
    }
}
