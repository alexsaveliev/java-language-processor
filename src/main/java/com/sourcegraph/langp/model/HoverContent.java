package com.sourcegraph.langp.model;

public class HoverContent {

    private String type;
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
