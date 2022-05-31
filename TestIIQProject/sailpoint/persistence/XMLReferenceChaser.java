/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * jsl - note that the name of this class is somewhat misleading.  This
 * does not look inside XML objects to find references, it uses the XML 
 * serialization annotations to guess at "child" objects that are wholey
 * owned by the parent object.
 *
 * If the property has an XML annotation and is *NOT* SerializationMode.REFERENCE,
 * SerializationMode.REFERENCE_LIST, or SerializationMode.REFERENCE_SET, then we
 * look to see if the value type or the collection type is a subclass
 * of SailPointObject using Class.isAssignableFrom.  If so, then we return it.
 *
 * One of the best examples of this is the List<Link> inside Identity.
 * I'm forgetting why this was necessary, shouldn't normal Hibernrate cascade
 * declarations handle this?
 * 
 *
 */

package sailpoint.persistence;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.Type;

import sailpoint.object.SailPointObject;
import sailpoint.tools.Reflection;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * Utility that takes a given SailPointObject annotated for XML serialization
 * and calculates the "hard" references (i.e. - associations and collections
 * of SailPointObjects that are owned by another object).
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class XMLReferenceChaser
{
    static private Log log = LogFactory.getLog(XMLReferenceChaser.class);

    /**
     * Return all "hard" references (i.e. - associations and collections of
     * SailPointObjects that are owned by another object) in the given
     * SailPointObject using the XMLProperty annotations.
     * 
     * @param  obj  The SailPointObject for which to retrieve references.
     * 
     * @return All "hard" references (i.e. - associations and collections of
     *         SailPointObjects that are owned by another object) in the given
     *         SailPointObject using the XMLProperty annotations.
     */
    public static Set<SailPointObject> getReferences(SailPointObject obj)
        throws Exception
    {
        Set<SailPointObject> references = new HashSet<SailPointObject>();

        if (log.isInfoEnabled())
            log.info("Getting references for " + obj.getClass().getName());

        BeanInfo info = Introspector.getBeanInfo(obj.getClass());
        for (PropertyDescriptor pd : info.getPropertyDescriptors())
        {
            log.info(pd.getName());

            // the annotation may be defined on either the getter or setter
            // though the property must have a getter to be considered
            Method getter = pd.getReadMethod();
            if (getter == null)
                continue;

            XMLProperty annotation = getter.getAnnotation(XMLProperty.class);
            if (annotation == null) {
                Method setter = pd.getWriteMethod();
                if (setter != null) 
                    annotation = setter.getAnnotation(XMLProperty.class);
            }

            // If this is NOT a reference type, collect the hard references.
            if (null != annotation)
            {
                SerializationMode mode = annotation.mode();
                if ((null != mode) && !mode.isReference() && !mode.ignoreReferences())
                {
                    Class propertyType = pd.getPropertyType();
                    Class listElementType =
                        Reflection.getListElementType(pd, String.valueOf(getter));
    
                    if ((null != propertyType) && SailPointObject.class.isAssignableFrom(propertyType))
                    {
                        SailPointObject spo = (SailPointObject)Reflection.getValue(getter, obj);
                        if (spo != null) {
                            if (log.isInfoEnabled())
                                log.info("Adding reference " + spo.getClass().getName() + 
                                         " : " + spo.getName());
                            
                            references.add(spo);
                        }
                    }
                    else if ((null != listElementType) && SailPointObject.class.isAssignableFrom(listElementType))
                    {
                        Collection<SailPointObject> collection =
                            (Collection<SailPointObject>) Reflection.getValue(getter, obj);
                        if (null != collection)
                        {
                            for (SailPointObject child : collection)
                            {
                                if (child != null) {
                                    if (log.isInfoEnabled())
                                        log.info("Adding reference " + child.getClass().getName() + 
                                                 " : " + child.getName());
                                    
                                    references.add(child);

                                    // jsl - we've done this for awhile now but it shouldn't 
                                    // be necessary, we'll just do the work again later when we
                                    // call saveInternal on the child !?
                                    //references.addAll(getReferences(child));
                                }
                            }
                        }
                    }
                }
            }
        }

        return references;
    }

    /**
     * jsl - this is a new method that looks for references inside XML blobs.
     * It isn't finished, but demonstrates how to use ClassMetadata to find XmlTypes.
     * This was developed while debugging the problem of XML objects containing
     * references to entities and a deepCopy on them causes the referenced objects
     * to be fetched in a funny state during a flush.  I ended up trying
     * to address that a different way (NewXmltype) so this code is no longer
     * used, but we need to revisit this since attachment isn't going
     * to cause the contained references to be in the session after the attach.
     * This is okay just to get the references persisted, but you couldn't
     * traverse through them.
     */
    public static Set<SailPointObject> getHiddenReferences(SessionFactory factory, SailPointObject obj)
        throws Exception
    {
        Set<SailPointObject> references = new HashSet<SailPointObject>();

        if (log.isInfoEnabled())
            log.info("Getting references for " + obj.getClass().getName());

        ClassMetadata meta = factory.getClassMetadata(Hibernate.getClass(obj));
        if (meta != null) {
            String[] props = meta.getPropertyNames();
            if (props != null) {
                for (String prop : props) {
                    Type type = meta.getPropertyType(prop);
                    if ("sailpoint.persistence.XmlType".equals(type.getName())) {

                        System.out.println("Property: " + prop + " type " + type.getName());

                    }
                }
            }
        }

        return references;
    }



}
