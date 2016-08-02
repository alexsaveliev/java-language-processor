package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Range extends RepoRev {

    @JsonProperty(value="File")
    private String file;

    @JsonProperty(value="StartLine")
    private int startLine;

    @JsonProperty(value="StartColumn")
    private int startColumn;

    @JsonProperty(value="EndLine")
    private int endLine;

    @JsonProperty(value="EndColumn")
    private int endColumn;

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

    public int getStartColumn() {
        return startColumn;
    }

    public void setStartColumn(int startColumn) {
        this.startColumn = startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }
}
