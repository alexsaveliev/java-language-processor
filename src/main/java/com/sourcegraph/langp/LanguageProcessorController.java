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

    @PostMapping(value = "/prepare")
    public void prepare(@RequestBody PrepareSpec prepareSpec)
            throws WorkspaceException {
        workspaceService.getWorkspace(prepareSpec.getRepo(), prepareSpec.getCommit(), prepareSpec.isForce());
    }

    @PostMapping(value = "/definition")
    public Range definition(@RequestBody Position pos)
            throws WorkspaceException, SymbolException, NoDefinitionFoundException {
        return symbolService.definition(pos);
    }

    @PostMapping(value = "/hover")
    public Hover hover(@RequestBody Position pos) throws WorkspaceException, SymbolException {
        return symbolService.hover(pos);
    }

    @PostMapping(value = "/local-refs")
    public LocalRefs localRefs(@RequestBody Position pos)
            throws WorkspaceException, SymbolException, NoDefinitionFoundException {
        return symbolService.localRefs(pos);
    }

    @PostMapping(value = "/external-refs")
    public ExternalRefs externalRefs(@RequestBody RepoRev repoRev)
            throws WorkspaceException, SymbolException {
        return symbolService.externalRefs(repoRev);
    }

    @ExceptionHandler({Exception.class})
    @ResponseBody
    ResponseEntity<Error> handleError(HttpServletResponse response, Exception ex) throws IOException {
        Error error = new Error(ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
