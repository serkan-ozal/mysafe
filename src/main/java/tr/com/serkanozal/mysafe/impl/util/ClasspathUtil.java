package tr.com.serkanozal.mysafe.impl.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Utility class to find URL entries in classpath.
 *
 * @author Serkan OZAL
 */
public final class ClasspathUtil {

    private ClasspathUtil() {

    }

    public static Set<URL> findClasspathUrls(ClassLoader classLoader) {
        Set<URL> urls = new HashSet<URL>();

        try {
            String[] classpathProperties =
                    System.getProperty("java.class.path").split(File.pathSeparator);
            for (String classpathProperty : classpathProperties) {
                urls.add(new File(classpathProperty).toURI().toURL());
            }
            urls.addAll(getExtURLs(getExtDirs()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String surefireProperty = System.getProperty("surefire.test.class.path");
        if (surefireProperty != null && surefireProperty.trim().length() > 0) {
            try {
                String[] surefireClasspathProperties =
                        surefireProperty.split(File.pathSeparator);
                for (String surefireClasspathProperty : surefireClasspathProperties) {
                    urls.add(new File(surefireClasspathProperty).toURI().toURL());
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        // Also start with this classes's loader, in some environment this can
        // be different than the current thread's one
        for (ClassLoader loader = classLoader; loader != null; loader = loader.getParent()) {
            urls.addAll(findClasspathsByLoader(loader));
            loader = loader.getParent();
        }

        Map<URL, URL> replaceURLs = new HashMap<URL, URL>();
        Set<URL> derivedUrls = new HashSet<URL>();
        for (URL url : urls) {
            if (url.getProtocol().startsWith("vfs")) {
                try {
                    URLConnection conn = url.openConnection();
                    Object virtualFile = conn.getContent();
                    if (virtualFile.getClass().getName()
                            .equals("org.jboss.vfs.VirtualFile")) {
                        File file =
                                (File) virtualFile.getClass()
                                        .getMethod("getPhysicalFile")
                                        .invoke(virtualFile);
                        String fileName = file.getCanonicalPath();
                        String name =
                                (String) virtualFile.getClass()
                                        .getMethod("getName")
                                        .invoke(virtualFile);
                        name = name.trim().toLowerCase();
                        if ((name.endsWith("jar") || name.endsWith("zip")
                                && fileName.endsWith("/contents"))) {
                            fileName = fileName.replace("contents", name);
                        }
                        URL repURL = new URL("file:/" + fileName);
                        replaceURLs.put(url, repURL);
                    }
                } catch (Exception e) {
                    // We don't expect to trapped here
                    e.printStackTrace();
                }
            }
            try {
                if (url.toExternalForm().endsWith("WEB-INF/classes")) {
                    derivedUrls.add(
                            new URL(url.toExternalForm()
                                    .replace("WEB-INF/classes", "WEB-INF/lib")));
                } else if (url.toExternalForm().endsWith("WEB-INF/classes/")) {
                    derivedUrls.add(
                            new URL(url.toExternalForm()
                                    .replace("WEB-INF/classes/", "WEB-INF/lib/")));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        urls.removeAll(replaceURLs.keySet());
        urls.addAll(replaceURLs.values());
        urls.addAll(derivedUrls);
        replaceURLs.clear();
        // Check contained urls
        for (URL url : urls) {
            for (URL rootUrl : urls) {
                if (url.equals(rootUrl)) {
                    continue;
                }
                if (url.toExternalForm().startsWith(rootUrl.toExternalForm())) {
                    if (replaceURLs.get(url) != null) {
                        URL settledUrl = replaceURLs.get(url);
                        if (settledUrl.toExternalForm()
                                .startsWith(rootUrl.toExternalForm())) {
                            replaceURLs.put(url, rootUrl);
                        }
                    } else {
                        replaceURLs.put(url, rootUrl);
                    }
                }
            }
        }
        urls.removeAll(replaceURLs.keySet());
        return urls;
    }

    private static Set<URL> findClasspathsByLoader(ClassLoader loader) {
        Set<URL> urls = new HashSet<URL>();
        if (loader instanceof URLClassLoader) {
            URLClassLoader urlLoader = (URLClassLoader) loader;
            urls.addAll(Arrays.asList(urlLoader.getURLs()));
        } else {
            Enumeration<URL> urlEnum;
            try {
                urlEnum = loader.getResources("");
                while (urlEnum.hasMoreElements()) {
                    URL url = urlEnum.nextElement();
                    if (url.getProtocol().startsWith("bundleresource")) {
                        continue;
                    }
                    urls.add(url);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return urls;
    }

    protected static File[] getExtDirs() {
        String extDirsProperty = System.getProperty("java.ext.dirs");
        File[] extDirs;
        if (extDirsProperty != null) {
            StringTokenizer st = new StringTokenizer(extDirsProperty, File.pathSeparator);
            int count = st.countTokens();
            extDirs = new File[count];
            for (int i = 0; i < count; i++) {
                extDirs[i] = new File(st.nextToken());
            }
        } else {
            extDirs = new File[0];
        }
        return extDirs;
    }

    protected static Set<URL> getExtURLs(File[] dirs) throws IOException {
        Set<URL> urls = new HashSet<URL>();
        for (int i = 0; i < dirs.length; i++) {
            String[] files = dirs[i].list();
            if (files != null) {
                for (int j = 0; j < files.length; j++) {
                    if (!files[j].equals("meta-index")) {
                        File f = new File(dirs[i], files[j]);
                        urls.add(f.toURI().toURL());
                    }
                }
            }
        }
        return urls;
    }

}
