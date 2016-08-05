package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

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

    public RepoRev() {
    }

    public RepoRev(String repo, String commit) {
        setRepo(repo);
        setCommit(commit);
    }

    @Override
    public int hashCode() {
        int ret = repo == null ? 0 : repo.hashCode();
        ret = ret * 13 + (commit == null ? 0 : commit.hashCode());
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof RepoRev)) {
            return false;
        }
        RepoRev that = (RepoRev) o;
        return StringUtils.equals(this.getRepo(), that.getRepo()) &&
                StringUtils.equals(this.getCommit(), that.getCommit());
    }

}
