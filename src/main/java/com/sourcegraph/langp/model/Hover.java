package com.sourcegraph.langp.model;

import java.util.Collection;

public class Hover {

    private Collection<HoverContent> contents;

    public Collection<HoverContent> getContents() {
        return contents;
    }

    public void setContents(Collection<HoverContent> contents) {
        this.contents = contents;
    }
}
