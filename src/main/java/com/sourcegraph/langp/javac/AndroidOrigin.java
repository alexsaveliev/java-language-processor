package com.sourcegraph.langp.javac;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;

class AndroidOrigin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidOrigin.class);

    private static final String ANDROID_SDK_REPO = "android.googlesource.com/platform/frameworks/base";
    private static final String ANDROID_SUPPORT_REPO = "android.googlesource.com/platform/frameworks/support";
    private static final String ANDROID_CORE_REPO = "android.googlesource.com/platform/libcore";

    private static List<String> libcoreClasses;
    private static List<String> supportClasses;
    private static List<String> sdkClasses;

    static {
        libcoreClasses = loadDefinitions("/android-libcore.dat");
        supportClasses = loadDefinitions("/android-support.dat");
        sdkClasses = loadDefinitions("/android-sdk.dat");
    }

    private AndroidOrigin() {
    }

    /**
     * Resolves jar URI either to libcore or to frameworks/base (support) resolved target
     * @param origin URI to resolve
     * @param force indicates if URI should be resolved to Android SDK if it can't be resolved neither to libcore nor to
     *              Android Support framework. I.e. we are sure that origin belongs to Android
     * @return resolved target or null if resolution failed
     */
    static String resolve(URI origin, boolean force) {
        String topClassName = extractTopClassName(origin);
        if (topClassName == null) {
            return null;
        }
        int index = Collections.binarySearch(libcoreClasses, topClassName);
        if (index >= 0) {
            return ANDROID_CORE_REPO;
        } else {
            index = Collections.binarySearch(supportClasses, topClassName);
            if (index >= 0) {
                return ANDROID_SUPPORT_REPO;
            }
            if (force) {
                return ANDROID_SDK_REPO;
            }
            if (Collections.binarySearch(sdkClasses, topClassName) >= 0) {
                return ANDROID_SDK_REPO;
            }
            return null;
        }
    }

    /**
     * Extracts top-level class name from jar URI (supposed to be in form jar:file..!path/to/classname.class)
     * @param origin jar URI
     * @return extracted path to top-level class in form foo/bar/bazz or null
     */
    private static String extractTopClassName(URI origin) {
        String path = origin.toString();
        // retrieve part after !/ (path to class file)
        int i = path.lastIndexOf('!');
        if (i != -1) {
            path = path.substring(i + 2);
        } else {
            return null;
        }
        i = path.indexOf('$');
        if (i != -1) {
            // inner class, leaving only top-level class element
            return path.substring(0, i);
        }
        i = path.indexOf('.');
        if (i != -1) {
            // removing everything after . (stripping '.class')
            return path.substring(0, i);
        }
        return null;
    }

    /**
     * Loads class definitions from specified resource
     * @param id resource ID
     * @return sorted class definitions
     */
    private static List<String> loadDefinitions(String id) {
        InputStream is = AndroidOrigin.class.getResourceAsStream(id);
        if (is != null) {
            try {
                List<String> ret = IOUtils.readLines(is);
                Collections.sort(ret);
                return ret;
            } catch (IOException e) {
                LOGGER.warn("Failed to load definitions", e);
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }
}
