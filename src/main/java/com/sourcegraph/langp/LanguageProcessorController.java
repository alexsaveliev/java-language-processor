package com.sourcegraph.langp;

import com.sourcegraph.langp.model.Error;
import com.sourcegraph.langp.model.*;
import com.sourcegraph.langp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;

@RestController
//@SuppressWarnings("unused")
public class LanguageProcessorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageProcessorController.class);

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private SymbolService symbolService;

    @PostMapping(value = "/prepare")
    public void prepare(@Valid @RequestBody RepoRev repoRev, HttpServletResponse response)
            throws WorkspaceException, InterruptedException, ExecutionException {
        LOGGER.info("Prepare {}@{}",
                repoRev.getRepo(),
                repoRev.getCommit());
        repositoryService.getRepository(repoRev.getRepo(), repoRev.getCommit(), true);
    }

    @PostMapping(value = "/definition")
    public Range definition(@Valid @RequestBody Position pos)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        Path root = repositoryService.getWorkspace(pos.getRepo(), pos.getCommit()).toPath();
        Range ret = symbolService.definition(root, pos);
        ret.setRepo(pos.getRepo());
        ret.setCommit(pos.getCommit());
        return ret;
    }

    @PostMapping(value = "/hover")
    public Hover hover(@Valid @RequestBody Position pos)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        Path root = repositoryService.getWorkspace(pos.getRepo(), pos.getCommit()).toPath();
        return symbolService.hover(root, pos);
    }

    @PostMapping(value = "/local-refs")
    public RefLocations localRefs(@Valid @RequestBody Position pos)
            throws WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        RefLocations ret = symbolService.localRefs(pos);
        Collection<Range> refs = new LinkedList<>();
        for (Range range : ret.getRefs()) {
            Range copy = new Range(range);
            copy.setRepo(pos.getRepo());
            copy.setCommit(pos.getCommit());
            refs.add(copy);
        }
        ret.setRefs(refs);
        return ret;
    }

    @PostMapping(value = "/external-refs")
    public ExternalRefs externalRefs(@Valid @RequestBody RepoRev repoRev)
            throws WorkspaceException, SymbolException {
        return symbolService.externalRefs(repoRev);
    }

    @PostMapping(value = "/exported-symbols")
    public ExportedSymbols exportedSymbols(@Valid @RequestBody RepoRev repoRev)
            throws WorkspaceException, SymbolException {
        return symbolService.exportedSymbols(repoRev);
    }

    @PostMapping(value = "/defspec-to-position")
    public Position defSpecToPosition(@Valid @RequestBody DefSpec defSpec)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        Path root = repositoryService.getWorkspace(defSpec.getRepo(), defSpec.getCommit()).toPath();
        return symbolService.defSpecToPosition(root, defSpec);
    }

    @PostMapping(value = "/position-to-defspec")
    public DefSpec positionToDefSpec(@Valid @RequestBody Position pos)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        Path root = repositoryService.getWorkspace(pos.getRepo(), pos.getCommit()).toPath();
        return symbolService.positionToDefSpec(root, pos);
    }

    @PostMapping(value = "/defspec-refs")
    public RefLocations defSpecRefs(@Valid @RequestBody DefSpec defSpec)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        Path root = repositoryService.getWorkspace(defSpec.getRepo(), defSpec.getCommit()).toPath();
        Position pos = symbolService.defSpecToPosition(root, defSpec);
        return localRefs(pos);
    }

    @ExceptionHandler({WorkspaceBeingPreparedException.class})
    @ResponseBody
    ResponseEntity<Error> handleWorkspaceBeingClonedException(HttpServletResponse response) throws IOException {
        Error error = new Error("Workspace being prepared...");
        return new ResponseEntity<>(error, HttpStatus.ACCEPTED);
    }

    @ExceptionHandler({NoDefinitionFoundException.class})
    @ResponseBody
    ResponseEntity<Error> handleNoDefinitionFoundException(HttpServletResponse response) throws IOException {
        Error error = new Error("No definition found");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({Exception.class})
    @ResponseBody
    ResponseEntity<Error> handleError(HttpServletResponse response, Exception ex) throws IOException {
        LOGGER.error("An internal error occurred", ex);
        Error error = new Error(ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

}
