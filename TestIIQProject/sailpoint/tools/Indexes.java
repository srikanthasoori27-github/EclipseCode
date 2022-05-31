/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to create indexes that can't be described
 * using the normal hbm.xml notation.
 *
 * Here is a list of the situations that are hard to describe using the 
 * current hbm index notation.
 * 
 * 1) Create an index for an attribute defined in an entity across 
 *    ALL subclasses.
 * 
 * Example:
 * Lets say the following annotation is added to SailPointObject. The generator 
 * will go through all of our classes and find any subclasses of 
 * SailPointObject and create an index for the property on all subclasses.
 * 
 * @Indexes({@Index(property="scope",subClasses=true)});
 * GENERATES: (for each subclass)
 *   create index on spt_GENERATED_INDEX_NAME on spt_TABLENAME(column_name);
 *  
 * 2) Create an index for an attribute defined in a parent.hbm.xml file 
 *    which applies only to certain subclasses.
 * 
 * This is the similar the problem  in #1, but in this case we have an 
 * attribute like lastModified which  is an internal attribute used by  
 * our persistence layer that needs to be part of each object. In order 
 * to create an index we have to pull the references out of the parent 
 * hbm and into each child hbm file. 
 *  
 * Example:  Some subclasses use lastModified to compute the age of 
 * an object, including the Maintenance task which needs 
 * Certification.lastModified  to be indexed. 
 * 
 * @Indexes({@Index(property="lastModified", name="spt_certification_lastMod")})
 * GENERATES:
 *   create index spt_certification_lastMod on identityiq.spt_certification(lastModified);
 * 
 * 3) Composite indexes spanning attributes 
 * 
 * Example:  Application AND nativeIdentity property for Links for our 
 * standard correlation search and needs to also have a composite 
 * index for performance.
 *
 * @Indexes({@Index(property="application", name="spt_link_appId_composite"), 
 *           @Index(property="nativeIdentity",name="spt_link_appId_composite", 
 *                  caseSensitive=true)})
 *
 * NOTE: the indexes share an index name which will imply a composite index
 * GENERATES:
 *   create index spt_link_appId_composite on identityiq.spt_link (application,upper(native_identity));
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Indexes {
    /**
     * This object is just a container for the Index object where 
     * all of the data exists.
     */
    Index[] value();
}
