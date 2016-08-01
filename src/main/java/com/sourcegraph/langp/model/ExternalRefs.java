package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public class ExternalRefs {

    @JsonProperty(value="Defs")
    private Collection<DefSpec> defs;

    public Collection<DefSpec> getDefs() {
        return defs;
    }

    public void setDefs(Collection<DefSpec> defs) {
        this.defs = defs;
    }
}
