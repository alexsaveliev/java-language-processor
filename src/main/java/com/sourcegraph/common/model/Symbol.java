package com.sourcegraph.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class Symbol extends DefSpec {

    @JsonProperty(value="Name")
    private String name;

    @JsonProperty(value="Kind")
    private String kind;

    @JsonProperty(value="File")
    private String file;

    @JsonProperty(value="DocHTML")
    private String docHtml;

    @JsonIgnore
    private Range range;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getDocHtml() {
        return docHtml;
    }

    public void setDocHtml(String docHtml) {
        this.docHtml = docHtml;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }
}
