package com.sourcegraph.common.javac;

public class OriginEntry {

    public String repo;
    public String unit;

    public OriginEntry(String repo, String unit) {
        this.repo = repo;
        this.unit = unit;
    }
}
