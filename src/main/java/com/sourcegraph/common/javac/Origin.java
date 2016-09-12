package com.sourcegraph.common.javac;

import com.sourcegraph.common.model.Dependency;
import com.sourcegraph.common.model.JavacConfig;
import com.sourcegraph.common.util.PathUtil;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Origin {

    private static final Logger LOGGER = LoggerFactory.getLogger(Origin.class);

    private static final String OPENJDK_REPO_ROOT = "hg.openjdk.java.net/jdk8/jdk8/";

    private static final String JDK_REPO = OPENJDK_REPO_ROOT + "jdk";
    private static final String TOOLS_JAR_REPO = OPENJDK_REPO_ROOT + "langtools";
    private static final String NASHORN_REPO = OPENJDK_REPO_ROOT + "nashorn";

    private static Map<Pattern, String> overrides;

    private static Map<URI, Entry> cache = new HashMap<>();
    private static Map<String, Entry> depsCache = new HashMap<>();

    static {
        overrides = new HashMap<>();
        InputStream is = Origin.class.getResourceAsStream("/override.properties");
        if (is != null) {
            try {
                Properties props = new Properties();
                props.load(is);
                for (String key : props.stringPropertyNames()) {
                    overrides.put(Pattern.compile(key), props.getProperty(key));
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to load substitution properties", e);
            }
        }
    }

    static String getRepository(JavaFileObject origin, JavacConfig javacConfig) {
        URI normalizedOrigin = normalizeOrigin(origin.toUri());
        Entry cached = cache.get(normalizedOrigin);
        if (cached != null) {
            return cached.uri;
        }
        Path jarFile;
        try {
            jarFile = getOriginJARFilePath(normalizedOrigin);
        } catch (URISyntaxException e) {
            LOGGER.warn("Error getting origin file path for origin: {}", normalizedOrigin, e);
            cache.put(normalizedOrigin, new Entry());
            return null;
        }

        if (jarFile == null) {
            // local ref, should not happen
            return null;
        }

        String special = processSpecialJar(origin.toUri(), jarFile, javacConfig);
        if (special != null) {
            return special;
        }

        Dependency dependency = javacConfig.getDependencyForJar(jarFile);
        if (dependency == null) {
            String resolved;
            if (javacConfig.android) {
                resolved = tryResolveExplodedAar(normalizedOrigin, javacConfig);
            } else {
                resolved = null;
            }
            cache.put(normalizedOrigin, new Entry(resolved));
            return resolved;
        }

        String scm = getScmUriForDependency(dependency);
        cache.put(normalizedOrigin, new Entry(scm));

        return scm;
    }

    /**
     * Removes !.... from jar URI
     * @param origin origin to normalize
     * @return origin with stripped !... tail for jar URI, or unchanged origin otherwise
     */
    private static URI normalizeOrigin(URI origin) {
        if (origin.getScheme().equals("jar")) {
            String s = origin.toString();
            int pos = s.lastIndexOf('!');
            if (pos != -1) {
                s = s.substring(0, pos);
                try {
                    return new URI(s);
                } catch (URISyntaxException e) {
                    return origin;
                }
            } else {
                return origin;
            }
        } else {
            return origin;
        }
    }

    /**
     * @return the origin JAR file as a Path if its URI is a "jar:file:" or "file:" URI. For "jar:file:" URIs, the path inside the JAR after the "!" is stripped.
     */
    private static Path getOriginJARFilePath(URI origin) throws URISyntaxException {
        if (origin == null) return null;
        if (origin.getScheme().equals("jar")) {
            URI fileURI = new URI(origin.getRawSchemeSpecificPart());
            if (!fileURI.getScheme().equals("file")) {
                throw new URISyntaxException(origin.toString(), "def origin URI must be a jar:file: URI, not jar:" + fileURI.getScheme());
            }
            File localFile = new File(fileURI);

            // Split on the "!" (in, e.g., "jar:file:/path/to/my.jar!/path/to/class/file.class").
            String path = localFile.getPath();
            int i = path.indexOf('!');
            if (i != -1) {
                path = path.substring(0, i);
            }
            return FileSystems.getDefault().getPath(path);
        } else {
            // TODO (alexsaveliev) should we report an error or what?
            //throw new URISyntaxException(origin.toString(), "def origin URI must be a jar:file: URI");
            return null;
        }
    }

    /**
     * Detects special JAR files
     * @param origin origin to check
     * @param jarFile jar file we are working with
     * @param javacConfig javac config to use
     * @return resolved target if jar file matches known one
     */
    private static String processSpecialJar(URI origin, Path jarFile, JavacConfig javacConfig) {
        String jarName = jarFile.getFileName().toString();
        if (isJDK(jarFile)) {
            if (javacConfig.android) {
                return AndroidOrigin.resolve(origin, true);
            }
            String ret = JDK_REPO;
            cache.put(origin, new Entry(ret));
            return ret;
        } else if (jarName.equals("tools.jar")) {
            String ret = TOOLS_JAR_REPO;
            cache.put(origin, new Entry(ret));
            return ret;
        } else if (jarName.equals("nashorn.jar")) {
            String ret = NASHORN_REPO;
            cache.put(origin, new Entry(ret));
            return ret;
        } else if (jarName.equals("android.jar")) {
            return AndroidOrigin.resolve(origin, true);
        } else if (javacConfig.android) {
            return AndroidOrigin.resolve(origin, false);
        } else if (javacConfig.androidSdk) {
            return AndroidOrigin.resolve(origin, true);
        }
        return null;
    }

    /**
     * @param jarFile path to Jar file
     * @return true if given Jar file belongs to JDK
     */
    private static boolean isJDK(Path jarFile) {
        return jarFile.endsWith("ct.sym") || PathUtil.normalize(jarFile.toString()).contains("jre/lib/");
    }

    /**
     * This method tries to resolve target for origins matching .../exploded-aar/group/artifact/version/...
     * @param origin to resolve
     * @param config config to use
     * @return resolved target if origin matches exploded AAR pattern
     */
    private static String tryResolveExplodedAar(URI origin, JavacConfig config) {
        String uri = origin.toString();
        int pos = uri.indexOf("/exploded-aar/");
        if (pos < 0) {
            return null;
        }
        uri = uri.substring(pos + "/exploded-aar/".length());
        String parts[] = uri.split("\\/", 4);
        if (parts.length < 4) {
            return null;
        }
        // looking for unit's dependency that matches group/artifact/version
        for (Dependency dependency : config.dependencies) {
            if (StringUtils.equals(parts[0], dependency.groupID) &&
                    StringUtils.equals(parts[1], dependency.artifactID) &&
                    StringUtils.equals(parts[2], dependency.version)) {
                return getScmUriForDependency(dependency);
            }
        }
        return null;
    }

    /**
     * Try to resolveOrigin this raw Dependency to its VCS target.
     * @return SCM URI
     */
    private static String getScmUriForDependency(Dependency dependency) {

        String groupId = dependency.groupID;
        String key = groupId + ':' + dependency.artifactID + ':' + dependency.version;
        Entry cached = depsCache.get(key);
        if (cached != null) {
            return cached.uri;
        }

        String scm = getOverride(groupId + '/' + dependency.artifactID);
        // We may know repo URI already
        if (scm != null) {
            depsCache.put(key, new Entry(scm));
            return scm;
        }

        try {
            scm = getScmUrl(dependency);
        } catch (IOException | XmlPullParserException e) {
            scm = null;
        }
        depsCache.put(key, new Entry(scm));
        return scm;
   }

    /**
     * @param lookup GroupID + "/" + ArtifactID
     * @return overriden SCM URI
     */
    private static String getOverride(String lookup) {
        for (Map.Entry<Pattern, String> entry : overrides.entrySet()) {
            Matcher m = entry.getKey().matcher(lookup);
            if (m.find()) {
                return m.replaceAll(entry.getValue());
            }
        }
        return null;
    }

    /**
     * This method tries to retrieve SCM URL, if POM model for given dependency does not specify SCM URL and
     * parent model belongs to the same group, we'll try to fecth URL from the parent model
     * @param dependency dependency to retrieve SCM URL for
     * @return SCM URL or null
     * @throws IOException
     * @throws XmlPullParserException
     */
    private static String getScmUrl(Dependency dependency) throws IOException, XmlPullParserException {
        Model model = fetchModel(dependency);
        while (model != null) {
            Scm scm = model.getScm();
            if (scm != null) {
                return scm.getUrl();
            }

            Parent parent = model.getParent();
            if (parent == null) {
                return null;
            }
            if (!StringUtils.equals(parent.getGroupId(), dependency.groupID)) {
                return null;
            }
            dependency = new Dependency(parent.getGroupId(),
                    parent.getArtifactId(),
                    parent.getVersion(),
                    null);
            model = fetchModel(dependency);
        }
        return null;
    }

    /**
     * Tries to fetch POM model from maven central for a given dependency
     * @param dependency dependency to fetch model to
     * @return POM model if found and valid
     * @throws IOException
     * @throws XmlPullParserException
     */
    private static Model fetchModel(Dependency dependency)
            throws IOException, XmlPullParserException {

        // Get the url to the POM file for this artifact
        String url = "http://central.maven.org/maven2/"
                + dependency.groupID.replace('.', '/') + '/' + dependency.artifactID + '/'
                + dependency.version + '/' + dependency.artifactID + '-' + dependency.version + ".pom";
        InputStream input = new BOMInputStream(new URL(url).openStream());

        MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
        Model model = xpp3Reader.read(input);
        input.close();
        return model;
    }

    private static class Entry {
        String uri;

        Entry() {
        }

        Entry(String uri) {
            this.uri = uri;
        }
    }

}