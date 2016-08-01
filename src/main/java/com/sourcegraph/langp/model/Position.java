package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Position extends RepoRev {

    @JsonProperty(value = "File")
    private String file;

    @JsonProperty(value = "Line")
    private int line;

    @JsonProperty(value = "Character")
    private int character;

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getCharacter() {
        return character;
    }

    public void setCharacter(int character) {
        this.character = character;
    }
}
