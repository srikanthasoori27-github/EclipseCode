/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;


/**
 * A marker annotation used by TracingAspect to flag classes that should not
 * have the TracingAspect woven into them.  Typically this is used either
 * because the class needs to be able to run in a constrained classpath (ie -
 * without the AspectJ jars) or to prevent recursion during tracing.
 */
public @interface Untraced {
}
