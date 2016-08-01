package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public class Hover {

    @JsonProperty(value="Contents")
    private Collection<HoverContent> contents;

    public Collection<HoverContent> getContents() {
        return contents;
    }

    public void setContents(Collection<HoverContent> contents) {
        this.contents = contents;
    }
}
