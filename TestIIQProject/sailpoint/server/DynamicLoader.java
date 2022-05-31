/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A utility for dynamically loading Java classes.
 * This is built upon sailpoint.tools.JavaLoader which
 * is in turn based upon a public domain IBM Developer Works
 * utility called CharSequenceCompiler.  
 *
 * To JavaLoader we add awareness of Rule objects which contain
 * the source and modification dates.
 *
 * Author: Jeff
 *
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;

import java.net.URL;
import java.net.URLDecoder;


import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.CharSequenceCompiler;
import sailpoint.tools.CharSequenceCompilerException;


public class DynamicLoader {

    private static Log log = LogFactory.getLog(DynamicLoader.class);

    /**
     * Information about previously loaded classes.
     */
    static class ClassInfo {

        /**
         * The Class we loaded.
         */
        public Class cls;

        /**
         * True if this was dynamically loaded.
         */
        public boolean dynamic;

        /**
         * The last modification date of the object containing
         * the dynamically loaded class.  
         */
        public Date modified;

    };

    /**
     * Cache of classes we've loaded.  In order to dynamically track
     * changes to Java stored in Rules we have to check the Rule first.
     * To avoid database hits, we use this table to remember whether
     * this class was in fact dynamic or just part of the .jar.  It also
     * has the last modification date of the Rule so we don't reload
     * unless the Rule has changed.
     */
    static Map<String,ClassInfo> _classes = new HashMap<String,ClassInfo>();

    /**
     * Given a class name, find it with optional dynamic loading.
     * This can be used anywhere you would ordinarilly use Class.forName 
     * allowing dynamic loading of things such as WorkflowLibrary, 
     * Connector, or TaskExecutor.
     */
    static public Class classForName(SailPointContext context, String className)
        throws ClassNotFoundException {
        
        Class cls = null;

        ClassInfo info = getInfo(className);
        
        if (info != null && !info.dynamic) {
            // a normal class we've seen before
            cls = info.cls;
            if (cls == null) {
                // cache was cleeared, shouldn't happen?
                cls = Class.forName(className);
                info.cls = cls;
            }
        }
        else if (info != null) {
            // a Rule we've seen before
            boolean reload = false;
            if (info.cls == null) {
                // cache was cleared
                reload = true;
            }
            else {
                // reload only if the Rule changed
                // could have an option to disable this or do it less often?
                try {
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("name", className));
                    List<String> props = new ArrayList<String>();
                    props.add("modified");
                    Iterator<Object[]> result = context.search(Rule.class, ops, props);
                    if (result == null || !result.hasNext()) {
                        // didn't find a matching rule, it could have been deleted, should
                        // we continue returning the previously loaded class or start throwing?
                        log.warn("Previously loaded Java Rule no longer exists: " + className);
                    }
                    else {
                        Object[] row = result.next();
                        Date modified = (Date)row[0];
                        // modified can be null if was newly created
                        if (modified != null && modified.after(info.modified)) {
                            reload = true;
                        }
                        else if (log.isInfoEnabled()) {
                            log.info("Reusing cached class: " + className);
                        }
                    }
                }
                catch (Throwable t) {
                    // problem with the query, just reuse the last one
                    log.error("Unable to read Java Rule modification date: " + className, t);
                }
            }

            if (reload)
                info = loadRule(context, className, info);
            
            cls = info.cls;
        }
        else {
            // We don't know what this is yet, favor rules or classes?
            // In threoy we may need to synthaonize here incase several
            // threads try to load this class at the same time?  
            // CharSequenceCompiler will synchronize but we may end
            // up doing the same work twice which is wasteful.
            info = loadRule(context, className, null);
            if (info != null) {
                addInfo(className, info);
                cls = info.cls;
            }
        }

        // If we end up here nothing to show, throw ClassNotFound
        // Don't call Class.forName at this point since we may be focing
        // this one to use a Rule that's broken
        if (cls == null)
            throw new ClassNotFoundException(className);

