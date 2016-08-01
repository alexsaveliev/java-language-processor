package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepoRev {

    @JsonProperty(value = "Repo")
    private String repo;

    @JsonProperty(value = "Commit")
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
