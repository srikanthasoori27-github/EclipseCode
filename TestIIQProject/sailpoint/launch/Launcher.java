/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.launch;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import sailpoint.tools.Untraced;

/**
 * This class needs to stay self-contained and very well behaved.  We do not
 * want any classes loaded before our custom ClassLoader is in place or there
 * will be conflicts caused by a class being loaded by two different class
 * loaders.
 */
@Untraced
public class Launcher {

    //////////////////////////////////////////////////////////////////////
    //
    // Aliases
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * These define a set of built-in application classes that we
     * can call with simpler names.
     */
    @Untraced
    public static class Alias {
        public String name;
        public String className;
        public String help;
        public Alias(String n, String cn, String h) {
            name = n;
            className = cn;
            help = h;
        }
    }

    static final Alias ALIAS_SCHEMA = 
    new Alias("schema", "sailpoint.server.SchemaGenerator",
              "Schema file generator");

    static final Alias ALIAS_EXTSCHEMA = 
    new Alias("extendedSchema", "sailpoint.server.ExtendedSchemaGenerator",
              "Extended attribute delta schema generator");

    static final Alias ALIAS_UPGRADE = 
    new Alias("upgrade", "sailpoint.server.upgrade.Upgrader",
              "Perform data upgrades between releases");

    static final Alias ALIAS_PATCH = 
    new Alias("patch", "sailpoint.server.upgrade.Patcher",
              "Perform data upgrades post release");

    static final Alias ALIAS_CONSOLE = 
    new Alias("console", "sailpoint.server.SailPointConsole",
              "Administrative console");

    static final Alias ALIAS_ENCRYPT = 
    new Alias("encrypt", "sailpoint.server.Encryptor",
              "IdentityIQ encryptor");

    static final Alias ALIAS_INTEGRATION = 
    new Alias("integration", "sailpoint.integration.IntegrationConsole",
              "Integration console");

    static final Alias ALIAS_OIM = 
    new Alias("oim", "sailpoint.integration.oim.OIMConsole",
              "OIM Integration console");

    static final Alias ALIAS_DATA_EXPORT =
    new Alias("exportschema", "sailpoint.reporting.custom.DataExportConsole",
              "Data Export console");

    static final Alias ALIAS_KEY_CONSOLE = 
    new Alias("keystore", "sailpoint.server.KeyStoreConsole",
              "Key Store console");

    static final Alias ALIAS_PARSE = 
    new Alias("parse", "sailpoint.server.Parser",
              "XML file parser");

    static final Alias ALIAS_VIEW_EXPORT =
    new Alias("exportviews", "sailpoint.reporting.custom.ViewExportConsole",
              "View Export console");

    static final Alias ALIAS_DOC2HTML = 
    new Alias("doc2html", "sailpoint.tools.Doc2Html",
              "XML design document formatter");

    static final Alias[] ALIASES = {
        ALIAS_SCHEMA,
        ALIAS_EXTSCHEMA,
        ALIAS_UPGRADE,
        ALIAS_PATCH,
        ALIAS_CONSOLE,
        ALIAS_ENCRYPT,
        ALIAS_INTEGRATION,
        ALIAS_OIM,
        ALIAS_DATA_EXPORT,
        ALIAS_VIEW_EXPORT,
        ALIAS_KEY_CONSOLE,
        ALIAS_PARSE,
        ALIAS_DOC2HTML
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     *
     */
    private static boolean _debug = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     *
     * @param o
     */
    private static void debugPrint(Object o) {
        if ( _debug )
            System.err.println(o);
    }

    /**
     *
     * @param input
     * @return
     */
    private static String[] shiftArray(String[] input) {
        if ( input == null ) return new String[0];

        String[] output = new String[input.length - 1];

        System.arraycopy(input, 1, output, 0, input.length - 1);

        return output;
    }  // shiftArray(String[])

    /**
     *
     */
    private static void Usage() {
        System.err.println("Usage: " + Launcher.class.getName() +
                                           " [ -d ] [<application> | <class>] <arguments>...");
        System.err.println("Applications:");
        for (int i = 0 ; i < ALIASES.length ; i++) {
            Alias a = ALIASES[i];
            System.err.format("  %-15s %s\n", a.name, a.help);
        }
        System.exit(1);
    }

    private static String mapAlias(String name) {
        for (int i = 0 ; i < ALIASES.length ; i++) {
            Alias a = ALIASES[i];
            if (a.name.equals(name)) {
                name = a.className;
                break;
            }
        }
        return name;
    }

    /**
     * Use reflection to invoke sailpoint.tools.Util.setJavaLibraryPath()
     * 
     * @param cl
     *            classLoader to use to load the Util class
     */
    private static void setJavaLibraryPath(ClassLoader cl) {
        try {
            Class utilClass = cl.loadClass("sailpoint.tools.Util");
            Method m = utilClass
                    .getMethod("setJavaLibraryPath", (Class[]) null);
            m.invoke(null, (Object[]) null);

        } catch (Exception e) {// Do nothing
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // main
    //
    //////////////////////////////////////////////////////////////////////

    /**
     *
     * @param args
     */
    public static void main(String[] args) {

        if ( args == null || args.length == 0 )
            Usage();

        if ( "-d".equals(args[0]) ) {
            _debug = true;
            args = shiftArray(args);
            if ( args.length == 0 )
                Usage();
        }

        String className = mapAlias(args[0]);
        args = shiftArray(args);

        WebInfClassLoader cl = new WebInfClassLoader(_debug);
        debugPrint("Created new classloader.");
        Thread.currentThread().setContextClassLoader(cl);

        //Update java.library.path to include WEB-INF/lib
        setJavaLibraryPath(cl);

        Class c = null;
        try {
            c = cl.loadClass(className);
        } catch ( ClassNotFoundException ex ) {
            System.err.println("Class " + className + " not found");
            System.exit(1);
        }

        debugPrint("Loaded class '" + className + "'.");

        Method m = null;
        try {
            m = c.getMethod("main", new Class[] { args.getClass() });
        } catch ( NoSuchMethodException ex ) {
            System.err.println("Method 'main(String[])' not found in class '" +
                               c.getName() + "': " + ex.getLocalizedMessage());
            System.exit(1);
        }


        if ( m != null ) {
            debugPrint("Found main(String[]) method.");
            try {
                m.invoke(null, new Object[] { args });
            } catch ( InvocationTargetException ex ) {
                Throwable th = ex.getTargetException();
                System.err.println("Unable to invoke " + className +
                                      ".main(): " + th.getLocalizedMessage());
                th.printStackTrace(System.err);
                System.exit(1);
            } catch ( IllegalAccessException ex ) {
                System.err.println("Illegal access to " + className +
                                      ".main(): " + ex.getLocalizedMessage());
                ex.printStackTrace(System.err);
                System.exit(1);
            }
        }
    }  // main(String[])
}  // class Launcher
