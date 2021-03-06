package com.sourcegraph.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class JavacConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavacConfig.class);

    public static final String CONFIG_FILE_NAME = ".jconfig.json";

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
    }

    public Set<String> files;
    public Collection<String> sources;
    public Collection<String> classPath;
    public String outputDirectory;
    public boolean android;
    public boolean androidSdk;
    public Collection<Dependency> dependencies;
    public String unit;

    private Path file;

    /**
     * Normalizes and saves configuration
     * @param workspaceRoot workspace root, all entries will be resolved against it
     * @param targetDir target directory to create configuration in
     */
    public void save(Path workspaceRoot, Path targetDir) {
        if (files != null) {
            files = files.
                    stream().
                    map(s -> workspaceRoot.resolve(s).toAbsolutePath().normalize().toString()).
                    collect(Collectors.toSet());
        }
        if (sources != null) {
            sources = sources.
                    stream().
                    map(s -> workspaceRoot.resolve(s).toAbsolutePath().normalize().toString()).
                    collect(Collectors.toList());
        }
        if (classPath != null) {
            classPath = classPath.
                    stream().
                    map(s -> workspaceRoot.resolve(s).toAbsolutePath().normalize().toString()).
                    collect(Collectors.toList());
        }
        if (outputDirectory != null) {
            outputDirectory = workspaceRoot.resolve(outputDirectory).toString();
        }
        if (dependencies != null) {
            for (Dependency dependency : dependencies) {
                if (!StringUtils.isEmpty(dependency.file)) {
                    dependency.file = workspaceRoot.resolve(dependency.file).toAbsolutePath().normalize().toString();
                }
            }
        }
        File target = targetDir.resolve(CONFIG_FILE_NAME).toFile();
        this.file = target.toPath().toAbsolutePath().normalize();

        try (FileWriter writer = new FileWriter(target)) {
            mapper.writeValue(writer, this);
            LOGGER.info("Wrote {}", target);
        } catch (IOException e) {
            LOGGER.warn("Failed to save configuration", e);
        }
    }

    public Dependency getDependencyForJar(Path jar) {
        String sample = jar.toAbsolutePath().normalize().toString();
        for (Dependency dependency : dependencies) {
            if (sample.equals(dependency.file)) {
                return dependency;
            }
        }
        return null;
    }



    /**
     * Tries to read configuration in the given directory
     * @param dir directory to check configuration in
     * @return configuration object or null if there is not configuration available
     */
    public static JavacConfig read(Path dir) {
        File file = dir.resolve(CONFIG_FILE_NAME).toFile();
        if (file.isFile()) {
            try (Reader reader = new FileReader(file)){
                JavacConfig ret = mapper.readValue(reader, JavacConfig.class);
                ret.file = file.toPath().toAbsolutePath().normalize();
                return ret;
            } catch (IOException e) {
                LOGGER.warn("Cannot read {}", file, e);
                return null;
            }
        }
        return null;
    }

    @JsonIgnore
    public Path getFile() {
        return file;
    }

}
