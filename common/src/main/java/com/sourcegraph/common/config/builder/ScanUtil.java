package com.sourcegraph.common.config.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * File scan utilities
 */
public class ScanUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanUtil.class);

    private ScanUtil() {
    }

    /**
     * Retrieves all matching files in the given root
     * @param root root directory
     * @param fileName file name to match against
     * @return found files
     * @throws IOException
     */
    public static Collection<Path> findMatchingFiles(Path root, String fileName) throws IOException {
        Collection<Path> result = new HashSet<>();

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (fileName.equals(name)) {
                    result.add(file.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Skip common build data directories and dot-directories.
                String dirName = dir.getFileName().normalize().toString();
                if (dirName.equals("build") || dirName.equals("target") || dirName.startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    /**
     * @param path workspace root
     * @param directories source directories
     * @return all Java files found
     */
    static Set<String> getSourceFiles(Path path, Collection<String> directories) {
        Set<String> files = new HashSet<>();
        try {
            FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    String name = file.toString();
                    if (name.endsWith(".java")) {
                        files.add(name);
                    }
                    return FileVisitResult.CONTINUE;
                }
            };
            for (String directory : directories) {
                Files.walkFileTree(path.resolve(directory), visitor);
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to collect java files", e);
        }
        return files;
    }
}
