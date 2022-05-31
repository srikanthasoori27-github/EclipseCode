/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A Hibernate UserType that stores arbitrary objects as XML blobs.
 * The object must extend AbstractXmlObject, or must be one of the
 * native types that XMLObjectFactory supports.  Most of the time
 * this means List.
 *
 * Author: Jeff, based on MapType by Kelly
 *
 * MAJOR ISSUE!!
 *
 * Detecting whether the user type value becomes dirty during a
 * transaction is our responsibility, it works like this:
 *
 *   1) When Hibernate brings the object into the session it
 *      calls deepCopy() to get a "snapshot".
 *
 *   2) When the transaction commits it calls equal() passing the
 *      shapshot generated in step 1 and the current value, often
 *      the current value is the same Java object that was the
 *      source of the deepCopy in step 1, but not always.
 *
 * The problem is that this needs to be a "deep equal".  If the values
 * are both List or Map and the contents are String or other simple types,
 * the default Object.equal does the job.
 *
 * But if it is one of our objects like ActivityConfig or a
 * collection of these objects that class must implement a deep equal()
 * method.  Currently many do not,
 *
 */
package sailpoint.persistence;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Date;
import java.util.List;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.xml.sax.SAXParseException;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.object.Attributes;

public class XmlType implements UserType
{
    static protected Log log = LogFactory.getLog(XmlType.class);

    /**
     * Returned by sqlTypes(), the column type we require for our value.
     */
    private static final int[] SQL_TYPES = {Types.CLOB};

    /**
     * Flag to enable using XML rather than a deep copy as
     * the "cached representation" for disassemble and assemble.
     *
     * Originally disassemble and assemble called deepCopy() to clone by
     * doing a full XML round trip.  Since the thing that gets put into
     * the 2nd level cache doesn't have to resemble the original
     * object we now just save the XML representation.  This saves an extra
     * parse when the object is put into the cache and an extra serializtion
     * when the object is brought out of the cache.
     */
    private static boolean CacheRepresentationXml = true;

    /**
     * Flag to enable "private contexts" when doing XML parsing in certain
     * situations.
     *
     * UPDATE: After all this work this causes a hang importing the demodata
     * in DB2 (but oddly enough nowhere else).  Trying to address this using
     * the PatchedDefaultFludhEventListener hack from the Groovy guys.
     */
    private static boolean AllowPrivateContexts = false;


    /**
     * Flag to enable some trace messages when debugging difference detection.
     */
    private static final boolean DebugDiffs = false;

    /**
     * Flag to disable using the simple clone and compare
     * functions. These functions perform comparisons and
     * cloning when the given XMLType instance contains
     * only simple java objects. This avoids expensive xml
     * serialization.
     *
     * Note: this stuff was added for the 5.1 performance
     * fixes and should be considered a quick fix. We plan to
     * introduce a more elegant fix in 5.2
     */

    private static final boolean UseSimpleCloning = true;

    /**
     * Return the column type we require for our value.
     */
    public int[] sqlTypes() {
        return SQL_TYPES;
    }

    /**
     * Documented as "The class returned by nullSaveGet()".
     * Not exactly sure how it is used but since we can represent
     * anything have to use Object.
     *
     * @see org.hibernate.usertype.UserType#returnedClass()
     */
    public Class returnedClass()
    {
        return Object.class;
    }

    /**
     * Documented as "Are objects of this type mutable?".
     * I'm guessing this means they can be modified while
     * still retaining the same Java object identity
     * (unlike String).
     *
     * @see org.hibernate.usertype.UserType#isMutable()
     */
    public boolean isMutable()
    {
        return true;
    }

