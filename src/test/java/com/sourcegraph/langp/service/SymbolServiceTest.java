package com.sourcegraph.langp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sourcegraph.langp.javac.Workspace;
import com.sourcegraph.langp.javac.WorkspaceService;
import com.sourcegraph.langp.model.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Timed;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@TestPropertySource(locations = "/test.properties")
public class SymbolServiceTest {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private SymbolService symbolService;

    @Autowired
    private WorkspaceService workspaceService;

    private Workspace workspace;

    @Before
    public void setUp() throws Exception {
        repositoryService.purge();
        workspaceService.purge();
    }

    @After
    public void tearDown() throws Exception {
        // ensure that no threads are left
        if (workspace != null) {
            workspace.computeIndexes();
            workspace = null;
        }
    }

    @Test
    @Timed(millis = 1000L)
    public void testHoverDoesNotBlock() throws Exception {
        Position position = new Position();
        position.setRepo("github.com/sgtest/java-maven-sample");
        position.setCommit("e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        position.setFile("src/main/java/mypkg/FooClass.java");
        position.setLine(14);
        position.setCharacter(19);
        try {
            symbolService.hover(repositoryService.getWorkspace(position.getRepo(), position.getCommit()).toPath(),
                    position);
        } catch (WorkspaceBeingPreparedException e) {
            return;
        }
        fail("Excepted to catch WorkspaceBeingPreparedException");
    }

    @Test
    public void testHover() throws Exception {
        // wait for clone completion
        File repoRoot = repositoryService.getRepository("github.com/sgtest/java-maven-sample",
                "e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db").get();
        workspace = workspaceService.getWorkspace(repoRoot.toPath());
        // wait for indexing completion
        workspace.computeIndexes();

        Position position = new Position();
        position.setRepo("github.com/sgtest/java-maven-sample");
        position.setCommit("e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        position.setFile("src/main/java/mypkg/FooClass.java");
        position.setLine(14);
        position.setCharacter(19);
        Hover hover = symbolService.hover(repositoryService.getWorkspace(position.getRepo(),
                position.getCommit()).toPath(),
                position);
        assertNotNull(hover);
        assertEquals("wrong number of content", 1, hover.getContents().size());
        HoverContent content = hover.getContents().iterator().next();
        assertEquals("unexpected hover content type",
                "java",
                content.getType());
        assertEquals("unexpected hover content value",
                " FooClass is a class.\n" +
                        " \n" +
                        " @author Fred\n" +
                        " \n",
                StringUtils.remove(content.getValue(), '\r'));
    }

    @Test
    @Timed(millis = 1000L)
    public void testDefinitionDoesNotBlock() throws Exception {
        Position position = new Position();
        position.setRepo("github.com/sgtest/java-maven-sample");
        position.setCommit("e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        position.setFile("src/main/java/mypkg/FooClass.java");
        position.setLine(14);
        position.setCharacter(19);
        try {
            symbolService.definition(repositoryService.getWorkspace(position.getRepo(), position.getCommit()).toPath(),
                    position);
        } catch (WorkspaceBeingPreparedException e) {
            return;
        }
        fail("Excepted to catch WorkspaceBeingPreparedException");
    }

    @Test
    public void testDefinition() throws Exception {
        // wait for clone completion
        File repoRoot = repositoryService.getRepository("github.com/sgtest/java-maven-sample",
                "e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db").get();
        workspace = workspaceService.getWorkspace(repoRoot.toPath());
        // wait for indexing completion
        workspace.computeIndexes();

        Position position = new Position();
        position.setRepo("github.com/sgtest/java-maven-sample");
        position.setCommit("e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        position.setFile("src/main/java/mypkg/subpkg/ZipClass.java");
        position.setLine(6);
        position.setCharacter(29);
        Range definition = symbolService.definition(repositoryService.getWorkspace(position.getRepo(),
                position.getCommit()).toPath(),
                position);
        assertNotNull(definition);
        assertEquals("unexpected definition file", "src/main/java/mypkg/FooClass.java", definition.getFile());
        assertEquals("unexpected definition start line", 14, definition.getStartLine());
        assertEquals("unexpected definition start character", 13, definition.getStartCharacter());
        assertEquals("unexpected definition end line", 14, definition.getEndLine());
        assertEquals("unexpected definition end character", 21, definition.getEndCharacter());
    }

    @Test
    public void testLocalRefs() throws Exception {
        // wait for clone completion
        File repoRoot = repositoryService.getRepository("github.com/sgtest/java-maven-sample",
                "e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db").get();
        workspace = workspaceService.getWorkspace(repoRoot.toPath());


        Position position = new Position();
        position.setRepo("github.com/sgtest/java-maven-sample");
        position.setCommit("e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        position.setFile("src/main/java/mypkg/FooClass.java");
        position.setLine(14);
        position.setCharacter(19);
        LocalRefs refs = symbolService.localRefs(position);
        assertNotNull(refs);
        Collection<Range> expected = new ObjectMapper().
                readValue(this.getClass().getResourceAsStream("/data/local-refs.json"),
                        new TypeReference<List<Range>>() {
                        });
        assertEquals("unexpected refs", new HashSet<>(expected), new HashSet<>(refs.getRefs()));
    }

    @Test
    public void testExternalRefs() throws Exception {
        // wait for clone completion
        File repoRoot = repositoryService.getRepository("github.com/sgtest/java-maven-sample",
                "e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db").get();
        workspace = workspaceService.getWorkspace(repoRoot.toPath());


        RepoRev repoRev = new RepoRev();
        repoRev.setRepo("github.com/sgtest/java-maven-sample");
        repoRev.setCommit("e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        ExternalRefs refs = symbolService.externalRefs(repoRev);
        assertNotNull(refs);
        Collection<DefSpec> expected = new ObjectMapper().
                readValue(this.getClass().getResourceAsStream("/data/external-refs.json"),
                        new TypeReference<List<DefSpec>>() {
                        });
        assertEquals("unexpected refs", new HashSet<>(expected), new HashSet<>(refs.getDefs()));
    }


}
