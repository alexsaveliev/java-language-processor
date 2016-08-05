package com.sourcegraph.langp.service;

import com.sourcegraph.langp.javac.WorkspaceService;
import com.sourcegraph.langp.model.Hover;
import com.sourcegraph.langp.model.HoverContent;
import com.sourcegraph.langp.model.JavacConfig;
import com.sourcegraph.langp.model.Position;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Timed;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@TestPropertySource(locations = "/test.properties")
public class RepositoryServiceTest {

    @Autowired
    private RepositoryService repositoryService;

    @Before
    public void setUp() throws Exception {
        repositoryService.purge();
    }

    @Test
    @Timed(millis = 1000L)
    public void testDoesNotBlock() throws Exception {
        repositoryService.getRepository("github.com/sgtest/java-maven-sample",
                "e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
    }

    @Test()
    public void testPrepare() throws Exception {
        Future<File> future = repositoryService.getRepository("github.com/sgtest/java-maven-sample",
                "e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        File file = future.get();
        assertTrue("Directory should exist", file.isDirectory());
        assertTrue(".git should exist", new File(file, ".git").isDirectory());
        assertTrue(".jsconfig.json should exist", new File(file, JavacConfig.CONFIG_FILE_NAME).isFile());

        JavacConfig javacConfig = JavacConfig.read(file.toPath());
        assertNotNull("missing configuration object", javacConfig);
        assertEquals("this is not android", false, javacConfig.android);
        assertEquals("this is not android SDK", false, javacConfig.androidSdk);
        assertEquals("wrong number of source directories", 1, javacConfig.sources.size());
        assertEquals("unexpected source directory",
                file.toPath().resolve("src/main/java"),
                Paths.get(javacConfig.sources.iterator().next()));
    }


}
