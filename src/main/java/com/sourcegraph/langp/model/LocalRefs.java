package com.sourcegraph.langp.model;

import java.util.Collection;

public class LocalRefs {

    private Collection<Position> refs;

    public Collection<Position> getRefs() {
        return refs;
    }

    public void setRefs(Collection<Position> refs) {
        this.refs = refs;
    }
}
