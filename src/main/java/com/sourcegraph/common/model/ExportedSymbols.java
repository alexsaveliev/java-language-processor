package com.sourcegraph.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public class ExportedSymbols {

    @JsonProperty(value="Symbols")
    private Collection<Symbol> symbols;

    public Collection<Symbol> getSymbols() {
        return symbols;
    }

    public void setSymbols(Collection<Symbol> symbols) {
        this.symbols = symbols;
    }
}
