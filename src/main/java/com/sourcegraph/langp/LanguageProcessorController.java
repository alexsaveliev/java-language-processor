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
import java.io.IOException;

@RestController
//@SuppressWarnings("unused")
public class LanguageProcessorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageProcessorController.class);

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private SymbolService symbolService;

    @PostMapping(value = "/definition")
    public Range definition(@RequestBody Position pos)
            throws WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        return symbolService.definition(pos);
    }

    @PostMapping(value = "/hover")
    public Hover hover(@RequestBody Position pos)
            throws WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException {
        return symbolService.hover(pos);
    }

    @PostMapping(value = "/local-refs")
    public LocalRefs localRefs(@RequestBody Position pos)
            throws WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException,
            NoDefinitionFoundException {
        return symbolService.localRefs(pos);
    }

    @PostMapping(value = "/external-refs")
    public ExternalRefs externalRefs(@RequestBody RepoRev repoRev)
            throws WorkspaceBeingClonedException,
            WorkspaceBeingConfiguredException,
            WorkspaceException,
            SymbolException {
        return symbolService.externalRefs(repoRev);
    }

    @ExceptionHandler({WorkspaceBeingConfiguredException.class})
    @ResponseBody
    ResponseEntity<Error> handleWorkspaceBeingConfiguredException(HttpServletResponse response) throws IOException {
        Error error = new Error("Workspace being configured...");
        return new ResponseEntity<>(error, HttpStatus.ACCEPTED);
    }

    @ExceptionHandler({WorkspaceBeingClonedException.class})
    @ResponseBody
    ResponseEntity<Error> handleWorkspaceBeingClonedException(HttpServletResponse response) throws IOException {
        Error error = new Error("Workspace being cloned...");
        return new ResponseEntity<>(error, HttpStatus.ACCEPTED);
    }

    @ExceptionHandler({Exception.class})
    @ResponseBody
    ResponseEntity<Error> handleError(HttpServletResponse response, Exception ex) throws IOException {
        Error error = new Error(ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
