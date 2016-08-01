package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DefSpec extends RepoRev {

    @JsonProperty(value="Unit")
    private String unit;

    @JsonProperty(value="UnitType")
    private String unitType;

    @JsonProperty(value="Path")
    private String path;

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
