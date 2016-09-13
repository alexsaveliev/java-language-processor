package com.sourcegraph.common.javac;

import com.sourcegraph.common.model.DefSpec;
import com.sourcegraph.common.model.JavacConfig;
import com.sourcegraph.common.model.Position;
import com.sourcegraph.common.model.Range;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Name;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Identifies if given record is acceptable
 */
public interface Acceptor {

    /**
     * @param record CSV index record to check
     * @return true if given CSV record is acceptable
     */
    boolean accept(CSVRecord record);
}