    /**
     * Documented as "Get a hashcode for the instance, consistent
     * with persistence "equality"".
     *
     * @see org.hibernate.usertype.UserType#hashCode(java.lang.Object)
     */
    public int hashCode(Object x) throws HibernateException
    {
        return x.hashCode();
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        Object result = null;

        String xml = JdbcUtil.getClobValue(rs, names[0]);

        if (!rs.wasNull() && xml != null && xml.trim().length() > 0) {

            // wordy and not usually interesting
            //if (log.isInfoEnabled())
            //log.info("XmlType::nullSafeGet " + xml);

            result = parseXml("nullSafeGet", xml, false);
        }

        return result;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws HibernateException, SQLException {

        if (null == value)
        {
            // Storing null in oracle CLOBs seems to cause problems for hibernate.
            // See: http://opensource.atlassian.com/projects/hibernate/browse/HHH-2723
            // For now, let's store an empty string.
            String strVal = " ";
            if ( HibernatePersistenceManager.usingOracle() ) {
                Object clob=JdbcUtil.setOracleCLOBParameter(st, index, strVal);
                SailPointInterceptor.registerLOB(clob);
            } else {
                JdbcUtil.setClobParameter(st, index, strVal);
            }
        }
        else
        {
            if (log.isInfoEnabled())
                log.info("XmlType::nullSafeSet " + value.getClass().getSimpleName());

            XMLObjectFactory f = XMLObjectFactory.getInstance();
            String xml = f.toXmlNoIndent(value);

            if ((null == xml) || (0 == xml.length())) {
                xml = " ";
            }
            if ( HibernatePersistenceManager.usingOracle() ) {
                Object clob = JdbcUtil.setOracleCLOBParameter(st, index, xml);
                SailPointInterceptor.registerLOB(clob);
            } else {
                JdbcUtil.setClobParameter(st, index, xml);
            }
        }
    }

    /**
     * Rehydrate an object from XML.
     * When privateContext is true we will push a private SailPointContext
     * (which equates to a new Hibernate session) if we need to resolve referneces.
     * Setting this correctly is CRITICAL.
     *
     * This must be on for nullSafeGet() and assemble() because the resulting
     * object and all of it's references must be "in" the current session cache.
     * This must be off for deepCopy() which is used when making the "snapshots"
     * for change detection.  From what I've seen deepCopy() is called in these
     * places:
     *
     *    1) immediately after loading an object to get the initial snapshot
     *    2) immediately after flushing the session to regenerate the snapshots
     *       based on the current state of the objects just flushed
     *    3) somewhere in the object deletion process, I'm not sure why
     *    4) maybe after calling assemble() to bring something out of the
     *       2nd level cache, it would make sense but I haven't verified
     *       that since we don't use the 2nd level cache for much besides Application
     *
     * The 2nd case is the one that started causing lots of trouble after the Hibernate
     * upgrade 5.1.  What appears to happen is that during the session flush, the
     * deepCopy can cause new objects to be brought into the session, or prevous
     * objects to be marked dirty in some way.  There are some assertions in Hibernate
     * than then fail with my favorite error of all time:
     *
     * org.hibernate.AssertionFailure:45 - an assertion failure occured (this may indicate
     * a bug in Hibernate, but is more likely due to unsafe use of the session)
     * org.hibernate.AssertionFailure: collection [sailpoint.object.Application.activityDataSources]
     * was not processed by flush()
     *
     * Basically, Hibenrate isn't expecting to have any dirty objects or collections
     * (it always seems to be a collection) remaining in the session after the flush,
     * but the side effect of the deepCopy() violates this.  There have been
     * some forum posts that may be related to this:
     *
     * http://opensource.atlassian.com/projects/hibernate/browse/HHH-2763
     *
     * That one seems a little different because it is related to referencing
     * lazy loaded collections (like Application.activityDataSources) but just parsing
     * an XML reference to an Application for a deepCopy shouldn't touch the
     * activityDataSources list so I'm not sure it's exactly the same.
     *
     * Here's a suggestion from the Grails people:
     *
     * http://github.com/grails/grails-core/blob/master/src/java/org/codehaus/groovy/grails/orm/hibernate/events/PatchedDefaultFlushEventListener.java
     * This has been copied locally and can be tried as an alternative to creating
     * a private session, but since we'll still go through all the work of
     * XML serialization and parsing I'm not sure how significant the difference will be.
     */
    protected Object parseXml(String method, String xml, boolean privateContext)
        throws HibernateException {

        Object result = null;
        if (xml != null) {
            SailPointContext previous = null;
            try {
                if (AllowPrivateContexts && privateContext)
                    previous = SailPointFactory.pushContext();

                SailPointContext con = SailPointFactory.getCurrentContext();
                XMLObjectFactory f = XMLObjectFactory.getInstance();
                result = f.parseXml(con, xml, false);

                if (log.isInfoEnabled()) {
                    if (result != null)
                        log.info("XmlType::" + method + " " + result.getClass().getSimpleName());
                    else
                        log.info("XmlType::" + method + " null");
                }

                // old hack to remember original XML, not useful
                //if (SaveOriginalXml && (result instanceof PersistentXmlObject))
                //((PersistentXmlObject)result).setOriginalXml(xml);
            }
            catch (Error e) {
                // Something tragic has happened beyond the anticipated xml parsing
                // errors.  Let the Error fly.
                throw e;
            }
            catch (Throwable t) {
                // UPDATE: There is really not much point in throwing here
                // if corrupted XML got into the database then we'll
                // never be able to fetch this object to fix it.  The only
                // alternative would be to use SQL to try to repair the damage,
                // but it seems better to lost it all and start over.  At
                // least we can delete the containing object.
                //
                // IIQSAW-3116: some runtime errors are not parse errors. In those cases,
                // throw the error so valid objects are not removed from the parent; thereby,
                // losing data. If it is a parse error, assume the DB object is bad and do what
                // is described in the UPDATE tag above. Upstream, the SAXParseException is wrapped in
                // a runtime exception and the offending xml is logged. No need to log the xml here.
                if (t.getCause() != null && t.getCause().getClass() == SAXParseException.class) {
                    log.error("XML string could not be parsed and will cause the object to be removed from the parent. See log file for offending content. " + t.getMessage(), t);
                }
                else {
                    throw new HibernateException("XML parsing failed, but not because of invalid content.", t);
                }
            }
            finally {
                if (previous != null) {
                    try {
                        SailPointFactory.popContext(previous);
                    }
                    catch (Throwable t) {
                        // This is one of those "can't happen" exceptions but if it
                        // Does it's pretty serious.  Since the thread local cotext
                        // is likely screwed up
                        throw new HibernateException(t);
                    }
                }
            }
        }
        return result;
    }

    private String filterPassword(String xml) {
        String printXml = xml;
        int idx = xml.indexOf("password");
        if (idx > 0) {
            int beginidx = xml.indexOf("value=\"", idx) + 7;
            if (beginidx > 0) {

                int endidx = xml.indexOf("\"", beginidx);

                if (endidx > 0) {
                    printXml = xml.substring(0, beginidx) + "*********" + xml.substring(endidx, xml.length());
                }
            }
        }
        return printXml;
    }


    /**
     * Reconstruct an object from the cacheable representation.
     * At the very least this method should perform a deep copy if the type
     * is mutable (optional operation).
     *
     * @see org.hibernate.usertype.UserType#assemble(java.io.Serializable, java.lang.Object)
     */
    public Object assemble(Serializable cached, Object owner)
        throws HibernateException {

        Object assembled = null;

        if (cached == null) {
            if (log.isInfoEnabled())
                log.info("XmlType::assemble null");
        }
        else if (cached instanceof String) {
            // must be caching XML
            String xml = (String)cached;

            // pjeong: don't print log info with password
            if (log.isInfoEnabled()) 
                log.info("XmlType::assemble " + filterPassword(xml));

            // this must be left in the current session so privateContext arg is false
            assembled = parseXml("assemble", xml, false);
        }
        else {
            // old style, deep copy everything
            if (log.isInfoEnabled())
                log.info("XmlType::assemble " + cached.getClass().getSimpleName());

            // this must be left in the current session so privateContext arg is false
            assembled = deepCopyInternal(cached, false);
        }

        return assembled;
    }

    /**
     * Transform the object into its cacheable representation. At the very
     * least this method should perform a deep copy if the type is mutable.
     * That may not be enough for some implementations, however; for example,
     * associations must be cached as identifier values. (optional operation)
     *
     * @see org.hibernate.usertype.UserType#disassemble(java.lang.Object)
     *
     * Originally this called deepCopy() to clone by doing a full
     * XML round trip.  Since the thing that gets put into the 2nd level
     * cache doesn't have to resemble the original object we now just
     * save the XML representation.  This saves an extra parse when
     * the object is put into the cache and an extra serialization when
     * the object is brought out of the cache.
     */
    public Serializable disassemble(Object value) throws HibernateException
    {
        Serializable disassembled = null;

        if (value == null) {
            log.info("XmlType::disassemble null");
        }
        else if (CacheRepresentationXml) {
            // new style, cache the XML representation
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            disassembled = f.toXmlNoIndent(value);
        }
        else {
            // old style, always deep copy
            if (log.isInfoEnabled())
                log.info("XmlType::disassemble " + value.getClass().getSimpleName());

            // this isn't being returned to set privateContext arg to true
            // to avoid messing up the current session
            disassembled = (Serializable)deepCopyInternal(value, true);
        }

        return disassembled;
    }

    /**
     * Return a deep copy of the persistent state, stopping at entities
     * and at collections. It is not necessary to copy immutable objects,
     * or null values, in which case it is safe to simply return the argument.
     *
     * @see org.hibernate.usertype.UserType#deepCopy(java.lang.Object)
     *
     * Some of the XML objects need to reference other top-level
     * persistent objects.  These will be serialized as <Reference> elements,
     * but to restore them we have to pass in an XMLResolver.
     * See comments under nullSafeGet about why we do not release
     * the SailPointContext here.
     */
    public Object deepCopy(Object value) throws HibernateException
    {
        // this is expected to be used only to create the snapshot
        // for difference detection so we want it in a private context
        // to prevent messing up the cache on commit
        return deepCopyInternal(value, true);
    }

    /**
     * Make a deep copy of an object by serializing to XML then
     * parsing the XML into a new object.  See the comments
     * above parseXml() for more about what privateContext does.
     */
    public Object deepCopyInternal(Object value, boolean privateContext)
        throws HibernateException {

        Object copy = null;

        if (value == null) {
            log.info("XmlType::deepCopy null");
        }
        else {
            if (log.isInfoEnabled())
                log.info("XmlType::deepCopy " + value.getClass().getSimpleName());

            if (UseSimpleCloning && isSimpleObject(value)){
                try {
                    return simpleClone(value);
                } catch (Throwable e) {
                    // This was added at the end of 5.1 as an optimization.
                    // If it fails, log the error and fall back to
                    // xml cloning
                    log.error(e.getMessage(), e);
                }
            }

            XMLObjectFactory f = XMLObjectFactory.getInstance();
            // okay without the header?
            String xml = f.toXmlNoIndent(value);
            copy = parseXml("deepCopyInternal", xml, privateContext);
        }

        return copy;
    }

    /**
     * During merg, replace the existing (target) value in the entity we
     * are merging to with a new (original) value from the detached entity
     * we are merging. For immutable objects, or null values, it is safe
     * to simply return the first parameter. For mutable objects, it is
     * safe to return a copy of the first parameter. For objects with
     * component values, it might make sense to recursively replace component
     * values.
     *
     * @see org.hibernate.usertype.UserType#replace(java.lang.Object, java.lang.Object, java.lang.Object)
     *
     * I'm not exactly sure when this gets called, I don't hit any breakpoitns
     * on it so we apparently don't use merge() anywhere.
     *
     */
    public Object replace(Object original, Object target, Object owner)
        throws HibernateException
    {
        Object replaced = null;

        if (original == null) {
            log.info("XmlType::replace null");
        }
        else {
            if (log.isInfoEnabled())
                log.info("XmlType::replace " + original.getClass().getSimpleName());

            // I'm pretty sure we're expected to be operating in the current session cache here
            replaced = deepCopyInternal(original, false);
        }

        return replaced;
    }

    /**
     * Compare two instances of the class mapped by this type for
     * persistence "equality". Equality of the persistent state.
     *
     * @see org.hibernate.usertype.UserType#equals(java.lang.Object, java.lang.Object)
     */
    public boolean equals(Object x, Object y) throws HibernateException
    {
        boolean eq = false;
        boolean diffTraced = false;

        if (x == y)
            eq = true;

        else if (x == null) {
            if ( ( y instanceof Map ) && ( ((Map)y).size() == 0 ) )
                eq = true;
            else if ( ( y instanceof Collection ) && ( ((Collection)y).size() == 0 ) )
                eq = true;
        }
        else if (y == null) {
            if ( ( x instanceof Map ) && ( ((Map)x).size() == 0 ) )
                eq = true;
            else if ( ( x instanceof Collection ) && ( ((Collection)x).size() == 0 ) )
                eq = true;
        }
        else {

            // Examine the object to determine if we can avoid
            // performing a full xml comparison by performing
            // a simple comparison. If return is null then
            // simpleComparison is not possible
            Boolean simpleCompare = null;

            if (UseSimpleCloning){
                try {
                    simpleCompare = simpleComparison(x, y);
                } catch (Throwable e) {
                    // This was added at the end of 5.1 as an optimization.
                    // If it fails, log the error and fall back to
                    // xml comparison
                    log.error(e.getMessage(), e);
                }
            }

            // Tried various hacks here to save the original XML but those
           // are unreliable until we can know for sure whether deepCopy
           // is only used to make the initial snapshot.  So we have
           // to do a full serialization and compare now.  Alternately
           // we could make all XML types implement their own equals method
           // but that's dangerous and error prone (though probably faster).
           // REALLY need to think more about having XML objects maintain
           // their own dirty flag, but then when do you clear it?

            if (simpleCompare == null) {        
                eq = compareXml(x, y);
                // kludge: this does it's own tracing with the sorted Maps which
                // is what we want to see, don't trace again below
                diffTraced = true;
            }
            else
                eq = simpleCompare;
        }

        if (log.isInfoEnabled()) {
            // including the objects is generally too wordy
            //log.info("XmlType::equals " + x + " == " + y +
            //" --> " + (eq ? "true" : "false"));
            String class1 = (x != null) ? x.getClass().getSimpleName() : "null";
            String class2 = (y != null) ? y.getClass().getSimpleName() : "null";

            log.info("XmlType::equals " + class1 + "/" + class2 + 
                     " --> " + (eq ? "true" : "false"));
        }

        return eq;
    }

    /**
     * Turn each object into its xml representation
     * and compare the resulting strings.
     */
    private boolean compareXml(Object x, Object y) {

        boolean eq = false;
        Object o1 = orderMaps(x);
        Object o2 = orderMaps(y);

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        String before = f.toXmlNoIndent(o1);
        String after = f.toXmlNoIndent(o2);
        eq = before.equals(after);

        return eq;
    }

    /**
     * Maps and Collections are of interest here since they
     * have ordering. In the case of the Maps we want to assure
     * all of the keys are ordered correctly and with collections
     * we also want to make sure any maps have their keys ordered
     * properly. Additionally if we bump into a HashSet turn it
     * into a TreeSet since its order is based on the hashcode
     * of the value.
     */
    @SuppressWarnings("unchecked")
    private Object orderMaps(Object obj) {

        Object neu = obj;
        if ( obj instanceof Map ) {
            Map m = (Map)obj;
            neu = new TreeMap();
            Iterator<String> keys = m.keySet().iterator();
            if ( keys != null ) {
                while ( keys.hasNext() ) {
                    String key = keys.next();
                    // TreeMap can't support null keys, filter them out
                    // until we determine whether we need to be
                    // doing this at all - jsl
                    if (key != null) {
                        Object val = m.get(key);
                        val = orderMaps(val);
                        ((Map)neu).put(key,val);
                    }
                }
            }
        } else
        if ( obj instanceof Collection ) {
            Collection c = (Collection)obj;
            if ( containsMaps(c) ) {
                if ( c instanceof HashSet ) {
                    neu  = new TreeSet(c);
                } else {
                    neu  = new ArrayList(c);
                }
                for ( Object o : ((Collection)neu) ) {
                    if ( ( o instanceof Map ) ||
                         ( o instanceof Collection ) ) {
                        o = orderMaps(o);
                    }
                }
            }
        }
        return neu;
    }

    /**
     * Peek at the collection and see if it contains Map
     * objects.
     */
    private boolean containsMaps(Collection col) {
        if ( ( col != null ) && ( col.size() > 0 ) ) {
            Object obj = null;
            for ( Object o : col ) {
                obj = o;
                break;
            }
            if ( ( obj != null ) && ( obj instanceof Map ) ) {
                return true;
            }
        }
        return false;
    }

    // debug
    private void runDiff(String s1, String s2) {

        try {

            long ts = System.currentTimeMillis();
            String file1 = "C:/tmp/diff/file1-"+ ts + ".out";
            String file2 = "C:/tmp/diff/file2-"+ ts +".out";

            Util.writeTestFile(file1, s1);
            Util.writeTestFile(file2, s2);

            Process p = Runtime.getRuntime().exec("diff -w "+file1+" "+file2);

            BufferedReader stdInput = new BufferedReader(new
                 InputStreamReader(p.getInputStream()));

            // read the output from the command
            String s = null;
            StringBuffer buf = new StringBuffer();
            while ((s = stdInput.readLine()) != null) {
                buf.append(s);
                buf.append("\n");
            }
            if ( buf.length() > 0 ) {
                if (log.isDebugEnabled()) {
                    log.debug("FILE1\n[" + s1 + "] FILE2\n[" + s2 + "]");
                    log.debug("DIFF: \n[" + buf.toString() + "]");
                }
            }
        } catch(Exception e) {
            if (log.isErrorEnabled())
                log.debug("Error: "+ e.getMessage(), e);
        }
    }

    /**
     * Attempt to clone simple objects so we can avoid performing the clone
     * with XML serialization.
     *
     * Note: this stuff was added for the 5.1 performance
     * fixes and should be considered a quick fix. We plan to
     * introduce a more elegant fix in 5.2
     */
    private Object simpleClone(Object o){
        if (o==null) {
            return null;
        }else if (o instanceof Map){
                Map neu = o instanceof Attributes ? new Attributes() : new HashMap();
                Map old = (Map)o;
                for(Object key : old.keySet()){
                    neu.put(simpleClone(key), simpleClone(old.get(key)));
                }
                return neu;
        } else if (o instanceof Collection){
            Collection neu = o instanceof List ? new ArrayList() : new HashSet();
            for(Object val : (Collection)o){
                neu.add(simpleClone(val));
            }
            return neu;
        } else if (o instanceof String || o instanceof Long || o instanceof Integer || o instanceof Boolean
                || o instanceof Float){
            return o;
        } else if (o instanceof Date) {
            return ((Date)o).clone();
        }  else {
            throw new RuntimeException("Unhandled object type");
        }
    }

    /**
     * Attempts to determine if the two objects are equivalent
     * without using expensive xml serialization
     *
     * Note: this stuff was added for the 5.1 performance
     * fixes and should be considered a quick fix. We plan to
     * introduce a more elegant fix in 5.2
     */
    private static Boolean simpleComparison(Object o1, Object o2){

        if (!isSimpleObject(o1) || !isSimpleObject(o2))
            return null;
        if (o1 instanceof List && o2 instanceof List){
            return compareList((List)o1, (List)o2);
        } else if (o1 instanceof Collection && o2 instanceof Collection){
            return compareCollections((Collection)o1, (Collection)o2);
        } else if (o1 instanceof Map){
            return compareMaps((Map)o1, (Map)o2);
        }

        return compareObjects(o1, o2);
    }

    /**
     * Compare the contents of the given collections to determine if they
     * are equivalent. !NOTE: Call isSimpleObject() on the maps to
     * determine if this method may be used here. This method isn't
     * smart enough to handle anything other than basic java objects.
     */
    private static boolean compareCollections(Collection c1, Collection c2){

        if (c1.size() != c2.size())
            return false;

        for(Object o1 : c1){
            if (!c2.contains(o1))
                return false;
        }

        return true;
    }

    /**
     * Compare the contents of the given lists to determine if they
     * are equivalent. !NOTE: Call isSimpleObject() on the maps to
     * determine if this method may be used here. This method isn't
     * smart enough to handle anything other than basic java objects.
     */
    private static boolean compareList(List l1, List l2){

        if (l1.size() != l2.size())
            return false;

        for (int i = 0; i < l1.size(); i++) {
            if (!compareObjects(l1.get(i), l2.get(i)))
                return false;
        }

        return true;
    }

    /**
     * Compare the contents of the given maps to determine if they
     * are equivalent. !NOTE: Call isSimpleObject() on the maps to
     * determine if this method may be used here. This method isn't
     * smart enough to handle anything other than basic java objects.
     */
    private static boolean compareMaps(Map m1, Map m2){

        if (m1.size() != m2.size())
            return false;

        for(Object k1 : m1.keySet()){
            if (!m2.containsKey(k1))
                return false;
            if (!compareObjects(m1.get(k1),m2.get(k1)))
                return false;
        }

        return true;
    }

    /**
     * Compares two objects using .equals(). Treats nulls
     * as equals.
     */
    private static boolean compareObjects(Object o1, Object o2){
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    /**
     * Returns true if the collection only contains simple java
     * objects and no SailPoint objects.
     */
    private static boolean containsSimpleObects(Map m){
        if (m == null)
            return true;

        if (!containsSimpleObects(m.values()) || !containsSimpleObects(m.keySet()))
            return false;

        return true;
    }

    /**
     * Returns true if the collection only contains simple java
     * objects and no SailPoint objects.
     */
    private static boolean containsSimpleObects(Collection c){
        if (c == null)
            return true;
        Iterator iter = c.iterator();
        while(iter.hasNext()){
            if (!isSimpleObject(iter.next()))
                return false;
        }

        return true;
    }

    /**
     * Returns true if the object is 'simple', which means it
     * is a basic java object, or a collection of basic objects.
     * If the object is a SailPoint object or contains a
     * SailPoint object this will reutrn false.
     */
    private static boolean isSimpleObject(Object o){

        if (o == null)
            return true;

        if (o instanceof Collection)
            return containsSimpleObects((Collection)o);
        else if (o instanceof Map)
            return containsSimpleObects((Map)o);

        return o instanceof String || o instanceof Date || o instanceof Float ||
                o instanceof Integer || o instanceof Long || o instanceof Boolean;
    }


}
