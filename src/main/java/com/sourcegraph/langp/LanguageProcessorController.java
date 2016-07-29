package com.sourcegraph.langp;

import com.sourcegraph.langp.model.Error;
import com.sourcegraph.langp.model.Hover;
import com.sourcegraph.langp.model.LocalRefs;
import com.sourcegraph.langp.model.Position;
import com.sourcegraph.langp.service.NoDefinitionFoundException;
import com.sourcegraph.langp.service.SymbolException;
import com.sourcegraph.langp.service.SymbolService;
import com.sourcegraph.langp.service.WorkspaceException;
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
    private SymbolService symbolService;

    @PostMapping(value = "/definition")
    public Position definition(@RequestBody Position pos)
            throws WorkspaceException, SymbolException, NoDefinitionFoundException {
        return symbolService.definition(pos);
    }

    @PostMapping(value = "/hover")
    public Hover hover(@RequestBody Position pos) throws WorkspaceException, SymbolException {
        return symbolService.hover(pos);
    }

    @PostMapping(value = "/local-refs")
    public LocalRefs localRefs(@RequestBody Position position) {
        return null;
    }

    @ExceptionHandler({SymbolException.class, WorkspaceException.class, NoDefinitionFoundException.class})
    @ResponseBody
    ResponseEntity<Error> handleError(HttpServletResponse response, Exception ex) throws IOException {
        Error error = new Error(ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
