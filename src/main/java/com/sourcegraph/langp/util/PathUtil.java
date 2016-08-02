package com.sourcegraph.langp.util;

import org.apache.commons.lang3.SystemUtils;

public class PathUtil {

    /**
     * Normalizes path string by translating it to Unix-style (foo\bar => foo/bar)
     * @param path path to normalize
     * @return normalized path
     */
    public static String normalize(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return path.replace('\\', '/');
        } else {
            return path;
        }
    }

}