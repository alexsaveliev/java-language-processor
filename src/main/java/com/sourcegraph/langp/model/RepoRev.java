package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class RepoRev {

    @JsonProperty(value = "Repo")
    @NotNull
    private String repo;

    @JsonProperty(value = "Commit")
    @NotNull
    private String commit;

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }
}
