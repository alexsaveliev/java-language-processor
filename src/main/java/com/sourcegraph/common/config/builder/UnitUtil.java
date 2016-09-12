package com.sourcegraph.common.config.builder;

import org.apache.commons.lang3.StringUtils;

/**
 * Unit utilities
 */
class UnitUtil {

    private static final String DEFAULT_GROUP_ID = "default-group";
    private static final String DEFAULT_ARTIFACT_ID = "default-artifact";


    /**
     * @param groupId group ID (may be empty)
     * @return normalized group ID (not empty)
     */
    static String groupId(String groupId) {
        return StringUtils.defaultIfEmpty(groupId, DEFAULT_GROUP_ID);
    }

    /**
     * @param artifactId artifact ID (may be empty)
     * @return normalized artifact ID (not empty)
     */
    static String artifactId(String artifactId) {
        return StringUtils.defaultIfEmpty(artifactId, DEFAULT_ARTIFACT_ID);
    }

    /**
     * @param groupId group ID
     * @param artifactId artifact ID
     * @return identifier based on group and artifact identifiers
     */
    static String id(String groupId, String artifactId) {
        return groupId(groupId) + '/' + artifactId(artifactId);
    }
}
