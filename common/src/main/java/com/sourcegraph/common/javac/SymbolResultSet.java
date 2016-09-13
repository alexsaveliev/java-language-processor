package com.sourcegraph.common.javac;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.util.Iterator;

/**
 * Tracks definitions and referencs
 */
public final class SymbolResultSet implements Iterable<CSVRecord>, Closeable {

    private CSVParser parser;

    private Acceptor acceptor;

    public SymbolResultSet(CSVParser parser, Acceptor acceptor) {
        this.parser = parser;
        this.acceptor = acceptor;
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    @Override
    public Iterator<CSVRecord> iterator() {

        Iterator<CSVRecord> records = parser.iterator();

        return new Iterator<CSVRecord>() {

            private CSVRecord next;

            @Override
            public boolean hasNext() {
                while (true) {
                    if (!records.hasNext()) {
                        return false;
                    }
                    CSVRecord next = records.next();
                    if (acceptor.accept(next)) {
                        this.next = next;
                        return true;
                    }
                }
            }

            @Override
            public CSVRecord next() {
                return next;
            }
        };
    }
}