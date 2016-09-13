package com.sourcegraph.common.util;

import org.apache.commons.lang3.SystemUtils;

/**
 * Aux functions for path objects
 */
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