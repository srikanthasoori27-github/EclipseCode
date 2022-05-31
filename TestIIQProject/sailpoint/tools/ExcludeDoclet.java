/**
 * This is a hack to the standard Javadoc Doclet to provide
 * an @exclude tag to exclude classes, packages and methods
 * from the generated HTML docs.
 *
 * As an aside, an @exclude tag has been the number one
 * enhancement request for Javadoc since 1997.  That's
 * right, people have been complaining about this for 12 YEARS!
 * If I still worked for Sun I would find the guy responsible
 * for this and belittle him.  Strenuously.
 *
 * The source was found here:

 *   http://www.sixlegs.com/blog/java/exclude-javadoc-tag.html
 *
 * The author states on his website "It's in the public domain,
 * so go crazy".
 *
 * Someone else apparently took this and dressed it up a little
 * with an LGPL license:
 *
 *   http://developer.berlios.de/projects/padoclet/
 * 
 * Not sure what the differences are, but I'm going with the 
 * original for now.
 * 
 * There was a block comment about needing to override languageVersion
 * to support Java 5 features like generic return types.  I added that.
 *
 * There are some blog comments about people having problems
 * with this in JDK 6 and they posted some fixes.  This version
 * is the original, I did not include the proposed fixes
 * since I'm not sure if they're backward compatible.
 *
 * Basically what this does is wrap everything in the DocRoot
 * model in a dynamic proxy class created by java.lang.reflect.Proxy.
 * so it can intercept arrays of Doc's and filter them.
 * 
 * There is some disturbing special case proxy unwrapping
 * hackery down in ExcludeHandler.invoke that I don't
 * fully understand.  
 * 
 * To use this add these to the javadoc ant task:
 *   doclet="sailpoint.tools.ExcludeDoclet"
 *   docletpath='${build}/WEB-INF/classes'
 *
 */

package sailpoint.tools;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.tools.doclets.standard.Standard;
import com.sun.tools.javadoc.Main;

public class ExcludeDoclet
{
    public static void main(String[] args)
    {
        String name = ExcludeDoclet.class.getName();
        Main.execute(name, name, args);
    }

    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    public static boolean validOptions(String[][] options, DocErrorReporter reporter)
    throws java.io.IOException
    {
        return Standard.validOptions(options, reporter);
    }
    
    public static int optionLength(String option)
    {
        return Standard.optionLength(option);
    }
        
    public static boolean start(RootDoc root)
    throws java.io.IOException
    {
        fixTheModel(root);
        return Standard.start((RootDoc)process(root, RootDoc.class));
    }
    
    /**
     * To my great surprise, we're allowed to mess with the raw comment
     * text and that somehow makes it's way into the HTML output.
     * I thought parsing would have been done by now, but the
     * javadoc/doclet code is just to horrible to look at for very long.
     * 
     * There are two hacks we're accomplishing here:
     *
     *   - identify empty lines between paragraphs of comments and
     *     insert a <p> which is annoying and no one ever remembers it
     *
     *   - look for an @ignore tag and remove it and everything after it
     *     this is used to strip design notes from the published docs
     *
     * jsl
     */
    private static void fixTheModel(RootDoc root) {
        ClassDoc[] classes = root.classes();
        if (classes != null) {
            for (int c = 0 ; c < classes.length ; c++) {
                ClassDoc cd = classes[c];
                //println("Class: " + cd.name());
                fixDoc(cd);
                fixDocs(cd.fields());
                fixDocs(cd.enumConstants());
                fixDocs(cd.constructors());
                fixDocs(cd.methods());
            }
        }
    }

    private static void fixDoc(Doc doc) {
        String raw = doc.getRawCommentText();
        doc.setRawCommentText(fixText(raw));
    }

    private static void fixDocs(Doc[] things) {
        if (things != null) {
            for (int i = 0 ; i < things.length ; i++) {
                Doc thing = things[i];
                fixDoc(thing);  
            }
        }
    }

    private static StringBuilder b = new StringBuilder();

