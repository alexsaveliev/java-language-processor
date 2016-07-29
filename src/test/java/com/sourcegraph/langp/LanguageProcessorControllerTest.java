package com.sourcegraph.langp;

import com.sourcegraph.langp.javac.Workspace;
import com.sourcegraph.langp.model.Hover;
import com.sourcegraph.langp.model.HoverContent;
import com.sourcegraph.langp.model.Position;
import com.sourcegraph.langp.service.WorkspaceException;
import com.sourcegraph.langp.service.WorkspaceService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "/test.properties")
public class LanguageProcessorControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${workspace}")
    private String workspace;

    @Autowired
    private WorkspaceService workspaceService;

    @Test
    public void mavenHoverTest() throws Exception {

        Position p = new Position();

        p.setRepo("github.com/sgtest/java-maven-sample");
        p.setCommit("e6e1dca05be97bba8cd9ea5b828191c5c6d2b9db");
        p.setFile("src/main/java/mypkg/FooClass.java");
        p.setLine(14);
        p.setCharacter(16);
        Hover hover = this.restTemplate.postForObject("/hover", p, Hover.class);
        assertNotNull("got null hover object", hover);
        assertNotNull("got null hover content", hover.getContents());
        assertEquals("got invalid hover content", 1, hover.getContents().size());
        HoverContent content = hover.getContents().iterator().next();
        assertEquals("got invalid hover content type", "java", content.getType());
        assertEquals("got invalid hover content value",
                " FooClass is a class.\n \n @author Fred\n \n",
                StringUtils.remove(content.getValue(), '\r'));
    }

    @Test
    public void gradleHoverTest() throws Exception {

        Position p = new Position();

        p.setRepo("github.com/sgtest/java-gradle-sample");
        p.setCommit("26e4819e4ad3925b6a905a8dce4d5be5151595cb");
        p.setFile("src/main/java/mypkg/FooClass.java");
        p.setLine(36);
        p.setCharacter(15);
        Hover hover = this.restTemplate.postForObject("/hover", p, Hover.class);
        assertNotNull("got null hover object", hover);
        assertNotNull("got null hover content", hover.getContents());
        assertEquals("got invalid hover content", 1, hover.getContents().size());
        HoverContent content = hover.getContents().iterator().next();
        assertEquals("got invalid hover content type", "java", content.getType());
        assertEquals("got invalid hover content value",
                " myInt is a method.\n \n @return an awesome int\n",
                StringUtils.remove(content.getValue(), '\r'));
    }


}
