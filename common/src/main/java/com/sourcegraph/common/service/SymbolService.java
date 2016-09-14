package com.sourcegraph.common.service;

import com.sourcegraph.common.configuration.TaskExecutorConfiguration;
import com.sourcegraph.common.javac.SymbolIndex;
import com.sourcegraph.common.javac.SymbolResultSet;
import com.sourcegraph.common.javac.Workspace;
import com.sourcegraph.common.javac.WorkspaceService;
import com.sourcegraph.common.model.*;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Provides foundSymbol resolution methods
 */
@Service
public class SymbolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolService.class);

    /**
     * Wait no more than X milliseconds to acquire object
     */
    @Value("${workspace.get.timeout:250}")
    private long timeout;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private TaskExecutorConfiguration taskExecutorConfiguration;

    /**
     * @param root     workspace root
     * @param position symbol position
     * @return hover information for a given symbol
     * @throws SymbolException            if no foundSymbol is found
     * @throws NoDefinitionFoundException if there is no foundSymbol at specific position
     */
    public Hover hover(Path root,
                       Position position)
            throws NoDefinitionFoundException,
            SymbolException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Hover {}/{} {}:{}",
                root,
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());

        try {
            Symbol s = getSymbol(position, workspace, sourceFile);
            Hover ret = new Hover();
            ret.setTitle(s.getTitle());
            ret.setDocHtml(s.getDocHtml());
            return ret;
        } catch (NoDefinitionFoundException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for hover {}/{} {}:{}",
                    root,
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root     workspace root
     * @param position symbol position
     * @return symbol's local definition
     * @throws SymbolException            if no foundSymbol is found
     * @throws NoDefinitionFoundException if there is no foundSymbol at specific position
     */
    public com.sourcegraph.common.model.Symbol definition(Path root,
                                                          Position position) throws
            SymbolException,
            NoDefinitionFoundException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Definition {}/{} {}:{}",
                root,
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());

        try {
            return getSymbol(position, workspace, sourceFile);
        } catch (NoDefinitionFoundException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for definition {}/{} {}:{}",
                    root,
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param position symbol's position
     * @return local references to specific symbol
     * @throws SymbolException            if no symbol is found
     * @throws NoDefinitionFoundException if there is no symbol at specific position
     */
    public RefLocations localRefs(Path root,
                                  Position position) throws
            SymbolException,
            NoDefinitionFoundException {

        LOGGER.info("Local refs {} {}:{}",
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Workspace workspace = workspaceService.getWorkspace(root);
        Path sourceFile = root.resolve(position.getFile());

        RefLocations ret = new RefLocations();
        ret.setRefs(new LinkedList<>());
        try {
            workspace.computeIndexes(taskExecutorConfiguration.taskExecutor());

            SymbolIndex index = workspace.findIndex(sourceFile);
            if (index.isBeingIndexed()) {
                throw new WorkspaceBeingPreparedException();
            }
            if (!index.isIndexed()) {
                index.index(taskExecutorConfiguration.taskExecutor());
                throw new WorkspaceBeingPreparedException();
            }
            CSVRecord symbol = getSymbol(index, position);
            if (symbol == null) {
                throw new NoDefinitionFoundException();
            }

            try (SymbolResultSet records = index.getRecords(record -> {
                boolean match = symbol.get(1).equals(record.get(1));
                if (match && SymbolIndex.DEF.equals(record.get(0))) {
                    ret.getRefs().add(SymbolIndex.toRange(record));
                    return false;
                }
                return match;
            })) {
                for (CSVRecord record : records) {
                    ret.getRefs().add(SymbolIndex.toRange(record));
                }
                return ret;
            }
        } catch (NoDefinitionFoundException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for local refs {} {}:{}",
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root workpace root
     * @return all external references from given repository
     * @throws SymbolException if no foundSymbol is found
     */
    public ExternalRefs externalRefs(Path root)
            throws WorkspaceException,
            SymbolException {

        LOGGER.info("External refs in {}",
                root);

        try {
            Workspace workspace = workspaceService.getWorkspace(root);
            workspace.computeIndexes(taskExecutorConfiguration.taskExecutor());
            ExternalRefs ret = new ExternalRefs();
            Collection<SymbolIndex> indexes = workspace.getIndexes();
            Collection<DefSpec> defSpecs = new LinkedList<>();
            for (SymbolIndex index : indexes) {
                try (SymbolResultSet resultSet = index.getRecords(record -> {
                    if (!SymbolIndex.REF.equals(record.get(0))) {
                        return false;
                    }
                    return !StringUtils.isEmpty(record.get(7));
                })) {
                    for (CSVRecord r : resultSet) {
                        DefSpec spec = new DefSpec();
                        spec.setRepo(r.get(7));
                        spec.setUnitType(SymbolIndex.UNIT_TYPE);
                        spec.setUnit(r.get(8));
                        spec.setPath(r.get(1));
                        defSpecs.add(spec);
                    }
                }
            }
            ret.setDefs(defSpecs);
            return ret;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for external refs in {}",
                    root,
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root repository root
     * @return all exported symbols from given repository
     * @throws SymbolException if no symbol is found
     */
    public ExportedSymbols exportedSymbols(Path root)
            throws WorkspaceException,
            SymbolException {

        LOGGER.info("Exported symbols in {}",
                root);

        try {
            Workspace workspace = workspaceService.getWorkspace(root);
            workspace.computeIndexes(taskExecutorConfiguration.taskExecutor());
            ExportedSymbols ret = new ExportedSymbols();
            Collection<com.sourcegraph.common.model.Symbol> symbols = new HashSet<>();

            Collection<SymbolIndex> indexes = workspace.getIndexes();
            for (SymbolIndex index : indexes) {
                try (SymbolResultSet resultSet = index.getRecords(record -> SymbolIndex.DEF.equals(record.get(0)) &&
                        "true".equals(record.get(12)))) {
                    for (CSVRecord r : resultSet) {
                        com.sourcegraph.common.model.Symbol s = SymbolIndex.toSymbol(r);
                        symbols.add(s);
                    }
                }
            }
            ret.setSymbols(symbols);
            return ret;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for exported symbols in {}",
                    root,
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root    workspace root
     * @param defSpec def spec
     * @return position of symbol denoted by a given spec
     * @throws SymbolException            if no symbol is found
     * @throws NoDefinitionFoundException if there is no symbol with specified spec found
     */
    public Position defSpecToPosition(Path root,
                                      DefSpec defSpec) throws SymbolException,
            NoDefinitionFoundException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Defspec to position {}:{}",
                root,
                defSpec.getPath());

        Workspace workspace = workspaceService.getWorkspace(root);

        try {
            workspace.computeIndexes(taskExecutorConfiguration.taskExecutor());
            Collection<SymbolIndex> indexes = workspace.getIndexes();
            for (SymbolIndex index : indexes) {
                if (index.isBeingIndexed()) {
                    throw new WorkspaceBeingPreparedException();
                }
                if (!index.isIndexed()) {
                    index.index(taskExecutorConfiguration.taskExecutor());
                    throw new WorkspaceBeingPreparedException();
                }
                try (SymbolResultSet resultSet = index.getRecords(record -> {
                    if (SymbolIndex.REF.equals(record.get(0))) {
                        return false;
                    }
                    return defSpec.getPath().equals(record.get(1));
                })) {
                    Iterator<CSVRecord> defs = resultSet.iterator();
                    if (defs.hasNext()) {
                        com.sourcegraph.common.model.Symbol s = SymbolIndex.toSymbol(defs.next());
                        Position p = new Position();
                        p.setRepo(defSpec.getRepo());
                        p.setCommit(defSpec.getCommit());
                        p.setFile(s.getFile());
                        p.setLine(s.getRange().getStartLine());
                        p.setCharacter(s.getRange().getStartCharacter());
                        return p;
                    }
                }
            }
            throw new NoDefinitionFoundException();
        } catch (NoDefinitionFoundException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for defspec to path {}:{}",
                    root,
                    defSpec.getPath(),
                    e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param root     workspace root
     * @param position symbols' position
     * @return def spec of symbol at the given position
     * @throws SymbolException            if no symbol is found
     * @throws NoDefinitionFoundException if there is no symbol at the given position
     */
    public DefSpec positionToDefSpec(Path root,
                                     Position position) throws SymbolException,
            NoDefinitionFoundException,
            WorkspaceBeingPreparedException {

        LOGGER.info("Position to def spec {}:{}/{} {}:{}",
                position.getRepo(),
                position.getCommit(),
                position.getFile(),
                position.getLine(),
                position.getCharacter());

        Workspace workspace = workspaceService.getWorkspace(root);

        Path sourceFile = root.resolve(position.getFile());

        try {

            SymbolIndex index = workspace.findIndex(sourceFile);
            if (index.isBeingIndexed()) {
                throw new WorkspaceBeingPreparedException();
            }
            if (!index.isIndexed()) {
                index.index(taskExecutorConfiguration.taskExecutor());
                throw new WorkspaceBeingPreparedException();
            }
            CSVRecord symbol = getSymbol(index, position);
            if (symbol == null) {
                throw new NoDefinitionFoundException();
            }

            DefSpec ret = new DefSpec();
            ret.setPath(symbol.get(1));
            ret.setUnit(index.getConfig().unit);
            ret.setRepo(position.getRepo());
            ret.setCommit(position.getCommit());

            return ret;

        } catch (NoDefinitionFoundException | WorkspaceBeingPreparedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("An error occurred while looking for position to def spec {}:{}/{} {}:{}",
                    position.getRepo(),
                    position.getCommit(),
                    position.getFile(),
                    position.getLine(),
                    position.getCharacter(), e);
            throw new SymbolException(e.getMessage());
        }
    }

    /**
     * @param index    symbol index to look for symbols in
     * @param position position of symbol
     * @return symbol at the given position
     */
    private CSVRecord getSymbol(SymbolIndex index, Position position) {
        CSVRecord symbol = null;

        try (SymbolResultSet resultSet = index.getRecords(record -> {
            if (!position.getFile().equals(record.get(2))) {
                return false;
            }
            int startLine = Integer.parseInt(record.get(3));
            int startCharacter = Integer.parseInt(record.get(4));
            int endLine = Integer.parseInt(record.get(5));
            int endCharacter = Integer.parseInt(record.get(6));

            if (startLine > position.getLine() ||
                    startLine == position.getLine() && startCharacter > position.getCharacter()) {
                return false;
            }

            if (endLine < position.getLine() ||
                    endLine == position.getLine() && endCharacter < position.getCharacter()) {
                return false;
            }

            return true;
        })) {
            for (CSVRecord r : resultSet) {
                if (symbol == null) {
                    symbol = r;
                } else {

                    int rStartLine = Integer.parseInt(r.get(3));
                    int rStartCharacter = Integer.parseInt(r.get(4));
                    int rEndLine = Integer.parseInt(r.get(5));
                    int rEndCharacter = Integer.parseInt(r.get(6));

                    int sStartLine = Integer.parseInt(symbol.get(3));
                    int sStartCharacter = Integer.parseInt(symbol.get(4));
                    int sEndLine = Integer.parseInt(symbol.get(5));
                    int sEndCharacter = Integer.parseInt(symbol.get(6));

                    if (rStartLine > sStartLine ||
                            rEndLine < sEndLine ||
                            rStartLine == sStartLine && rStartCharacter > sStartCharacter ||
                            rEndLine == sEndLine && rEndCharacter < sEndCharacter) {
                        symbol = r;
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("An I/O error while reading index data", ex);
        }
        return symbol;
    }

    private com.sourcegraph.common.model.Symbol getSymbol(Position position, Workspace workspace, Path sourceFile) throws WorkspaceBeingPreparedException, NoDefinitionFoundException, IOException {
        SymbolIndex index = workspace.findIndex(sourceFile);
        if (index.isBeingIndexed()) {
            throw new WorkspaceBeingPreparedException();
        }
        if (!index.isIndexed()) {
            index.index(taskExecutorConfiguration.taskExecutor());
            throw new WorkspaceBeingPreparedException();
        }
        CSVRecord symbol = getSymbol(index, position);
        if (symbol == null) {
            throw new NoDefinitionFoundException();
        }
        try (SymbolResultSet resultSet = index.getRecords(record -> !SymbolIndex.REF.equals(record.get(0)) &&
                symbol.get(1).equals(record.get(1)))) {
            Iterator<CSVRecord> defs = resultSet.iterator();
            if (!defs.hasNext()) {
                throw new NoDefinitionFoundException();
            }
            return SymbolIndex.toSymbol(defs.next());
        }
    }

}