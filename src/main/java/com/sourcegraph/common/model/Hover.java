package com.sourcegraph.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Hover {

    @JsonProperty(value="Title")
    private String title;

    @JsonProperty(value="DocHTML")
    private String docHtml;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocHtml() {
        return docHtml;
    }

    public void setDocHtml(String docHtml) {
        this.docHtml = docHtml;
    }
}
