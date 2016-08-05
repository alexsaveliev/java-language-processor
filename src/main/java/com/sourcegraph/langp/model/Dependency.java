package com.sourcegraph.langp.model;

public class Dependency {

    public String groupID;
    public String artifactID;
    public String version;
    public String file;

    public Dependency() {
    }

    public Dependency(String groupID, String artifactID, String version, String file) {
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.version = version;
        this.file = file;
    }
}

