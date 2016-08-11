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
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        repositoryService.getRepository(repoRev.getRepo(), repoRev.getCommit(), true);
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    @PostMapping(value = "/definition")
    public Range definition(@Valid @RequestBody Position pos)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        return symbolService.definition(pos);
    }

    @PostMapping(value = "/hover")
    public Hover hover(@Valid @RequestBody Position pos)
            throws WorkspaceBeingPreparedException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        return symbolService.hover(pos);
    }

    @PostMapping(value = "/local-refs")
    public LocalRefs localRefs(@Valid @RequestBody Position pos)
            throws WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        return symbolService.localRefs(pos);
    }

    @PostMapping(value = "/external-refs")
    public ExternalRefs externalRefs(@Valid @RequestBody RepoRev repoRev)
            throws WorkspaceException, SymbolException {
        return symbolService.externalRefs(repoRev);
    }

    @PostMapping(value = "/exported-symbols")
    public ExternalRefs exportedSymbols(@Valid @RequestBody RepoRev repoRev)
            throws WorkspaceException, SymbolException {
        return symbolService.exportedSymbols(repoRev);
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
