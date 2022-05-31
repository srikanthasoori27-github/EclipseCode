/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.launch;

import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;

import sailpoint.tools.Untraced;

/**
 * This is an extension of URLClassLoader that will find classes using the
 * order:<p>
 *     - Bootstrap (if 'bootstrapFirst' system property is not 'false')<p>
 *     - WEB-INF/classes and WEB-INF/lib/*.jar<p>
 *     - Bootstrap (if 'bootstrapFirst' system property is 'false')<p>
 *     - System
 */
@Untraced
public class WebInfClassLoader extends URLClassLoader {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A FilenameFilter that returns jars and zips.  This is not an anonymous
     * inner class so we can add the Untraced annotation.
     */
    @Untraced
    private static class JarFilter implements FilenameFilter {

        public boolean accept (File b, String name) {
            for ( String extension : new String[] { ".jar", ".zip" } ) {
                if ( name.toLowerCase().endsWith(extension) )
                    return true;
            }
            return false;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     *
     */
    private ClassLoader _system;

    /**
     * Bootstrap classloader loads classes from the JRE rt.jar
     */
    private ClassLoader _bootstrapClassloader;

    /**
     *
     */
    private boolean _debug;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // IMPLEMENTATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     *
     *
     */
    public WebInfClassLoader() {
        this(false);
    }

    public WebInfClassLoader(boolean debug) {
            // initialize ourself as parentless
        super(new URL[0], null);
        _debug = debug;
        _system = getSystemClassLoader();
        _bootstrapClassloader = getBootstrapClassLoader();

        initClassPath();
    }

    /**
     *
     * @param debug
     */
    public void setDebug(boolean debug) {
        _debug = debug;
    }

    /**
     *
     * @return
     */
    public boolean isDebug() {
        return _debug;
    }

    /**
     *
     * @param o
     */
    private void debugPrint(Object o) {
        if ( _debug )
            System.err.println(o);
    }

    /**
     *
     *
     */
    private void initClassPath() {

            // try to figure out the root of our web application
        URL url =
               getClass().getProtectionDomain().getCodeSource().getLocation();

            // if our url is not file based, then we don't know what to do
        if ( ! url.getProtocol().equals("file") ) {
            debugPrint(url + " is not a file URL.");
            return;
        }

        String urlBase = "";
        int i = url.toString().indexOf("WEB-INF");
        if ( i > 0 ) {
            urlBase = url.toString();
            urlBase = urlBase.substring(0, i);
        }

        debugPrint("urlBase = " + urlBase);

        String filePathBase = url.getPath();
        i = filePathBase.indexOf("WEB-INF");
        if ( i > 0 ) {
            filePathBase = filePathBase.substring(0, i);
        }

        debugPrint("filePathBase = " + filePathBase);

        try {
                // trailing '/' is important here, see URLClassLoader JavaDoc
            URL u = new URL(urlBase + "WEB-INF/classes/");
            super.addURL(u);
            debugPrint("classes = " + u);
        } catch ( MalformedURLException ex ) {
            System.err.println("Unable create URL for WEB-INF/classes.");
        }

        String fileEncoding = System.getProperty("file.encoding");
        debugPrint("Using encoding " + fileEncoding + " to decode URL.");
        String filePath = filePathBase + "WEB-INF/lib";
        debugPrint("WEB-INF/lib before decode = " + filePath);
        try {
            filePath = URLDecoder.decode(filePath, fileEncoding);
        } catch ( UnsupportedEncodingException ex ) {
            System.err.println("Encoding " + fileEncoding +
                                                " unsupported.  Using UTF-8");
            try {
                filePath = URLDecoder.decode(filePath, "UTF-8");
            } catch ( UnsupportedEncodingException ex2 ) {
                System.err.println("UTF-8 encoding not supported. " +
                                            "No decoding will be performed.");
            }
        }
        debugPrint("WEB-INF/lib after decode = " + filePath);

        File webInfLib = new File(filePath);
        if ( ! webInfLib.exists() ) {
            System.err.println(filePath + " does not exist.");
        }

        File[] jarFiles = webInfLib.listFiles(new JarFilter());

        debugPrint("jarFiles = ");

        if ( jarFiles != null ) {
            for ( File jar : jarFiles ) {
                debugPrint("  " + jar.getAbsolutePath());
                try {
                    super.addURL(jar.toURI().toURL());
                } catch ( MalformedURLException ex ) {
                    System.err.println("Unable to create URL for '" +
                                                jar.getAbsolutePath() + "'.");
                }
            }  // for jar ... jarFiles
        }  // if jarFiles

    }  // initClassPath()

	/**
     * Method added for customClassLoader to find already loaded classes by WebInfClassLoader
     * @param name
     * @return
     */
    public Class<?> findAlreadyLoadedClass(String name) {
        return findLoadedClass(name);
    }
	
    /**
     *
     */
    protected synchronized Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {

        // First, check if it's already loaded
        Class<?> clazz = findLoadedClass(name);
        debugPrint("Class '" + name + "' already loaded.");

        if ( clazz == null ) {
            try {
                if (bootstrapFirst()) {
                    // Next, try to find in bootstrap classloader
                    clazz = loadfromBootstrap(name);
                }

                if (clazz == null) {
                    // Next, try to find it ourself
                    clazz = findClass(name);
                    debugPrint("Found class '" + name + "'.");
                }
            } catch ( ClassNotFoundException ex ) {
                // Finally, ask the system classloader
                if ( _system != null ) {
                    clazz = _system.loadClass(name);
                    debugPrint("System found class '" + name + "'.");
                } else {
                    debugPrint("Class '" + name + "' not found.");
                    // if there is no parent, then propogate the exception
                    throw ex;
                }
            }
        }

        if ( resolve ) resolveClass(clazz);

        return clazz;
    }

    /**
     * @return the bootstrap classLoader
     */
    private ClassLoader getBootstrapClassLoader() {
        ClassLoader bs_cl = String.class.getClassLoader();
        if (bs_cl == null) {
            bs_cl = getSystemClassLoader();
            while (bs_cl.getParent() != null) {
                bs_cl = bs_cl.getParent();
            }
        }
        return bs_cl;
    }

    /**
     * @return true if the 'bootstrapFirst' system property is not set to 'false''
     */
    private boolean bootstrapFirst() {
        String flag = System.getProperty("bootstrapFirst", "true");
        return !"false".equalsIgnoreCase(flag);
    }

    /**
     * Attempt to load the given class from the bootstrap classloader.  This is borrowed
     * from Apache Tomcat webapp classloader.
     * @param name the class to load
     * @return null if not found by bootstrap classloader, otherwise returns the Class
     */
    private Class<?> loadfromBootstrap(String name) {
        Class<?> clazz = null;

        String resourceName = classNameToResourcePath(name, false);

        boolean tryLoadingFromBootstrapLoader;
        try {
            // Use getResource as it won't trigger an expensive
            // ClassNotFoundException if the resource is not available from
            // the Java SE class loader. However (see
            // https://bz.apache.org/bugzilla/show_bug.cgi?id=58125 for
            // details) when running under a security manager in rare cases
            // this call may trigger a ClassCircularityError.
            tryLoadingFromBootstrapLoader = (_bootstrapClassloader.getResource(resourceName) != null);
        } catch (ClassCircularityError cce) {
            // The getResource() trick won't work for this class. We have to
            // try loading it directly and accept that we might get a
            // ClassNotFoundException.
            tryLoadingFromBootstrapLoader = true;
        }

        if (tryLoadingFromBootstrapLoader) {
            try {
                clazz = _bootstrapClassloader.loadClass(name);
                if (clazz != null) {
                    debugPrint("Bootstrap found class '" + name + "'.");
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }

        return clazz;
    }

    /**
     * Convert a class name to a filename path
     * @param className the class name
     * @param addLeadingSlash if true, prepend a leading slash
     * @return
     */
    private String classNameToResourcePath(String className, boolean addLeadingSlash) {
        // 1 for leading '/', 6 for ".class"
        StringBuilder path = new StringBuilder(7 + className.length());
        if (addLeadingSlash) {
            path.append('/');
        }
        path.append(className.replace('.', '/'));
        path.append(".class");
        return path.toString();
    }

    /**
     *
     */
    public final URL getResource(final String name) {
        URL resource = findResource(name);

        if ( resource == null ) {
            if ( _system != null ) {
                resource = _system.getResource(name);
            }
        }

        return resource;
    }
}  // class WebInfClassLoader