    private static String fixText(String raw) {
        String fixed = raw;
        if (raw != null) {

            // first strip off the ignored portion
            int ignore = raw.indexOf("@ignore");
            if (ignore >= 0)
                fixed = raw.substring(0, ignore);

            // promote blank lines to <p>'s, skipping
            // over <pre> blocks
            b.setLength(0);
            boolean startedTags = false;
            int pendingNewlines = 0;
            int psn = 0;
            while (psn < fixed.length()) {
                char ch = fixed.charAt(psn++);
                if (startedTags) {
                    b.append(ch);
                }
                else if (ch == '@') {
                    if (pendingNewlines > 0) {
                        b.append("\n\n");
                        startedTags = true;
                    }
                    b.append(ch);
                }
                else if (ch == '\n') {
                    pendingNewlines++;
                }
                else if (Character.isWhitespace(ch)) {
                    if (pendingNewlines == 0)
                        b.append(ch);
                }
                else {
                    if (pendingNewlines == 1)
                        b.append("\n");
                    else if (pendingNewlines > 1)
                        b.append("\n<p>\n");
                    b.append(ch);
                    pendingNewlines = 0;
                    if (ch == '<' && fixed.indexOf("pre>", psn) == psn) {
                        int endpre = fixed.indexOf("</pre>", psn);
                        if (endpre > 0) {
                            int nextpsn = endpre + 7;
                            b.append(fixed.substring(psn, nextpsn));
                            psn = nextpsn;
                        }
                        // else, malformed ignore
                    }
                }
            }

            fixed = b.toString();
            /*
            if (!raw.equals(fixed)) {
                println("----converted this---");
                println(raw);
                println("---to this---");
                println(fixed);
                println("-------");
            }
            */
        }

        return fixed;
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    private static boolean exclude(Doc doc)
    {
        if (doc instanceof ProgramElementDoc) {
            if (((ProgramElementDoc)doc).containingPackage().tags("exclude").length > 0)
                return true;
        }
        return doc.tags("exclude").length > 0;
    }

    @SuppressWarnings("unchecked")
    private static Object process(Object obj, Class expect)
    {
        if (obj == null)
            return null;
        Class cls = obj.getClass();
        if (cls.getName().startsWith("com.sun.")) {
            return Proxy.newProxyInstance(cls.getClassLoader(),
                                          cls.getInterfaces(),
                                          new ExcludeHandler(obj));
        } else if (obj instanceof Object[]) {
            Class componentType = expect.getComponentType();
            Object[] array = (Object[])obj;
            List list = new ArrayList(array.length);
            for (int i = 0; i < array.length; i++) {
                Object entry = array[i];
                if ((entry instanceof Doc) && exclude((Doc)entry))
                    continue;
                list.add(process(entry, componentType));
            }
            return list.toArray((Object[])Array.newInstance(componentType, list.size()));
        } else {
            return obj;
        }
    }

    private static class ExcludeHandler
    implements InvocationHandler
    {
        private Object target;
        
        public ExcludeHandler(Object target)
        {
            this.target = target;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable
        {
            String methodName = method.getName();
            if (args != null) {
                if (methodName.equals("compareTo") ||
                    methodName.equals("equals") ||
                    methodName.equals("overrides") ||
                    methodName.equals("subclassOf")) {
                    args[0] = unwrap(args[0]);
                }
            }

            try {
                Object value = method.invoke(target, args);

                // jsl - tried to find out where the comment text was returned
                // so we could hack on it but commentText is never called,
                // continue someday...
                /*
                if (target instanceof ClassDoc) {
                    if (value != null)
                        System.out.println("------------\n" + methodName + "\n" + value.toString());
                }
                */

                return process(value, method.getReturnType());
            } 
            catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        private Object unwrap(Object proxy)
        {
            if (proxy instanceof Proxy)
                return ((ExcludeHandler)Proxy.getInvocationHandler(proxy)).target;
            return proxy;
        }
    }
}
