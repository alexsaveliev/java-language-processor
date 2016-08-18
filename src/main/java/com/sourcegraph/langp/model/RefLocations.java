package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public class RefLocations {

    @JsonProperty(value="Refs")
    private Collection<Range> refs;

    public Collection<Range> getRefs() {
        return refs;
    }

    public void setRefs(Collection<Range> refs) {
        this.refs = refs;
    }
}
