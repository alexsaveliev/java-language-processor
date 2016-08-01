package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PrepareSpec extends RepoRev {

    @JsonProperty(value="Force")
    private boolean force;

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
