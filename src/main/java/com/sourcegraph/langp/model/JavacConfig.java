package com.sourcegraph.langp.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

public class JavacConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavacConfig.class);

    public static final String CONFIG_FILE_NAME = ".jconfig.json";

    private static Gson gson;

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        gsonBuilder.disableHtmlEscaping();
        gson = gsonBuilder.create();
    }

    public Collection<String> sources;
    public Collection<String> classPath;
    public String outputDirectory;

    /**
     * @param path to check
     * @return true if sources contain given path
     */
    public boolean containsSource(Path path) {
        return sources != null && sources.stream().anyMatch(path::startsWith);
    }
    /**
     * Normalizes and saves configuration
     * @param workspaceRoot workspace root, all entries will be resolved against it
     * @param targetDir target directory to create configuration in
     */
    public void save(Path workspaceRoot, Path targetDir) {
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
        File target = targetDir.resolve(CONFIG_FILE_NAME).toFile();
        try (FileWriter writer = new FileWriter(target)) {
            gson.toJson(this, writer);
            LOGGER.info("Wrote {}", target);
        } catch (IOException e) {
            LOGGER.warn("Failed to save configuration", e);
        }
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
                return gson.fromJson(reader, JavacConfig.class);
            } catch (IOException e) {
                LOGGER.warn("Cannot read {}", file, e);
                return null;
            }
        }
        return null;
    }
}
