package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class HoverContent {

    @JsonProperty(value="Type")
    private String type;

    @JsonProperty(value="Value")
    private String value;

    public HoverContent() {
    }

    public HoverContent(String value) {
        setValue(value);
        setType("java");
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
