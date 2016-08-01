package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public class LocalRefs {

    @JsonProperty(value="Refs")
    private Collection<Position> refs;

    public Collection<Position> getRefs() {
        return refs;
    }

    public void setRefs(Collection<Position> refs) {
        this.refs = refs;
    }
}