        return cls;
    }

    /**
     * Alternative interface for classForName that only
     * throws GeneralException.  Makes it easier to drop this
     * into older code.
     */
    static public Class getClass(SailPointContext context, String className)
        throws GeneralException {

        Class cls = null;

        if (className == null)
            throw new GeneralException("Missing class name");

        try {
            cls = classForName(context, className);
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }

        return cls;
    }

    /**
     * Resolve the class and create an instance.  Throws only GeneralException
     * so it ca be used more easily in older code.
     */
    static public Object instantiate(SailPointContext context, String className)
        throws GeneralException {

        Object obj = null;
        Class cls = getClass(context, className);

        try {
            obj = cls.newInstance();
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }
        return obj;
    }

    /**
     * Look for a prevoiusly loaded class.
     */
    static private synchronized ClassInfo getInfo(String name) {
        return _classes.get(name);
    }

    static private synchronized void addInfo(String name, ClassInfo info) {
        _classes.put(name, info);
    }

    /**
     * Load a Rule, recompile the source, and load the Class.
     */
    static private ClassInfo loadRule(SailPointContext context, String name, ClassInfo info)
        throws ClassNotFoundException {

        if (log.isInfoEnabled())
            log.info("Reading rule: " + name);

        Rule rule = null;
        try {
            rule = context.getObjectByName(Rule.class, name);
        }
        catch (Throwable t) {
            // something wrong with the db, log but this is 
            // just the start of our problems
            log.error("Unable to load Rule: " + name, t);
        }

        if (rule == null) {
            if (info != null) { 
                // should only get here if the get above failed, or if
                // the rule was deleted which we've already logged
                // continue using the previous version
            }
            else {
                // must be an ordinary class
                Class cls = Class.forName(name);
                // if we didn't throw intern it
                info = new ClassInfo();
                info.cls = cls;
                if (log.isInfoEnabled()) 
                    log.info("Caching static class: " + name);
            }
        }
        else {
            Class cls = load(name, rule.getSource());
            if (cls == null) {
                // Something wrong with the source
                // If we've already loaded this continue using the last version
                // otherwise throw
                if (info == null)
                    throw new ClassNotFoundException(name);
            }
            else {
                if (info == null) {
                    // first time we've loaded this Rule
                    info = new ClassInfo();
                    info.dynamic = true;
                    if (log.isInfoEnabled())
                        log.info("Caching dynamic class: " + name);
                }
                info.cls = cls;
                Date mod = rule.getModified();
                if (mod == null)
                    mod = rule.getCreated();
                // don't think this can happen?
                if (mod == null)
                    mod = new Date();
                info.modified = mod;
            }
        }

        return info;
    }

    /**
     * Compile and load a Java class using CharSequenceCompiler.
     * Log errors if we get them but do not throw.
     */
    static private Class load(String name, String source) {

        Class cls = null;
        
        if (log.isInfoEnabled())
            log.info("Compiling class: " + name);

        // create an instance of the compiler, I don't know if these are
        // expensive to create, we could cache it but this class is not
        // thread safe so there would need to be synchronization around
        // calls to load() or keep in a thread local
        // UPDATE: Actually it looks like the compile() method is synchronized
        // so we could just keep one

        // command line options
        List<String> options = new ArrayList<String>();
        options.add("-cp");
        options.add(getClassPath());

        // This one can see sailpoint classes but not the libraries
        ClassLoader cl = DynamicLoader.class.getClassLoader();
        
        // neither can this...
        //URLClassLoader cl = (URLClassLoader)Thread.currentThread().getContextClassLoader();

        /*
        System.out.println("Using ClassLoader: " + cl);
        try {
            WebInfClassLoader wicl = (WebInfClassLoader)cl;
            Class apclass = wicl.loadClassPublic("org.apache.commons.logging.Log", false);
            if (apclass != null)
                System.out.println("Class found!");
        }
        catch (Throwable t) {
            System.out.println(t);
        }
        System.out.println("ClassLoader: " + cl);
        System.out.println(cl.findResource("org.apache.commons.logging.Log"));
        */

        // This is our custom one that understands WebInf
        // this causes all sorts of mulitple load problems
        //ClassLoader cl = new WebInfClassLoader();

        CharSequenceCompiler compiler = new CharSequenceCompiler(cl, options);
            
        // this gathers compiler errors
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<JavaFileObject>();

        try {
            cls = compiler.compile(name, source, errors);
        } 
        catch (CharSequenceCompilerException e) {
            log.error("Unable to compile: " + name);
            StringBuilder msgs = new StringBuilder();
            msgs.append(e.getMessage());
            List<Diagnostic<? extends JavaFileObject>> diags = e.getDiagnostics().getDiagnostics();
            if (diags != null) {
                msgs.append("\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diags) {
                    msgs.append(diagnostic.getMessage(null)).append("\n");
                }
            }
            log.error(msgs.toString());
        }
        catch (Throwable t) {
            // TOOD: Need error reporting
            log.error("Unable to compile: " + name, t);
        }

        return cls;
    }

    /**
     * Build the classpath to pass to the compiler.
     * This was drived from code in launch/WebInfClassLoader, could
     * try to refadtor this and share but the reults are different, here
     * we just want a list of names we can passed as -cp, there it loads
     * File objects and gives them to URLClassLoader.
     */
    static private String getClassPath() {

        String path = null;

        // try to figure out the root of our web application
        URL url = DynamicLoader.class.getProtectionDomain().getCodeSource().getLocation();

        // if our url is not file based, then we don't know what to do
        if ( ! url.getProtocol().equals("file") ) {
            log.error(url + " is not a file URL.");
        }
        else {
            StringBuffer pathbuf = new StringBuffer();

            String filePathBase = url.getPath();
            int i = filePathBase.indexOf("WEB-INF");
            if ( i > 0 )
                filePathBase = filePathBase.substring(0, i);

            if (log.isInfoEnabled())
                log.info("filePathBase = " + filePathBase);

            // add /classes to override identityiq.jar
            pathbuf.append(filePathBase + "WEB-INF/classes/");
            
            // locate the WEB-INF/lib directory
            String fileEncoding = System.getProperty("file.encoding");
            if (log.isInfoEnabled())
                log.info("Using encoding " + fileEncoding + " to decode URL.");

            String filePath = filePathBase + "WEB-INF/lib";
            if (log.isInfoEnabled())
                log.info("WEB-INF/lib before decode = " + filePath);

            try {
                filePath = URLDecoder.decode(filePath, fileEncoding);
            } 
            catch ( UnsupportedEncodingException ex ) {
                log.error("Encoding " + fileEncoding + " unsupported.  Using UTF-8");
                try {
                    filePath = URLDecoder.decode(filePath, "UTF-8");
                } catch ( UnsupportedEncodingException ex2 ) {
                    log.error("UTF-8 encoding not supported. No decoding will be performed.");
                }
            }
            
            if (log.isInfoEnabled())
                log.info("WEB-INF/lib after decode = " + filePath);

            File webInfLib = new File(filePath);
            if ( ! webInfLib.exists() ) {
                log.error(filePath + " does not exist.");
            }

            File[] jarFiles = webInfLib.listFiles(new JarFilter());

            if (jarFiles != null) {
                for (File jar : jarFiles) {
                    pathbuf.append(File.pathSeparatorChar);
                    pathbuf.append(jar.getAbsolutePath());
                }
            }

            path = pathbuf.toString();
            if (log.isInfoEnabled())
                log.info(path);
        }

        return path;
    }

    /**
     * A FilenameFilter that returns jars and zips.  
     */
    public static class JarFilter implements FilenameFilter {

        static String[] Extensions = { ".jar", ".zip" };

        public boolean accept (File b, String name) {
            for (String extension : Extensions) {
                if ( name.toLowerCase().endsWith(extension) )
                    return true;
            }
            return false;
        }
    }

}

